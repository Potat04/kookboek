package nl.potat04.kookboek.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.PaletteId

/**
 * The palettes you can pick between on the settings screen.
 *
 * All six are the same idea in a different colour: a sheet of paper, one ink, one
 * accent used sparingly, and cards edged with a printed rule instead of a shadow.
 * None of them is Material's dynamic colour — the paper is the point of the app.
 *
 * Every value here was checked against the contrast the app actually needs, and the
 * numbers behind them are in `.claude/knowledge/ui.md`. The three that matter most:
 * `onSurfaceVariant` reaches at least 6:1 on all three of its backgrounds (it carries
 * every summary and meta line, and at 5:1 it was the thing that made them hard to
 * read), `outline` reaches 1.86:1 against the page so a card has a visible edge, and
 * `inverseSurface`/`inversePrimary` are set by hand so the snackbar is not lavender.
 *
 * If you add a palette, fill in all 25 roles for both schemes and check those three.
 */
class Palette(
    val id: PaletteId,
    @param:StringRes val labelRes: Int,
    val light: ColorScheme,
    val dark: ColorScheme,
)


// ---------------------------------------------------------------------------
/*
 * Sinaasappel — the original: cream paper, ink, terracotta.
 *
 * The palette the app shipped with. The muted tier and the hairlines were
 * corrected (they carried every summary line at barely 5:1); the cream, the
 * ink, the amber and the crimson are untouched.
 */

// Daylight.
private val SinaasappelLight = lightColorScheme(
    // the accent
    primary = Color(0xFFA2441C),
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFF3E0D3),
    onPrimaryContainer = Color(0xFF54200C),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFF4F4634),
    onSecondary = Color(0xFFF6F1E6),
    surfaceVariant = Color(0xFFEDE6D6),
    onSurfaceVariant = Color(0xFF4F4634),
    // paper and ink
    background = Color(0xFFF6F1E6),
    onBackground = Color(0xFF211D18),
    surface = Color(0xFFFFFCF5),
    onSurface = Color(0xFF211D18),
    // the container ladder
    surfaceContainerLowest = Color(0xFFFFFCF5),
    surfaceContainerLow = Color(0xFFFAF6EC),
    surfaceContainer = Color(0xFFF6F1E6),
    surfaceContainerHigh = Color(0xFFEDE6D6),
    surfaceContainerHighest = Color(0xFFE7DFCD),
    // the printed rules
    outline = Color(0xFFBEB296),
    outlineVariant = Color(0xFFC8BB9E),
    // when something went wrong
    error = Color(0xFF9B3B2C),
    onError = Color(0xFFFFF8F0),
    scrim = Color(0x99120E08),
    // the snackbar is built from these three
    inverseSurface = Color(0xFF2E2820),
    inverseOnSurface = Color(0xFFF6F1E6),
    inversePrimary = Color(0xFFE9A382),
)

// The same room with the lights down.
private val SinaasappelDark = darkColorScheme(
    // the accent
    primary = Color(0xFFE08B5C),
    onPrimary = Color(0xFF2A1206),
    primaryContainer = Color(0xFF3A2318),
    onPrimaryContainer = Color(0xFFFFD9C2),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFFC2B6A2),
    onSecondary = Color(0xFF15130F),
    surfaceVariant = Color(0xFF2A251E),
    onSurfaceVariant = Color(0xFFC2B6A2),
    // paper and ink
    background = Color(0xFF15130F),
    onBackground = Color(0xFFEFE7D8),
    surface = Color(0xFF1E1B15),
    onSurface = Color(0xFFEFE7D8),
    // the container ladder
    surfaceContainerLowest = Color(0xFF100E0A),
    surfaceContainerLow = Color(0xFF1A1712),
    surfaceContainer = Color(0xFF1E1B15),
    surfaceContainerHigh = Color(0xFF2A251E),
    surfaceContainerHighest = Color(0xFF322C24),
    // the printed rules
    outline = Color(0xFF544B3E),
    outlineVariant = Color(0xFF4C4439),
    // when something went wrong
    error = Color(0xFFE08D7E),
    onError = Color(0xFF3A0F08),
    scrim = Color(0xAA000000),
    // the snackbar is built from these three
    inverseSurface = Color(0xFFF3EBDC),
    inverseOnSurface = Color(0xFF221E18),
    inversePrimary = Color(0xFFA2441C),
)

