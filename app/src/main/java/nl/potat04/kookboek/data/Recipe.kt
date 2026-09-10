package nl.potat04.kookboek.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** How much of the recipe we managed to read off the page. Shown honestly to the user. */
enum class ParseQuality { FULL, PARTIAL, LINK_ONLY }

@Serializable
data class Step(
    val text: String,
    /** Heading this step belongs to, e.g. "Voor de saus". Null when the recipe has no sections. */
    val section: String? = null,
)

@Serializable
data class Ingredient(
    val text: String,
    /** Heading this line belongs to, e.g. "Voor de dressing". Null when the list has no groups. */
    val section: String? = null,
)

/**
 * A reader's own label, shared across recipes. Distinct from [Recipe.tags], which
 * are the site's words and stay read-only. [position] is the order the reader put
 * them in; it is not derived from the name.
 */
@Serializable
data class Label(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val position: Int = 0,
)

@Serializable
data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sourceUrl: String? = null,
    val siteName: String? = null,
    val author: String? = null,
    val description: String? = null,
    /** File name inside the app's images dir. Null when there is no picture. */
    val imageFile: String? = null,
    val imageUrl: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    /** Kept apart from [totalMinutes] when the page gives both; the total stays the one shown. */
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val servings: Int? = null,
    /** Original yield text, e.g. "15 stuks" — kept because it is often more informative than a number. */
    val servingsLabel: String? = null,
    val tags: List<String> = emptyList(),
    val labels: List<Label> = emptyList(),
    val notes: String = "",
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val checkedIngredients: Set<Int> = emptySet(),
    val checkedSteps: Set<Int> = emptySet(),
    val quality: ParseQuality = ParseQuality.FULL,
    /** Where the reader last left the servings stepper, so reopening does not reset it. */
    val cookedServings: Int? = null,
    /** Last "made it" tap, epoch millis. */
    val lastCookedAt: Long? = null,
    /** Last hand edit of ingredients or steps, so a refetch can warn before overwriting them. */
    val editedAt: Long? = null,
    val videoUrl: String? = null,
    /**
     * Set when the reader deletes the recipe. The row stays until it is purged or
     * restored; the normal list never shows it.
     */
    val deletedAt: Long? = null,
    /** A photographed recipe card kept beside the typed version, as a file in the images dir. */
    val attachmentFile: String? = null,
    /** First time the recipe screen was opened. Null means never. */
    val openedAt: Long? = null,
) {
    val hasContent: Boolean get() = ingredients.isNotEmpty() || steps.isNotEmpty()

    val isDeleted: Boolean get() = deletedAt != null

    /**
     * Everything the search box should look through, folded the way [foldForSearch]
     * folds the query: lower case and without accents, so "creme" finds "crème".
     */
    fun searchBlob(): String = buildString {
        append(title).append(' ')
        siteName?.let { append(it).append(' ') }
        author?.let { append(it).append(' ') }
        description?.let { append(it).append(' ') }
        tags.forEach { append(it).append(' ') }
        labels.forEach { append(it.name).append(' ') }
        ingredients.forEach { append(it.text).append(' ') }
        append(notes)
    }.let(::foldForSearch)

    // The wording for the time and the yield lives in ui/Labels.kt: a recipe is kept
    // for years and read in whichever language is set today, so only the numbers
    // belong here.
}
