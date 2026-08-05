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
    val ingredients: List<String> = emptyList(),
    val steps: List<Step> = emptyList(),
    val totalMinutes: Int? = null,
    val servings: Int? = null,
    /** Original yield text, e.g. "15 stuks" — kept because it is often more informative than a number. */
    val servingsLabel: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val checkedIngredients: Set<Int> = emptySet(),
    val checkedSteps: Set<Int> = emptySet(),
    val quality: ParseQuality = ParseQuality.FULL,
) {
    val hasContent: Boolean get() = ingredients.isNotEmpty() || steps.isNotEmpty()

    /** Everything the search box should look through. */
    fun searchBlob(): String = buildString {
        append(title).append(' ')
        siteName?.let { append(it).append(' ') }
        author?.let { append(it).append(' ') }
        description?.let { append(it).append(' ') }
        tags.forEach { append(it).append(' ') }
        ingredients.forEach { append(it).append(' ') }
        append(notes)
    }.lowercase()

    fun timeLabel(): String? {
        val m = totalMinutes ?: return null
        if (m <= 0) return null
        return when {
            m < 60 -> "$m min"
            m % 60 == 0 -> "${m / 60} uur"
            else -> "${m / 60} u ${m % 60} m"
        }
    }

    fun servingsLabelOrNull(): String? = servingsLabel
        ?: servings?.let { if (it == 1) "1 portie" else "$it porties" }
}
