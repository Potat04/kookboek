package nl.potat04.kookboek.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The interchange format: what a backup file and a shared `.kookboek` file contain.
 *
 * Separate DTOs rather than [Recipe] itself, on purpose. The storage model will keep
 * changing with the app; a file someone exported two years ago must still open. The
 * DTOs only ever gain optional fields, and [VERSION] moves when that rule has to be
 * broken.
 *
 * No zip and no file IO here. This is the text inside the file; the packaging around
 * it belongs to whoever writes the file.
 */
object RecipeJson {

    const val FORMAT = "kookboek"

    /** Bump only for a change an older app could not read. Adding an optional field is not one. */
    const val VERSION = 1

    /** Why a document could not be read. A type, so the screen can word it in the reader's language. */
    sealed class DecodeError(message: String) : Exception(message) {
        /** Not JSON, or JSON that is not a Kookboek document. */
        class NotAKookboekFile(cause: Throwable? = null) :
            DecodeError("not a kookboek document") { init { cause?.let(::initCause) } }

        /** Written by a newer app than this one. */
        class NewerVersion(val version: Int) :
            DecodeError("format version $version is newer than $VERSION")
    }

    @Serializable
    data class Document(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val exportedAt: Long,
        val recipes: List<RecipeDto> = emptyList(),
    )

    @Serializable
    data class LineDto(
        val text: String,
        val section: String? = null,
    )

    @Serializable
    data class RecipeDto(
        val id: String,
        val title: String,
        val sourceUrl: String? = null,
        val siteName: String? = null,
        val author: String? = null,
        val description: String? = null,
        /** File name relative to the archive the document sits in, or null for no picture. */
        val imageFile: String? = null,
        val ingredients: List<LineDto> = emptyList(),
        val steps: List<LineDto> = emptyList(),
        val prepMinutes: Int? = null,
        val cookMinutes: Int? = null,
        val totalMinutes: Int? = null,
        val servings: Int? = null,
        val servingsLabel: String? = null,
        val tags: List<String> = emptyList(),
        /** Labels travel by name: ids are local to one phone. */
        val labels: List<String> = emptyList(),
        val notes: String = "",
        @SerialName("favorite") val favourite: Boolean = false,
        val addedAt: Long,
        val cookedServings: Int? = null,
        val lastCookedAt: Long? = null,
        val editedAt: Long? = null,
        val videoUrl: String? = null,
        val attachmentFile: String? = null,
        val quality: String = ParseQuality.FULL.name,
    ) {
        /**
         * Back to the domain. [labels] must already exist on this phone; the caller
         * creates or matches them by name and hands over the result. Checks, dates of
         * opening and deletion are not part of the format, so they start fresh.
         */
        fun toRecipe(labels: List<Label> = emptyList()): Recipe = Recipe(
            id = id,
            title = title,
            sourceUrl = sourceUrl,
            siteName = siteName,
            author = author,
            description = description,
            // A file from somewhere else names its own pictures, and a name with a path
            // in it would point the store at whatever the path leads to — the database
            // among other things. It is dropped here so no caller has to remember.
            imageFile = bareName(imageFile),
            ingredients = ingredients.map { Ingredient(it.text, it.section) },
            steps = steps.map { Step(it.text, it.section) },
            prepMinutes = prepMinutes,
            cookMinutes = cookMinutes,
            totalMinutes = totalMinutes,
            servings = servings,
            servingsLabel = servingsLabel,
            tags = tags,
            labels = labels,
            notes = notes,
            favorite = favourite,
            addedAt = addedAt,
            quality = runCatching { ParseQuality.valueOf(quality) }.getOrDefault(ParseQuality.FULL),
            cookedServings = cookedServings,
            lastCookedAt = lastCookedAt,
            editedAt = editedAt,
            videoUrl = videoUrl,
            attachmentFile = bareName(attachmentFile),
        )

        companion object {
            fun from(recipe: Recipe, labelNames: List<String>) = RecipeDto(
                id = recipe.id,
                title = recipe.title,
                sourceUrl = recipe.sourceUrl,
                siteName = recipe.siteName,
                author = recipe.author,
                description = recipe.description,
                imageFile = recipe.imageFile,
                ingredients = recipe.ingredients.map { LineDto(it.text, it.section) },
                steps = recipe.steps.map { LineDto(it.text, it.section) },
                prepMinutes = recipe.prepMinutes,
                cookMinutes = recipe.cookMinutes,
                totalMinutes = recipe.totalMinutes,
                servings = recipe.servings,
                servingsLabel = recipe.servingsLabel,
                tags = recipe.tags,
                labels = labelNames,
                notes = recipe.notes,
                favourite = recipe.favorite,
                addedAt = recipe.addedAt,
                cookedServings = recipe.cookedServings,
                lastCookedAt = recipe.lastCookedAt,
                editedAt = recipe.editedAt,
                videoUrl = recipe.videoUrl,
                attachmentFile = recipe.attachmentFile,
                quality = recipe.quality.name,
            )
        }
    }

    /**
     * A file name as it may appear in a document or an archive: one name, no path.
     * Anything else is dropped rather than cleaned up — a file we wrote never has a
     * path in it, so one that does was not written by us.
     */
    fun bareName(raw: String?): String? = raw?.takeIf {
        it.isNotBlank() && !it.contains('/') && !it.contains('\\') && it != "." && it != ".."
    }

    private val json = Json {
        // A file from a newer minor version has keys we do not know; that is fine.
        ignoreUnknownKeys = true
        // Absent means null, and nulls are not written, which keeps the file readable.
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * @param labelsByRecipe label names per recipe id. Defaults to what each recipe
     * carries, so callers only pass it when they have a better source.
     */
    fun encode(
        recipes: List<Recipe>,
        labelsByRecipe: Map<String, List<String>> =
            recipes.associate { r -> r.id to r.labels.map { it.name } },
        exportedAt: Long = System.currentTimeMillis(),
    ): String {
        val document = Document(
            exportedAt = exportedAt,
            recipes = recipes.map { RecipeDto.from(it, labelsByRecipe[it.id].orEmpty()) },
        )
        return json.encodeToString(document)
    }

    /** Never throws: anything wrong with the text comes back as a [DecodeError]. */
    fun decode(text: String): Result<Document> {
        val document = try {
            json.decodeFromString<Document>(text)
        } catch (e: Exception) {
            return Result.failure(DecodeError.NotAKookboekFile(e))
        }
        if (document.format != FORMAT) return Result.failure(DecodeError.NotAKookboekFile())
        if (document.version > VERSION) return Result.failure(DecodeError.NewerVersion(document.version))
        return Result.success(document)
    }
}
