package nl.potat04.kookboek.data

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Android supplies the verified signing metadata; these rules decide whether it is our update. */
internal data class UpdatePackage(
    val name: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val signers: Set<String>,
    val signingHistory: Set<String>,
)

internal class UpdateFailure(val reason: UpdateError) : IOException(reason.name)

internal object UpdateApk {
    fun verifyFile(file: File, release: AppRelease) {
        if (!file.isFile || file.length() != release.size) throw UpdateFailure(UpdateError.INVALID_APK)
        release.sha256?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) throw UpdateFailure(UpdateError.INVALID_APK)
        }
    }

    fun verifyPackage(installed: UpdatePackage, incoming: UpdatePackage, release: AppRelease, sdk: Int) {
        val sameVersion = runCatching {
            !AppRelease.isNewer(incoming.versionName, release.version) &&
                !AppRelease.isNewer(release.version, incoming.versionName)
        }.getOrDefault(false)
        if (incoming.name != installed.name || incoming.versionCode <= installed.versionCode ||
            incoming.minSdk > sdk || !sameVersion
        ) throw UpdateFailure(UpdateError.INVALID_APK)

        val compatible = installed.signers.isNotEmpty() && incoming.signers.isNotEmpty() &&
            if (installed.signers.size > 1 || incoming.signers.size > 1) {
                installed.signers == incoming.signers
            } else {
                incoming.signingHistory.containsAll(installed.signers)
            }
        if (!compatible) throw UpdateFailure(UpdateError.SIGNATURE)
    }
}