// ---------------------------------------------------------------------------
/*
 * Olijf — the same warm paper, but the accent is a deep earthy olive.
 *
 * For anyone who finds the orange too busy. Bay leaf and olive oil rather
 * than clay.
 */

// Daylight.
private val OlijfLight = lightColorScheme(
    // the accent
    primary = Color(0xFF55682B),
    onPrimary = Color(0xFFFBF7EC),
    primaryContainer = Color(0xFFE0E5CC),
    onPrimaryContainer = Color(0xFF25330E),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFF4C553F),
    onSecondary = Color(0xFFF7F4E9),
    surfaceVariant = Color(0xFFECE9D7),
    onSurfaceVariant = Color(0xFF4C553F),
    // paper and ink
    background = Color(0xFFF6F1E6),
    onBackground = Color(0xFF1D1F17),
    surface = Color(0xFFFFFCF3),
    onSurface = Color(0xFF1D1F17),
    // the container ladder
    surfaceContainerLowest = Color(0xFFFFFCF3),
    surfaceContainerLow = Color(0xFFFAF7EA),
    surfaceContainer = Color(0xFFF6F1E6),
    surfaceContainerHigh = Color(0xFFECE9D7),
    surfaceContainerHighest = Color(0xFFE6E3CF),
    // the printed rules
    outline = Color(0xFFB6B49C),
    outlineVariant = Color(0xFFC6C4AD),
    // when something went wrong
    error = Color(0xFF9A3B26),
    onError = Color(0xFFFBF7EC),
    scrim = Color(0x99131509),
    // the snackbar is built from these three
    inverseSurface = Color(0xFF272B1E),
    inverseOnSurface = Color(0xFFF3F1E2),
    inversePrimary = Color(0xFFB6C68E),
)

// The same room with the lights down.
private val OlijfDark = darkColorScheme(
    // the accent
    primary = Color(0xFFA9C084),
    onPrimary = Color(0xFF1D2810),
    primaryContainer = Color(0xFF2C3719),
    onPrimaryContainer = Color(0xFFD3E2B2),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFFB4BCA2),
    onSecondary = Color(0xFF13160F),
    surfaceVariant = Color(0xFF272C1F),
    onSurfaceVariant = Color(0xFFB4BCA2),
    // paper and ink
    background = Color(0xFF13160F),
    onBackground = Color(0xFFE9EDD9),
    surface = Color(0xFF1B1F15),
    onSurface = Color(0xFFE9EDD9),
    // the container ladder
    surfaceContainerLowest = Color(0xFF0E100A),
    surfaceContainerLow = Color(0xFF171A11),
    surfaceContainer = Color(0xFF1B1F15),
    surfaceContainerHigh = Color(0xFF272C1F),
    surfaceContainerHighest = Color(0xFF323828),
    // the printed rules
    outline = Color(0xFF464C39),
    outlineVariant = Color(0xFF414637),
    // when something went wrong
    error = Color(0xFFE49484),
    onError = Color(0xFF3A1109),
    scrim = Color(0xAA000000),
    // the snackbar is built from these three
    inverseSurface = Color(0xFFE9EDD9),
    inverseOnSurface = Color(0xFF20241A),
    inversePrimary = Color(0xFF41521C),
)

// ---------------------------------------------------------------------------
/*
 * Bosbes — cooler paper with a periwinkle cast and a deep indigo accent.
 *
 * The one cool palette in the set. The paper still must not read as clinical
 * white, only as a page in a colder light.
 */

// Daylight.
private val BosbesLight = lightColorScheme(
    // the accent
    primary = Color(0xFF3F3D96),
    onPrimary = Color(0xFFF7F7FD),
    primaryContainer = Color(0xFFDCDBF4),
    onPrimaryContainer = Color(0xFF22215B),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFF4F506E),
    onSecondary = Color(0xFFF7F7FD),
    surfaceVariant = Color(0xFFE1E1EF),
    onSurfaceVariant = Color(0xFF4B4D67),
    // paper and ink
    background = Color(0xFFECECF5),
    onBackground = Color(0xFF1B1B2C),
    surface = Color(0xFFF8F8FD),
    onSurface = Color(0xFF1B1B2C),
    // the container ladder
    surfaceContainerLowest = Color(0xFFF8F8FD),
    surfaceContainerLow = Color(0xFFF2F2FA),
    surfaceContainer = Color(0xFFECECF5),
    surfaceContainerHigh = Color(0xFFE3E3F1),
    surfaceContainerHighest = Color(0xFFD7D7EA),
    // the printed rules
    outline = Color(0xFFADADC7),
    outlineVariant = Color(0xFFBEBED0),
    // when something went wrong
    error = Color(0xFFA82F3D),
    onError = Color(0xFFFDF7F8),
    scrim = Color(0x99121128),
    // the snackbar is built from these three
    inverseSurface = Color(0xFF272843),
    inverseOnSurface = Color(0xFFECECF5),
    inversePrimary = Color(0xFFAEB4F6),
)

