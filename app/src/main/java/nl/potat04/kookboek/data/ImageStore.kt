package nl.potat04.kookboek.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Recipe pictures are copied onto the phone on import. A saved recipe should not
 * go blank because the site redesigned, went offline, or you are cooking without signal.
 */
class ImageStore(context: Context) {

    private val dir = File(context.filesDir, "images").apply { mkdirs() }

    private val appContext = context.applicationContext

    // The same agent PageFetcher fetches under, because a clearance cookie earned by one
    // agent is not honoured for another. Worked out on first use, not at startup: asking
    // for it loads the WebView, and most launches never download a thing.
    private val userAgent: String get() = BrowserIdentity.userAgent(appContext)

    // A handful of decoded bitmaps; the list screen scrolls through thumbnails constantly.
    private val cache = object : LruCache<String, ImageBitmap>(24) {}

    fun file(name: String): File = File(dir, name)

    suspend fun download(url: String, id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "image/*,*/*;q=0.8")
                // A site that made PageFetcher pass a bot check guards its pictures too.
                SiteCookies.header(url)?.let { setRequestProperty("Cookie", it) }
            }
            val bytes = conn.use { it.inputStream.buffered().readBytes() }
            require(bytes.size in 1..MAX_BYTES) { "image is ${bytes.size} bytes" }

            val bitmap = decodeScaled(bytes) ?: error("not a decodable image")
            val target = File(dir, "$id.jpg")
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            target.name
        }.onFailure { Log.w(TAG, "image download failed for $url", it) }.getOrNull()
    }

    /** Decodes at roughly [MAX_EDGE]px so a 4000px hero shot does not eat 60 MB of heap. */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    suspend fun load(name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val f = File(dir, name)
                if (!f.exists()) return@runCatching null
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()?.also { cache.put(name, it) }
            }.getOrNull()
        }
    }

    /**
     * Copies a picture out of a backup, under the name it had there. Leaves [input]
     * open: it is one entry of a zip the caller is still walking through.
     *
     * A file that is already here is left alone. The name is the recipe's id, so the
     * same name is the same picture, and overwriting would only cost a rewrite.
     *
     * @return true when a new file was written.
     */
    suspend fun importFile(name: String, input: InputStream): Boolean = withContext(Dispatchers.IO) {
        val target = File(dir, File(name).name)
        if (target.exists()) return@withContext false
        runCatching {
            target.outputStream().use { input.copyTo(it) }
            true
        }.onFailure {
            Log.w(TAG, "could not import $name", it)
            // A half-written file would decode to nothing and stay there forever.
            target.delete()
        }.getOrDefault(false)
    }

    /**
     * Removes pictures no recipe points at any more.
     *
     * Files touched in the last few minutes are left alone: an import running right
     * now may already have written its picture but not yet stored the recipe.
     */
    suspend fun pruneOrphans(inUse: Set<String>) = withContext(Dispatchers.IO) {
        runCatching {
            val cutoff = System.currentTimeMillis() - GRACE_MS
            dir.listFiles()
                ?.filter { it.name !in inUse && it.lastModified() < cutoff }
                ?.forEach { file ->
                    cache.remove(file.name)
                    file.delete()
                }
        }.onFailure { Log.w(TAG, "could not prune images", it) }
        Unit
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try { block(this) } finally { disconnect() }

    companion object {
        /** Only used when the device has no WebView to ask. See [BrowserIdentity]. */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0.0.0 Mobile Safari/537.36"
        private const val TAG = "ImageStore"
        private const val MAX_BYTES = 12 * 1024 * 1024
        private const val MAX_EDGE = 1400
        private const val GRACE_MS = 10 * 60 * 1000L
    }
}
