package nl.potat04.kookboek.data

/**
 * What the reader chose about how the app looks. The colours themselves live in
 * `ui/theme/Palettes.kt` — this layer only remembers *which* one was picked, so the
 * stored value stays a stable name that survives a repaint of the palette.
 */
enum class PaletteId(val stored: String) {
    SINAASAPPEL("sinaasappel"),
    OLIJF("olijf"),
    BOSBES("bosbes"),
    RABARBER("rabarber"),
    ESPRESSO("espresso"),
    INKT("inkt"),
    ;

    companion object {
        fun of(stored: String?): PaletteId =
            entries.firstOrNull { it.stored == stored } ?: SINAASAPPEL
    }
}

/** Follow the phone, or override it — some people cook in a dark kitchen at noon. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * A multiplier over the whole type scale. This is deliberately separate from the
 * system font size: the phone is propped up across the counter, which asks for
 * something bigger than the same phone held in your hand.
 */
enum class TextSize(val scale: Float) {
    COMPACT(0.92f),
    NORMAL(1.0f),
    LARGE(1.12f),
    HUGE(1.25f),
}

data class Settings(
    val palette: PaletteId = PaletteId.SINAASAPPEL,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
)