// The same room with the lights down.
private val BosbesDark = darkColorScheme(
    // the accent
    primary = Color(0xFF96A6FF),
    onPrimary = Color(0xFF101540),
    primaryContainer = Color(0xFF2A2D69),
    onPrimaryContainer = Color(0xFFD5D8FF),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFFACADCB),
    onSecondary = Color(0xFF101228),
    surfaceVariant = Color(0xFF232642),
    onSurfaceVariant = Color(0xFFACADCB),
    // paper and ink
    background = Color(0xFF101228),
    onBackground = Color(0xFFE5E5F5),
    surface = Color(0xFF191B36),
    onSurface = Color(0xFFE5E5F5),
    // the container ladder
    surfaceContainerLowest = Color(0xFF0B0C1D),
    surfaceContainerLow = Color(0xFF141631),
    surfaceContainer = Color(0xFF191B36),
    surfaceContainerHigh = Color(0xFF262946),
    surfaceContainerHighest = Color(0xFF313457),
    // the printed rules
    outline = Color(0xFF45476B),
    outlineVariant = Color(0xFF40425D),
    // when something went wrong
    error = Color(0xFFF09AA1),
    onError = Color(0xFF40101A),
    scrim = Color(0xAA05060F),
    // the snackbar is built from these three
    inverseSurface = Color(0xFFE5E5F5),
    inverseOnSurface = Color(0xFF1B1D34),
    inversePrimary = Color(0xFF3F3D96),
)

// ---------------------------------------------------------------------------
/*
 * Rabarber — blotting-paper pink with a fresh raspberry accent.
 *
 * Cheerful without shouting. The error colour deliberately does not sit on
 * the accent's hue, or a warning would look like a heading.
 */

// Daylight.
private val RabarberLight = lightColorScheme(
    // the accent
    primary = Color(0xFFBC2E4E),
    onPrimary = Color(0xFFFFF6F5),
    primaryContainer = Color(0xFFF6DCE0),
    onPrimaryContainer = Color(0xFF5C0F26),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFF67464E),
    onSecondary = Color(0xFFFFFBFA),
    surfaceVariant = Color(0xFFF1E3E2),
    onSurfaceVariant = Color(0xFF67464E),
    // paper and ink
    background = Color(0xFFF9F0EF),
    onBackground = Color(0xFF241A1C),
    surface = Color(0xFFFFFBFA),
    onSurface = Color(0xFF241A1C),
    // the container ladder
    surfaceContainerLowest = Color(0xFFFFFBFA),
    surfaceContainerLow = Color(0xFFFDF6F5),
    surfaceContainer = Color(0xFFF9F0EF),
    surfaceContainerHigh = Color(0xFFF1E3E2),
    surfaceContainerHighest = Color(0xFFE9D6D5),
    // the printed rules
    outline = Color(0xFFC8ADAF),
    outlineVariant = Color(0xFFD2BEC0),
    // when something went wrong
    error = Color(0xFF8A5417),
    onError = Color(0xFFFFF6F0),
    scrim = Color(0x99241A1C),
    // the snackbar is built from these three
    inverseSurface = Color(0xFF3A2A2E),
    inverseOnSurface = Color(0xFFFBF1F0),
    inversePrimary = Color(0xFFF0A9B6),
)

