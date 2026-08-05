package nl.potat04.kookboek.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightPaper = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = TerracottaSoft,
    onPrimaryContainer = Color(0xFF54200C),
    secondary = InkMuted,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = PaperCard,
    onSurface = Ink,
    surfaceVariant = PaperSunk,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PaperCard,
    surfaceContainerLow = Color(0xFFFAF6EC),
    surfaceContainer = Paper,
    surfaceContainerHigh = PaperSunk,
    surfaceContainerHighest = Color(0xFFE7DFCD),
    outline = Rule,
    outlineVariant = Color(0xFFE6DDCA),
    error = Crimson,
    onError = Color(0xFFFFF8F0),
    scrim = Color(0x99120E08),
    // Snackbars are built from these; leaving them at the Material defaults puts a
    // lavender bar with purple buttons on top of the paper.
    inverseSurface = Color(0xFF2E2820),
    inverseOnSurface = Paper,
    inversePrimary = Color(0xFFE9A382),
)

private val DarkPaper = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1206),
    primaryContainer = AmberSoft,
    onPrimaryContainer = Color(0xFFFFD9C2),
    secondary = NightInkMuted,
    onSecondary = NightPaper,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = NightSunk,
    onSurfaceVariant = NightInkMuted,
    surfaceContainerLowest = Color(0xFF100E0A),
    surfaceContainerLow = Color(0xFF1A1712),
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightSunk,
    surfaceContainerHighest = Color(0xFF352F26),
    outline = NightRule,
    outlineVariant = Color(0xFF2E2921),
    error = NightCrimson,
    onError = Color(0xFF3A0F08),
    scrim = Color(0xAA000000),
    inverseSurface = Color(0xFFEDE4D4),
    inverseOnSurface = Color(0xFF221E18),
    inversePrimary = Terracotta,
)

@Composable
fun KookboekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPaper else LightPaper,
        typography = KookboekTypography,
        shapes = KookboekShapes,
        content = content,
    )
}
