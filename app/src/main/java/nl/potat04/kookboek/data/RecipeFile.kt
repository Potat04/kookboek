package nl.potat04.kookboek.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The `.kookboek` file: one or more recipes, with their pictures, in something you can
 * send over WhatsApp.
 *
 * It is a zip. `recipe.json` is a [RecipeJson] document, `images/<name>` are the
 * pictures and the photographed cards it refers to, and every entry is encrypted with
 * AES-256-GCM.
 *
 * No Android in here, so the whole format can be tested on the JVM. The file IO around
 * it — where the file goes, which picture belongs to whom — belongs to the caller.
 */
object RecipeFile {

    const val EXTENSION = "kookboek"

    /** Our own type when sharing; incoming chat attachments may use binary or ZIP types. */
    const val MIME_TYPE = "application/vnd.kookboek"

    const val DOCUMENT_ENTRY = "recipe.json"
    const val IMAGE_PREFIX = "images/"

    /**
     * What came out of a file that was read.
     *
     * The pictures are handed to the caller one at a time while reading rather than
     * collected here: a cookbook's worth of photos does not want to sit in memory at
     * once.
     */
    data class Content(
        val document: RecipeJson.Document,
        /** Names of the pictures that were in the archive, in the order they came out. */
        val images: List<String>,
    )

    /**
     * Writes [recipes] to [out] and closes it.
     *
     * @param imageBytes the picture stored under that file name, or null when it is gone.
     */
    fun write(
        out: OutputStream,
        recipes: List<Recipe>,
        labelsByRecipe: Map<String, List<String>> =
            recipes.associate { r -> r.id to r.labels.map { it.name } },
        exportedAt: Long = System.currentTimeMillis(),
        imageBytes: (String) -> ByteArray?,
    ) {
        ZipOutputStream(out.buffered()).use { zip ->
            val json = RecipeJson.encode(recipes, labelsByRecipe, exportedAt)
            zip.putNextEntry(ZipEntry(DOCUMENT_ENTRY))
            zip.write(encrypt(json.toByteArray(Charsets.UTF_8)))
            zip.closeEntry()

            // Both the recipe photo and a photographed recipe card ride along; a recipe
            // that arrives without its picture looks broken, and the sender has it.
            recipes
                .flatMap { listOfNotNull(it.imageFile, it.attachmentFile) }
                .distinct()
                .forEach { name ->
                    // The reader on the other end refuses a name with a path in it, and
                    // so does this end: an entry like that is never one of ours.
                    if (RecipeJson.bareName(name) == null) return@forEach
                    val bytes = imageBytes(name) ?: return@forEach
                    zip.putNextEntry(ZipEntry(IMAGE_PREFIX + name))
                    zip.write(encrypt(bytes))
                    zip.closeEntry()
                }
        }
    }

    /**
     * Reads a file written by [write]. Never throws: anything wrong with it comes back
     * as a [RecipeJson.DecodeError], the same vocabulary the plain document uses.
     *
     * @param saveImage called for each picture, with the name it had in the archive.
     */
    fun read(
        input: InputStream,
        saveImage: (name: String, bytes: ByteArray) -> Unit,
    ): Result<Content> {
        var document: String? = null
        val images = mutableListOf<String>()
        try {
            ZipInputStream(input.buffered()).use { zip ->
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    // A name that climbs out of the archive is either a mistake or an
                    // attack; either way there is nothing here worth following it for.
                    if (name.contains("..") || name.startsWith("/")) continue

                    val raw = zip.readAtMost(MAX_ENTRY_BYTES) ?: return notAKookboekFile()
                    total += raw.size
                    if (total > MAX_TOTAL_BYTES) return notAKookboekFile()

                    when {
                        name == DOCUMENT_ENTRY -> document = decrypt(raw).toString(Charsets.UTF_8)
                        name.startsWith(IMAGE_PREFIX) -> {
                            val fileName = name.removePrefix(IMAGE_PREFIX)
                            if (fileName.isNotBlank() && !fileName.contains('/')) {
                                saveImage(fileName, decrypt(raw))
                                images += fileName
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return Result.failure(RecipeJson.DecodeError.NotAKookboekFile(e))
        }

        val text = document ?: return notAKookboekFile()
        return RecipeJson.decode(text).map { Content(it, images) }
    }

    private fun <T> notAKookboekFile(): Result<T> =
        Result.failure(RecipeJson.DecodeError.NotAKookboekFile())

    /** Reads the entry, or null once it turns out to be bigger than any picture we write. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ the lock

    /**
     * Obfuscation, not secrecy, and it cannot be anything else.
     *
     * Both phones have to know this key and there is no server to hand one out, so it
     * ships inside the app and anybody who unzips the APK has it. What it buys is that
     * a `.kookboek` file is not a text file: no other app treats it as JSON, nobody
     * opens one in an editor, changes a line and hands back something the importer then
     * has to defend itself against, and a recipe sent to a friend is not sitting in
     * plain sight in their downloads folder. Do not put anything in here that would
     * matter if it were read.
     */
    private val KEY = "7c1f4a93d0b52e68af3c9014d7e2685b3f80a1cd46927fb5e0a83d14c6957f2b"
        .chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private val secretKey get() = SecretKeySpec(KEY, "AES")

    private val random = SecureRandom()

    /** `[12-byte nonce][ciphertext and tag]`. A fresh nonce per entry, as GCM demands. */
    private fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    private fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "entry is too short to hold anything" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
        )
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Pictures are resized to about 1400px on import, so nothing legitimate is near this. */
    private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 256 * 1024 * 1024L
}
