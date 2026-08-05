package nl.potat04.kookboek.parse

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Recalculates the amounts in an ingredient line when you cook for a different
 * number of people. Only the leading quantity is touched — "bak 3 minuten" further
 * down the line must stay put.
 */
object Scaling {

    private val VULGAR = mapOf(
        '½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875, '⅕' to 0.2, '⅖' to 0.4,
    )

    private const val NUM = "\\d+(?:[.,]\\d+)?"

    /** "1 1/2", "1/2", "1½", "½", "2,5", "2" — optionally as a range "2-3". */
    private val QUANTITY = Regex(
        "^\\s*(" +
            "$NUM\\s*[/⁄]\\s*$NUM" +          // 1/2
            "|$NUM\\s+$NUM\\s*[/⁄]\\s*$NUM" + // 1 1/2
            "|$NUM\\s*[${VULGAR.keys.joinToString("")}]" + // 1½
            "|[${VULGAR.keys.joinToString("")}]" +          // ½
            "|$NUM" +
            ")" +
            "(\\s*[-–—]\\s*(" +
            "$NUM\\s*[/⁄]\\s*$NUM|$NUM\\s*[${VULGAR.keys.joinToString("")}]" +
            "|[${VULGAR.keys.joinToString("")}]|$NUM))?"
    )

    fun scale(line: String, factor: Double): String {
        if (abs(factor - 1.0) < 0.001) return line
        val m = QUANTITY.find(line) ?: return line
        val low = parseAmount(m.groupValues[1]) ?: return line
        val high = m.groupValues[3].takeIf { it.isNotBlank() }?.let { parseAmount(it) }

        val scaled = if (high != null) {
            "${format(low * factor)}-${format(high * factor)}"
        } else {
            format(low * factor)
        }
        return scaled + line.substring(m.value.length)
    }

    fun parseAmount(raw: String): Double? {
        val s = raw.trim().replace(',', '.')
        if (s.isEmpty()) return null

        // A single vulgar fraction, possibly preceded by a whole number: 1½
        VULGAR.keys.firstOrNull { s.contains(it) }?.let { ch ->
            val whole = s.substringBefore(ch).trim().toDoubleOrNull() ?: 0.0
            return whole + VULGAR.getValue(ch)
        }
        // "1 1/2" or "3/4"
        val slash = s.indexOfFirst { it == '/' || it == '⁄' }
        if (slash >= 0) {
            val before = s.substring(0, slash).trim().split(Regex("\\s+"))
            val numerator = before.lastOrNull()?.toDoubleOrNull() ?: return null
            val whole = if (before.size > 1) before.first().toDoubleOrNull() ?: 0.0 else 0.0
            val denominator = s.substring(slash + 1).trim().toDoubleOrNull() ?: return null
            if (denominator == 0.0) return null
            return whole + numerator / denominator
        }
        return s.toDoubleOrNull()
    }

    /**
     * Cooks read "1½" far better than "1.5", and nobody wants "0.6666667 ui".
     * Above ten it flips to whole numbers: "141⅔ gr" is nonsense on a kitchen scale,
     * "142 gr" is what you would actually weigh out.
     */
    fun format(value: Double): String {
        if (value <= 0) return "0"
        if (abs(value - value.roundToInt()) < 0.02) return value.roundToInt().toString()
        if (value >= 10) return value.roundToInt().toString()

        val whole = floor(value).toInt()
        val rest = value - whole
        val nice = VULGAR.entries
            .filter { it.key in "½¼¾⅓⅔⅛" }
            .firstOrNull { abs(rest - it.value) < 0.03 }
        if (nice != null) return (if (whole > 0) "$whole" else "") + nice.key

        return String.format(Locale.getDefault(), "%.1f", value)
    }
}
