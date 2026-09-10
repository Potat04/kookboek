package nl.potat04.kookboek.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.FailureReason
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.TextSize
import nl.potat04.kookboek.data.ThemeMode
import nl.potat04.kookboek.parse.RecipeParser
import nl.potat04.kookboek.parse.Scaling

/**
 * The place where stored data turns into a sentence in the reader's language.
 *
 * These used to be functions on [Recipe] returning Dutch ("15 min", "2 porties").
 * A recipe is kept for years and read in whichever language is set today, so the
 * label cannot be baked into the model — only the numbers are.
 */

@Composable
fun Recipe.timeText(): String? {
    // A page that gives prep and cooking time but no total still has a total.
    val minutes = totalMinutes
        ?: listOfNotNull(prepMinutes, cookMinutes).takeIf { it.isNotEmpty() }?.sum()
        ?: return null
    if (minutes <= 0) return null
    return minutesText(minutes)
}

/**
 * The recipe screen's version of [timeText]: "15 min prep, 40 min cooking" when the
 * page gave both, so you know whether the hour is yours or the oven's. The library
 * card keeps the total; it has one line and "15 stuks" to fit on it.
 */
@Composable
fun Recipe.timeDetailText(): String? {
    val prep = prepMinutes?.takeIf { it > 0 }
    val cook = cookMinutes?.takeIf { it > 0 }
    if (prep == null || cook == null) return timeText()
    return stringResource(R.string.recipe_time_prep_cook, minutesText(prep), minutesText(cook))
}

@Composable
private fun minutesText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.recipe_time_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.recipe_time_hours, minutes / 60)
    else -> stringResource(R.string.recipe_time_hours_minutes, minutes / 60, minutes % 60)
}

/** "Laatst gemaakt op 10 sep. 2026", in the phone's medium date format. Null until the first "Made it". */
@Composable
fun Recipe.lastMadeText(): String? {
    val at = lastCookedAt ?: return null
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(at))
    return stringResource(R.string.recipe_last_made, date)
}

/**
 * The site's own wording wins — "15 stuks" says more than "4 porties" ever will, and
 * it is what the page actually promised. Only when there is no such text do we phrase
 * the number ourselves, and then it follows the app language.
 *
 * The stored label goes through [RecipeParser.descriptiveYield] on the way out, not
 * just on the way in. Recipes saved by an earlier version have "4 porties" sitting in
 * the database, from back when the parser wrote that; recognising it as a plain count
 * here means those recipes say "4 servings" in English too, without a migration.
 *
 * Both the [count] and the [factor] belong here rather than at the call site. The
 * servings stepper changes what is on screen, and the two branches have to react to it
 * differently: the site's own words can only be rewritten as text, one number at a
 * time, while the plural resource needs the count itself to pick between "portie" and
 * "porties". Scaling a finished plural would give "1 porties".
 */
@Composable
fun Recipe.servingsText(count: Int? = servings, factor: Double = 1.0): String? =
    RecipeParser.descriptiveYield(servingsLabel)
        ?.let { if (factor == 1.0) it else Scaling.scale(it, factor) }
        ?: count?.let { pluralStringResource(R.plurals.recipe_servings_count, it, it) }

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

/**
 * Why an import came back empty-handed, said out loud.
 *
 * Every one of these names what to do next, because "could not fetch the page" leaves
 * the reader guessing whether to wait, retry, or go and open the page themselves.
 */
fun FailureReason.text(): UiText = UiText.Res(
    when (this) {
        FailureReason.NO_VALID_LINK -> R.string.error_no_valid_link
        FailureReason.OFFLINE -> R.string.edit_error_offline
        FailureReason.FETCH_FAILED -> R.string.error_fetch_failed
        FailureReason.BLOCKED -> R.string.error_blocked
        FailureReason.TIMED_OUT -> R.string.edit_error_timed_out
        FailureReason.NO_RECIPE_ON_PAGE -> R.string.edit_error_no_recipe
        FailureReason.NO_SOURCE_URL -> R.string.error_no_source_url
        FailureReason.NOTHING_SHARED -> R.string.error_nothing_shared
    }
)