// The same room with the lights down.
private val RabarberDark = darkColorScheme(
    // the accent
    primary = Color(0xFFF2A0AE),
    onPrimary = Color(0xFF3A0E1B),
    primaryContainer = Color(0xFF4A1E29),
    onPrimaryContainer = Color(0xFFFFD6DE),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFFC4A9AF),
    onSecondary = Color(0xFF1A1114),
    surfaceVariant = Color(0xFF2F2227),
    onSurfaceVariant = Color(0xFFC4A9AF),
    // paper and ink
    background = Color(0xFF1A1114),
    onBackground = Color(0xFFF4E6E4),
    surface = Color(0xFF241A1D),
    onSurface = Color(0xFFF4E6E4),
    // the container ladder
    surfaceContainerLowest = Color(0xFF100A0C),
    surfaceContainerLow = Color(0xFF1F1518),
    surfaceContainer = Color(0xFF241A1D),
    surfaceContainerHigh = Color(0xFF2F2227),
    surfaceContainerHighest = Color(0xFF3B2C31),
    // the printed rules
    outline = Color(0xFF564549),
    outlineVariant = Color(0xFF4D4144),
    // when something went wrong
    error = Color(0xFFE0A257),
    onError = Color(0xFF3A1D06),
    scrim = Color(0xAA0D0709),
    // the snackbar is built from these three
    inverseSurface = Color(0xFFF0E2E0),
    inverseOnSurface = Color(0xFF2A1E21),
    inversePrimary = Color(0xFFB22945),
)

// ---------------------------------------------------------------------------
/*
 * Espresso — a properly yellowed page and burnt-coffee brown.
 *
 * The old-cookbook palette: high contrast, very little colour.
 */

// Daylight.
private val EspressoLight = lightColorScheme(
    // the accent
    primary = Color(0xFF6B3A1C),
    onPrimary = Color(0xFFFCF4E2),
    primaryContainer = Color(0xFFE7D3B4),
    onPrimaryContainer = Color(0xFF3A1D08),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFF5C4830),
    onSecondary = Color(0xFFFCF4E2),
    surfaceVariant = Color(0xFFE0D1AF),
    onSurfaceVariant = Color(0xFF574530),
    // paper and ink
    background = Color(0xFFEBDEC1),
    onBackground = Color(0xFF231810),
    surface = Color(0xFFF8EED6),
    onSurface = Color(0xFF231810),
    // the container ladder
    surfaceContainerLowest = Color(0xFFFCF5E4),
    surfaceContainerLow = Color(0xFFF3E8CC),
    surfaceContainer = Color(0xFFEBDEC1),
    surfaceContainerHigh = Color(0xFFE3D5B6),
    surfaceContainerHighest = Color(0xFFD9C9A6),
    // the printed rules
    outline = Color(0xFFAB9876),
    outlineVariant = Color(0xFFC8B692),
    // when something went wrong
    error = Color(0xFF8C2F17),
    onError = Color(0xFFFCF4E2),
    scrim = Color(0x99160E06),
    // the snackbar is built from these three
    inverseSurface = Color(0xFF2C1F14),
    inverseOnSurface = Color(0xFFF4E9D2),
    inversePrimary = Color(0xFFD9A76E),
)

// The same room with the lights down.
private val EspressoDark = darkColorScheme(
    // the accent
    primary = Color(0xFFC98F55),
    onPrimary = Color(0xFF251306),
    primaryContainer = Color(0xFF3B2410),
    onPrimaryContainer = Color(0xFFEDD3AC),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFFC0AB8A),
    onSecondary = Color(0xFF241708),
    surfaceVariant = Color(0xFF261A0F),
    onSurfaceVariant = Color(0xFFBBA684),
    // paper and ink
    background = Color(0xFF0F0B07),
    onBackground = Color(0xFFEFE2C9),
    surface = Color(0xFF191108),
    onSurface = Color(0xFFEFE2C9),
    // the container ladder
    surfaceContainerLowest = Color(0xFF0B0805),
    surfaceContainerLow = Color(0xFF15100A),
    surfaceContainer = Color(0xFF191108),
    surfaceContainerHigh = Color(0xFF261A0F),
    surfaceContainerHighest = Color(0xFF332516),
    // the printed rules
    outline = Color(0xFF5A4830),
    outlineVariant = Color(0xFF493B2B),
    // when something went wrong
    error = Color(0xFFE0937B),
    onError = Color(0xFF35100A),
    scrim = Color(0xAA080502),
    // the snackbar is built from these three
    inverseSurface = Color(0xFFEEE2C9),
    inverseOnSurface = Color(0xFF261A0F),
    inversePrimary = Color(0xFF6E3C1D),
)

