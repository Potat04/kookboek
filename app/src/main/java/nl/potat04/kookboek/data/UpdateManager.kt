package nl.potat04.kookboek.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    data class Ready(val release: AppRelease) : UpdateState
    data class Failed(val reason: UpdateError, val release: AppRelease? = null) : UpdateState
}

enum class UpdateError { CHECK, DOWNLOAD, INVALID_APK, SIGNATURE, INSTALL }

/** Owns downloads across screens. Only the UI may launch permission or installer activities. */
class UpdateManager(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    // GitHub APKs belong to the live app; debug has its own package and signing key.
    val enabled = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0
    private val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private val directory get() = File(app.cacheDir, "updates")
    private val apk get() = File(directory, "update.apk")

    @MainThread
    fun check(manual: Boolean = false) {
        if (!enabled) return
        if (job?.isCompleted == false || state.value is UpdateState.Ready) return
        val now = System.currentTimeMillis()
        val due = checkDue(now, prefs.getLong("checkedAt", 0))
        if (!manual && !due && state.value != UpdateState.Idle) return
        mutableState.value = UpdateState.Checking
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                val release = withContext(Dispatchers.IO) {
                    val installed = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
                    val json = if (manual || due) {
                        // Failed automatic attempts are throttled too. Manual retries remain available.
                        prefs.edit().putLong("checkedAt", now).putBoolean("checkFailed", true).apply()
                        readRelease().also {
                            // Parse before caching so an invalid response cannot look like success later.
                            it?.let(AppRelease::parse)
                            prefs.edit().putString("release", it)
                                .putBoolean("releaseKnown", true).putBoolean("checkFailed", false).apply()
                        }
                    } else {
                        check(prefs.getBoolean("releaseKnown", false) && !prefs.getBoolean("checkFailed", false)) {
                            "No successful cached check"
                        }
                        prefs.getString("release", null)
                    }
                    json?.let(AppRelease::parse)?.takeIf { AppRelease.isNewer(it.version, installed) }
                }
                mutableState.value = release?.let(UpdateState::Available) ?: UpdateState.Current
            } catch (e: CancellationException) {
                mutableState.value = UpdateState.Idle
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not check releases", e)
                mutableState.value = UpdateState.Failed(UpdateError.CHECK)
            }
        }
    }

    @MainThread
    fun download() {
        // A cancelled job may still be closing its socket and partial file on IO.
        if (job?.isCompleted == false) return
        val release = release() ?: return
        mutableState.value = UpdateState.Downloading(release, 0f)
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                withContext(Dispatchers.IO) {
                    require(directory.isDirectory || directory.mkdirs()) { "Could not create update cache" }
                    val partial = File(directory, "update.part")
                    try {
                        apk.delete()
                        downloadTo(release, partial)
                        validateApk(partial, release)
                        check(partial.renameTo(apk)) { "Could not finish download" }
                    } finally {
                        partial.delete()
                    }
                }
                mutableState.value = UpdateState.Ready(release)
            } catch (e: CancellationException) {
                mutableState.value = UpdateState.Available(release)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not download update", e)
                mutableState.value = UpdateState.Failed(
                    (e as? UpdateFailure)?.reason ?: UpdateError.DOWNLOAD, release,
                )
            }
        }
    }

    @MainThread
    fun cancel() { job?.cancel() }

    /** Recheck a cached file before handing it to another process. Never does IO on main. */
    @MainThread
    suspend fun installIntent(): Intent? {
        val release = (state.value as? UpdateState.Ready)?.release ?: return null
        return try {
            withContext(Dispatchers.IO) { validateApk(apk, release) }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", apk)
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update APK is not installable", e)
            mutableState.value = UpdateState.Failed(
                (e as? UpdateFailure)?.reason ?: UpdateError.INVALID_APK, release,
            )
            null
        }
    }

    @MainThread
    fun installationFailed() {
        mutableState.value = UpdateState.Failed(UpdateError.INSTALL, release())
    }

    private fun release(): AppRelease? = when (val value = state.value) {
        is UpdateState.Available -> value.release
        is UpdateState.Downloading -> value.release
        is UpdateState.Ready -> value.release
        is UpdateState.Failed -> value.release
        else -> null
    }

    private fun readRelease(): String? = request(API) { connection ->
        if (connection.responseCode == 404) return@request null
        if (connection.responseCode != 200) throw IOException("GitHub HTTP ${connection.responseCode}")
        connection.inputStream.use { input ->
            val bytes = input.readNBytes(MAX_METADATA_BYTES + 1)
            require(bytes.size <= MAX_METADATA_BYTES) { "Release metadata too large" }
            bytes.toString(Charsets.UTF_8)
        }
    }

    private suspend fun downloadTo(release: AppRelease, target: File) {
        val coroutine = currentCoroutineContext()
        request(release.downloadUrl) { connection ->
            if (connection.responseCode != 200) throw IOException("Download HTTP ${connection.responseCode}")
            val declared = connection.contentLengthLong
            if (declared >= 0 && declared != release.size) throw UpdateFailure(UpdateError.INVALID_APK)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var lastPercent = -1
                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > release.size) throw UpdateFailure(UpdateError.INVALID_APK)
                        output.write(buffer, 0, count)
                        val percent = (total * 100 / release.size).toInt()
                        if (percent != lastPercent) {
                            mutableState.value = UpdateState.Downloading(release, total.toFloat() / release.size)
                            lastPercent = percent
                        }
                    }
                    if (total != release.size) throw UpdateFailure(UpdateError.INVALID_APK)
                }
            }
        }
    }

    private fun validateApk(file: File, release: AppRelease) {
        UpdateApk.verifyFile(file, release)
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val installed = app.packageManager.getPackageInfo(app.packageName, flags)
        val incoming = app.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateFailure(UpdateError.INVALID_APK)
        UpdateApk.verifyPackage(installed.updatePackage(), incoming.updatePackage(), release, Build.VERSION.SDK_INT)
    }

    private fun android.content.pm.PackageInfo.updatePackage(): UpdatePackage {
        val signing = signingInfo ?: throw UpdateFailure(UpdateError.SIGNATURE)
        return UpdatePackage(
            name = packageName,
            versionCode = longVersionCode,
            versionName = versionName.orEmpty(),
            minSdk = applicationInfo?.minSdkVersion ?: throw UpdateFailure(UpdateError.INVALID_APK),
            signers = signing.apkContentsSigners.map { it.toCharsString() }.toSet(),
            signingHistory = signing.signingCertificateHistory.orEmpty().map { it.toCharsString() }.toSet(),
        )
    }
    /** Follow only HTTPS redirects to GitHub's release hosts, with bounded hops and timeouts. */
    private fun <T> request(address: String, read: (HttpURLConnection) -> T): T {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https" && url.host in HOSTS && url.userInfo == null)
            require(url.port == -1 || url.port == 443)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Kookboek-Android-Updater")
                setRequestProperty("Accept", if (url.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                if (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: throw IOException("Missing redirect"))
                } else return read(connection)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }


    companion object {
        private const val TAG = "UpdateManager"
        private const val API = "https://api.github.com/repos/${AppRelease.REPOSITORY}/releases/latest"
        private const val MAX_METADATA_BYTES = 1024 * 1024
        private val HOSTS = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

        internal fun checkDue(now: Long, checkedAt: Long): Boolean =
            checkedAt <= 0 || checkedAt > now || now - checkedAt >= 24 * 60 * 60 * 1000L

    }
}
