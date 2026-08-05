package nl.potat04.kookboek.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import nl.potat04.kookboek.data.Settings
import nl.potat04.kookboek.data.ThemeMode

/**
 * Wraps the app in the palette, the type scale and the shapes the reader chose.
 *
 * The colours themselves are in [Palettes]. There is no dynamic colour: the paper
 * identity is the point of the app, so the choice is between six papers, not between
 * whatever the wallpaper happens to be.
 */
@Composable
fun KookboekTheme(
    settings: Settings,
    /**
     * False for the share sheet: it floats over the browser, and the status bar on
     * screen at that moment belongs to the browser, not to us.
     */
    applySystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = settings.darkTheme()
    val colors = remember(settings.palette, dark) { paletteFor(settings.palette).scheme(dark) }
    val typography = remember(settings.textSize) { kookboekTypography(settings.textSize.scale) }

    if (applySystemBars) SystemBarIcons(dark)

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = KookboekShapes,
        content = content,
    )
}

@Composable
private fun Settings.darkTheme(): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun Palette.scheme(dark: Boolean): ColorScheme = if (dark) this.dark else light

/**
 * The line around something you tap or type into.
 *
 * `outline` is deliberately faint: it is a rule printed around a card, and at roughly
 * 2:1 against the page that is exactly what it looks like. But a text field's border
 * is the only thing marking where the field is, and WCAG asks 3:1 for the boundary of
 * a control. So the two get separate lines instead of one compromise that is either a
 * heavy card or an invisible field.
 *
 * Blending toward the muted ink rather than picking a colour keeps this in each
 * palette's own hue; measured across all six palettes in both schemes it lands
 * between 3.3:1 and 6.2:1 on every background it is drawn against.
 */
val ColorScheme.controlOutline: Color get() = lerp(outline, onSurfaceVariant, 0.55f)

/**
 * The clock and the battery icon sit on our own paper, because the app draws edge to
 * edge and [PaperBackground] fills the strip behind them. Their colour therefore has
 * to follow the palette the reader picked, not whatever the phone is set to — pick
 * "Donker" on a phone in light mode and the icons have to flip with the app.
 */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val light = APPEARANCE_LIGHT_STATUS_BARS or APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
    }
}

/**
 * Paints the window behind the app in the chosen paper. Call this from `onCreate`,
 * before `setContent`.
 *
 * `themes.xml` can only name one colour, and it names the default palette's. Anyone
 * who picked a different one would otherwise get a flash of cream paper on every cold
 * start, before the first frame is drawn. This is early enough to prevent that;
 * Compose is not, because the flash happens before Compose runs at all.
 */
fun Activity.paintWindowFor(settings: Settings) {
    val dark = when (settings.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM ->
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }
    val background = paletteFor(settings.palette).scheme(dark).background
    window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
}
