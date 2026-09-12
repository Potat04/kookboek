package nl.potat04.kookboek.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a restore actually did, so the screen can say it instead of guessing. */
data class BackupSummary(
    val added: Int,
    val updated: Int,
    val skipped: Int,
    val images: Int,
)

/**
 * Why a file could not be read back. A type and not a sentence: this layer does not
 * know which language the reader picked. See localization.md.
 */
sealed class BackupError(message: String) : Exception(message) {
    /** Not a zip, or a zip without a cookbook in it. */
    class NotABackup(cause: Throwable? = null) : BackupError("not a kookboek backup") {
        init { cause?.let(::initCause) }
    }

    /** Written by a newer Kookboek than this one; guessing at it would lose data. */
    class NewerVersion(val version: Int) : BackupError("backup format version $version")
}

/**
 * The whole cookbook in one zip: `recipes.json` plus the pictures it points at.
 *
 * A zip and not a bare JSON file because a recipe without its photo is only half of
 * what you saved. Everything goes through [RecipeRepository], never the DAO, so a
 * restore lands in the same place an import does.
 */
class Backup(private val repo: RecipeRepository) {

    /**
     * Writes every live recipe to [out] and closes it. Deleted recipes stay behind:
     * a backup is the cookbook, not the bin.
     *
     * @return how many recipes went in, so the snackbar can say so.
     */
    suspend fun write(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val recipes = repo.recipes.value
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DOCUMENT))
            zip.write(RecipeJson.encode(recipes).toByteArray())
            zip.closeEntry()

            recipes
                .flatMap { listOfNotNull(it.imageFile, it.attachmentFile) }
                .distinct()
                .forEach { name ->
                    // A row that somehow holds a path must not turn into a zip entry
                    // that writes outside the images directory when it is read back.
                    if (imageName(name) == null) return@forEach
                    val file = repo.images.file(name)
                    if (!file.isFile) return@forEach
                    zip.putNextEntry(ZipEntry("$IMAGES/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        recipes.size
    }

    /**
     * Reads a backup back in and merges it. Never throws: anything wrong with the file
     * comes back as a [BackupError].
     *
     * The merge rule, in one line: an unknown recipe is added, a known one is replaced
     * only by a copy the file stamped later than ours, and a recipe sitting in the bin
     * here stays in the bin. Restoring is not the same as undoing a delete — that is
     * what the "recently deleted" screen is for — and silently resurrecting things
     * someone threw out is the one surprise a restore must not spring.
     */
    suspend fun read(input: InputStream): Result<BackupSummary> {
        var text: String? = null
        var images = 0
        try {
            withContext(Dispatchers.IO) {
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory) {
                            when {
                                // `recipes.json` is the current name. Early builds used
                                // the singular form, so accept those backups as well.
                                name == DOCUMENT || name == LEGACY_DOCUMENT ->
                                    text = zip.readBytes().decodeToString()
                                name.startsWith("$IMAGES/") -> {
                                    val file = imageName(name.removePrefix("$IMAGES/"))
                                    if (file != null && repo.images.importFile(file, zip)) images++
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "could not read the backup", e)
            return Result.failure(BackupError.NotABackup(e))
        }

        val document = RecipeJson.decode(text ?: return Result.failure(BackupError.NotABackup()))
            .getOrElse { error ->
                return Result.failure(
                    when (error) {
                        is RecipeJson.DecodeError.NewerVersion -> BackupError.NewerVersion(error.version)
                        else -> BackupError.NotABackup(error)
                    }
                )
            }

        return Result.success(merge(document, images))
    }

    private suspend fun merge(document: RecipeJson.Document, images: Int): BackupSummary {
        // Labels travel by name because ids are local to one phone. Matching is
        // case-insensitive: "Zondag" and "zondag" are one label to anyone reading it.
        val known = repo.labels.value.associateByTo(mutableMapOf()) { it.name.lowercase() }
        var added = 0
        var updated = 0
        var skipped = 0

        for (dto in document.recipes) {
            val labels = dto.labels
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { name -> known.getOrPut(name.lowercase()) { repo.createLabel(name) } }

            // A picture named in the document but missing from the zip would leave the
            // card showing a grey block instead of a letter.
            val incoming = dto.toRecipe(labels).let { recipe ->
                recipe.copy(
                    imageFile = recipe.imageFile?.takeIf { repo.images.file(it).exists() },
                    attachmentFile = recipe.attachmentFile?.takeIf { repo.images.file(it).exists() },
                )
            }
            val local = repo.byId(dto.id) ?: repo.deletedById(dto.id)
            when {
                local == null -> {
                    repo.save(incoming)
                    added++
                }
                local.isDeleted -> skipped++
                stamp(incoming) > stamp(local) -> {
                    // The file has no opinion on what you ticked off or when you first
                    // opened it — those are this phone's, and they stay.
                    repo.save(
                        incoming.copy(
                            checkedIngredients = local.checkedIngredients,
                            checkedSteps = local.checkedSteps,
                            openedAt = local.openedAt,
                        )
                    )
                    updated++
                }
                else -> skipped++
            }
        }
        return BackupSummary(added = added, updated = updated, skipped = skipped, images = images)
    }

    companion object {
        private const val TAG = "Backup"

        const val DOCUMENT = "recipes.json"
        private const val LEGACY_DOCUMENT = "recipe.json"
        const val IMAGES = "images"

        /** How many daily files stay in the backup folder. A week is a week of mistakes. */
        const val KEEP = 7

        private val NAME = Regex("kookboek-\\d{4}-\\d{2}-\\d{2}\\.zip")

        /** `kookboek-2026-09-10.zip`. [Locale.US] on purpose: a file name is not prose. */
        fun fileName(millis: Long): String =
            SimpleDateFormat("'kookboek-'yyyy-MM-dd'.zip'", Locale.US).format(Date(millis))

        fun isBackupName(name: String): Boolean = NAME.matches(name)

        /**
         * The files to drop so only the newest [keep] daily backups stay. Sorting by
         * name is sorting by date — that is what the date in the name is for — and
         * anything that is not one of ours is left alone.
         */
        fun stale(names: List<String>, keep: Int = KEEP): List<String> =
            names.filter(::isBackupName).distinct().sortedDescending().drop(keep)

        /**
         * A name out of a zip is not to be trusted. Anything with a path in it, or the
         * empty string, is dropped rather than cleaned up: a backup we wrote never has
         * one, so a file that does is not a backup we wrote.
         */
        fun imageName(raw: String): String? = RecipeJson.bareName(raw)

        /** When a copy was last touched by hand, falling back to when it was saved. */
        fun stamp(recipe: Recipe): Long = maxOf(recipe.editedAt ?: 0L, recipe.addedAt)
    }
}
