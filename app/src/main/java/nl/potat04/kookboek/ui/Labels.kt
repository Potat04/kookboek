package nl.potat04.kookboek.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.FailureReason
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.TextSize
import nl.potat04.kookboek.data.ThemeMode

/**
 * The place where stored data turns into a sentence in the reader's language.
 *
 * These used to be functions on [Recipe] returning Dutch ("15 min", "2 porties").
 * A recipe is kept for years and read in whichever language is set today, so the
 * label cannot be baked into the model — only the numbers are.
 */

@Composable
fun Recipe.timeText(): String? {
    val minutes = totalMinutes ?: return null
    if (minutes <= 0) return null
    return when {
        minutes < 60 -> stringResource(R.string.recipe_time_minutes, minutes)
        minutes % 60 == 0 -> stringResource(R.string.recipe_time_hours, minutes / 60)
        else -> stringResource(R.string.recipe_time_hours_minutes, minutes / 60, minutes % 60)
    }
}

/**
 * The site's own wording wins — "15 stuks" says more than "4 porties" ever will, and
 * it is what the page actually promised. Only when there is no such text do we phrase
 * the number ourselves, and then it follows the app language.
 */
@Composable
fun Recipe.servingsText(): String? = servingsLabel
    ?: servings?.let { pluralStringResource(R.plurals.recipe_servings_count, it, it) }

/** A recipe whose page gave no title at all still needs something on the card. */
@Composable
fun Recipe.displayTitle(): String =
    title.ifBlank { stringResource(R.string.recipe_untitled) }

@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_follow_system
        ThemeMode.LIGHT -> R.string.settings_mode_light
        ThemeMode.DARK -> R.string.settings_mode_dark
    }

@get:StringRes
val TextSize.labelRes: Int
    get() = when (this) {
        TextSize.COMPACT -> R.string.settings_text_compact
        TextSize.NORMAL -> R.string.settings_text_normal
        TextSize.LARGE -> R.string.settings_text_large
        TextSize.HUGE -> R.string.settings_text_huge
    }

/** Why an import came back empty-handed, said out loud. */
fun FailureReason.text(): UiText = UiText.Res(
    when (this) {
        FailureReason.NO_VALID_LINK -> R.string.error_no_valid_link
        FailureReason.FETCH_FAILED -> R.string.error_fetch_failed
        FailureReason.NO_SOURCE_URL -> R.string.error_no_source_url
        FailureReason.NOTHING_SHARED -> R.string.error_nothing_shared
    }
)
