package nl.potat04.kookboek.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Serif for the things you read (titles, steps), sans for the things you tap.
 * The device serif is used on purpose: no font downloads, no bundled megabytes,
 * and it already looks like a printed cookbook.
 *
 * The whole scale runs through [scale], which is the reader's text-size choice from
 * the settings screen. It multiplies size and leading together, so the rhythm of the
 * page survives at every setting instead of the lines closing up as the letters grow.
 *
 * Sizes are not Material's defaults. Material's body scale is built for dense list
 * UIs; this one is built for a phone standing up on the far side of a counter, which
 * is why the small end of the scale is a step or two larger than you would expect
 * and nothing here goes below 12sp.
 */
private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif

fun kookboekTypography(scale: Float = 1f): Typography {
    fun style(
        family: FontFamily,
        weight: FontWeight,
        size: Float,
        lineHeight: Float,
        letterSpacing: Float = 0f,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (lineHeight * scale).sp,
        // Tracking is an optical correction for the shape of the letter, not for how
        // big it is drawn, so it does not get scaled along.
        letterSpacing = letterSpacing.sp,
    )

    return Typography(
        // Recipe titles and the masthead. Titles wrap to two or three lines, where a
        // serif's descenders come close to the next cap line — hence the open leading.
        displaySmall = style(Serif, FontWeight.Normal, 32f, 40f, -0.3f),
        headlineMedium = style(Serif, FontWeight.Normal, 26f, 34f, -0.2f),
        // "Ingrediënten", "Bereiding".
        headlineSmall = style(Serif, FontWeight.Normal, 21f, 28f),
        // Card titles, two lines with an ellipsis more often than not.
        titleLarge = style(Serif, FontWeight.Medium, 19f, 26f),
        // "Porties", the step numbers, the recipe's own section names. At 15sp this
        // was the same size as bodyMedium, so a heading and its text differed only by
        // weight; 16sp gives it back a step of its own.
        titleMedium = style(Sans, FontWeight.Medium, 16f, 22f),
        // Ingredients and steps: read from a phone propped up across the counter.
        // Untouched — this is the one size nobody was complaining about.
        bodyLarge = style(Sans, FontWeight.Normal, 17f, 26f),
        // The longest muted paragraphs in the app live here: the recipe's headnote,
        // the empty states, the failure explanations. 1.5 leading is the right measure
        // for something you read rather than scan.
        bodyMedium = style(Sans, FontWeight.Normal, 16f, 24f),
        // Card meta lines, the byline, the share-sheet summary. This is the size the
        // complaint was actually about; 13sp sat below Android's own floor for
        // supporting text.
        bodySmall = style(Sans, FontWeight.Normal, 14f, 20f),
        // Buttons and chips. The explicit leading matters: without one, Compose falls
        // back to platform font metrics and a label centres differently in the FAB,
        // the chip and a TextButton.
        labelLarge = style(Sans, FontWeight.Medium, 15f, 20f, 0f),
        // The recipe count, "Bewaard in Kookboek", "Vinkjes wissen". The old 0.6sp of
        // tracking was ~5% of the em: at that size it read as smeared, not as small
        // caps, and it pushed long Dutch words toward truncation.
        labelMedium = style(Sans, FontWeight.Medium, 13f, 18f, 0.2f),
        // The smallest thing here, and it still carries real information: the tags,
        // "omgerekend vanaf 4", "alleen de link".
        labelSmall = style(Sans, FontWeight.Medium, 12f, 16f, 0.3f),
    )
}