// ---------------------------------------------------------------------------
/*
 * Inkt — near-monochrome, for the highest legibility in the set.
 *
 * For reading in full sun or with tired eyes. Off-white paper and off-black
 * ink, and the accent is a dark blue-black fountain-pen ink rather than a
 * colour. Every reading here clears the bar by the widest margin.
 */

// Daylight.
private val InktLight = lightColorScheme(
    // the accent
    primary = Color(0xFF1B2A44),
    onPrimary = Color(0xFFFBF8F1),
    primaryContainer = Color(0xFFDDE4EF),
    onPrimaryContainer = Color(0xFF14213A),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFF3A3F49),
    onSecondary = Color(0xFFF8F5ED),
    surfaceVariant = Color(0xFFEDE9DF),
    onSurfaceVariant = Color(0xFF423F38),
    // paper and ink
    background = Color(0xFFF8F5ED),
    onBackground = Color(0xFF12110E),
    surface = Color(0xFFFDFBF5),
    onSurface = Color(0xFF12110E),
    // the container ladder
    surfaceContainerLowest = Color(0xFFFFFEFA),
    surfaceContainerLow = Color(0xFFFBF8F1),
    surfaceContainer = Color(0xFFF8F5ED),
    surfaceContainerHigh = Color(0xFFF2EEE4),
    surfaceContainerHighest = Color(0xFFEAE5D9),
    // the printed rules
    outline = Color(0xFFA6A199),
    outlineVariant = Color(0xFFC6C1B7),
    // when something went wrong
    error = Color(0xFF8C1D12),
    onError = Color(0xFFFBF8F1),
    scrim = Color(0xB30E0D0A),
    // the snackbar is built from these three
    inverseSurface = Color(0xFF1C222E),
    inverseOnSurface = Color(0xFFF4F1E9),
    inversePrimary = Color(0xFFB6C7DE),
)

// The same room with the lights down.
private val InktDark = darkColorScheme(
    // the accent
    primary = Color(0xFFB4C8E4),
    onPrimary = Color(0xFF0C1420),
    primaryContainer = Color(0xFF1D2C42),
    onPrimaryContainer = Color(0xFFD6E2F2),
    // the muted tier — this is what carries every summary and meta line
    secondary = Color(0xFFAAB0BA),
    onSecondary = Color(0xFF10131A),
    surfaceVariant = Color(0xFF23272F),
    onSurfaceVariant = Color(0xFFC3C6CC),
    // paper and ink
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFF2F0EA),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFF2F0EA),
    // the container ladder
    surfaceContainerLowest = Color(0xFF0A0C0F),
    surfaceContainerLow = Color(0xFF12151A),
    surfaceContainer = Color(0xFF171A20),
    surfaceContainerHigh = Color(0xFF23272F),
    surfaceContainerHighest = Color(0xFF2E333C),
    // the printed rules
    outline = Color(0xFF4E545E),
    outlineVariant = Color(0xFF3C424B),
    // when something went wrong
    error = Color(0xFFF0A79A),
    onError = Color(0xFF3A0D07),
    scrim = Color(0xB3000000),
    // the snackbar is built from these three
    inverseSurface = Color(0xFFEEEBE3),
    inverseOnSurface = Color(0xFF1A1D23),
    inversePrimary = Color(0xFF23364F),
)

val Palettes: List<Palette> = listOf(
    Palette(PaletteId.SINAASAPPEL, R.string.palette_sinaasappel, SinaasappelLight, SinaasappelDark),
    Palette(PaletteId.OLIJF, R.string.palette_olijf, OlijfLight, OlijfDark),
    Palette(PaletteId.BOSBES, R.string.palette_bosbes, BosbesLight, BosbesDark),
    Palette(PaletteId.RABARBER, R.string.palette_rabarber, RabarberLight, RabarberDark),
    Palette(PaletteId.ESPRESSO, R.string.palette_espresso, EspressoLight, EspressoDark),
    Palette(PaletteId.INKT, R.string.palette_inkt, InktLight, InktDark),
)

/**
 * A stored palette name that no longer exists falls back to the first one, so an
 * older preference can never leave the app with no colours at all.
 */
fun paletteFor(id: PaletteId): Palette =
    Palettes.firstOrNull { it.id == id } ?: Palettes.first()
