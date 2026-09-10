package nl.potat04.kookboek.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
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

            val bitmap = decodeScaled { ByteArrayInputStream(bytes) } ?: error("not a decodable image")
            write(bitmap, "$id.jpg")
        }.onFailure { Log.w(TAG, "image download failed for $url", it) }.getOrNull()
    }

    /**
     * Copies a picture the reader picked out of the gallery, by the same road a
     * downloaded one takes: scaled down and re-encoded as JPEG, ours from then on.
     *
     * [suffix] separates the photographed recipe card ("-card") from the recipe's own
     * picture, which keeps the plain recipe id so a replacement lands on the old file.
     */
    suspend fun saveFromUri(uri: Uri, id: String, suffix: String = ""): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val open = { checkNotNull(appContext.contentResolver.openInputStream(uri)) }
                val bitmap = decodeScaled(open) ?: error("not a decodable image")
                // A phone photo is nearly always turned by its Exif tag rather than by
                // its pixels, and BitmapFactory ignores that: a card shot in portrait
                // would end up on its side.
                write(bitmap.turned(open().use(::exifRotation)), "$id$suffix.jpg")
            }.onFailure { Log.w(TAG, "could not read the picked image $uri", it) }.getOrNull()
        }

    /** Writes the bitmap and drops the old decode, because the file name stays the same. */
    private fun write(bitmap: Bitmap, name: String): String {
        val target = File(dir, name)
        target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        bitmap.recycle()
        cache.remove(name)
        return target.name
    }

    /** Decodes at roughly [MAX_EDGE]px so a 4000px hero shot does not eat 60 MB of heap. */
    private fun decodeScaled(open: () -> InputStream): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return open().use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun exifRotation(stream: InputStream): Int =
        runCatching {
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)

    private fun Bitmap.turned(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val turned = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (turned !== this) recycle()
        return turned
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
