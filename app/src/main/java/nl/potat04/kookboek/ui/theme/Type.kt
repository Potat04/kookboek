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
 */
private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif

val KookboekTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Normal,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Normal,
        fontSize = 21.sp, lineHeight = 27.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Medium,
        fontSize = 19.sp, lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    // Ingredients and steps: read from a phone propped up across the counter.
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.8.sp,
    ),
)
