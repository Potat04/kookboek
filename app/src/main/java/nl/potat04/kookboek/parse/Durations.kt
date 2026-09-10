package nl.potat04.kookboek.parse

import kotlin.math.roundToInt

/** A spoken duration inside a piece of text: where it sits, and how long it is. */
data class Duration(val range: IntRange, val seconds: Int)

/**
 * Finds the durations a step talks about, in Dutch and English: "25 minuten", "1,5 uur",
 * "anderhalf uur", "1 hour 20 minutes", "2-3 min", "10 à 15 min". The screen underlines
 * them so a tap can start a kitchen timer.
 *
 * Pure text in, positions out. Deliberately modest: it reads what people write in
 * recipes, and nothing more. A range takes its larger end, because the timer that
 * goes off too late is the one that burns dinner.
 */
object Durations {

    fun find(text: String): List<Duration> {
        val found = mutableListOf<Duration>()
        SPECIALS.forEach { (regex, seconds) ->
            regex.findAll(text).forEach { m -> found += Duration(m.range, seconds) }
        }
        CLAUSE.findAll(text).forEach { m ->
            val amount = amountOf(m.groups["a"]!!.value) ?: return@forEach
            val other = m.groups["b"]?.value?.let(::amountOf)
            val unit = unitSeconds(m.groups["unit"]!!.value) ?: return@forEach
            val seconds = (maxOf(amount, other ?: amount) * unit).roundToInt()
            if (seconds > 0) found += Duration(m.range, seconds)
        }
        return merge(dropOverlaps(found.sortedWith(compareBy({ it.range.first }, { -it.range.last }))), text)
    }

    /** Keeps the earliest, longest match where two patterns claim the same text. */
    private fun dropOverlaps(sorted: List<Duration>): List<Duration> {
        val kept = mutableListOf<Duration>()
        sorted.forEach { d -> if (kept.none { it.range.last >= d.range.first }) kept += d }
        return kept
    }

    /** "1 uur en 20 minuten" is one duration, not two, if only "en" or a comma sits between. */
    private fun merge(list: List<Duration>, text: String): List<Duration> {
        val out = mutableListOf<Duration>()
        list.forEach { d ->
            val last = out.lastOrNull()
            if (last != null && JOIN.matches(text.substring(last.range.last + 1, d.range.first))) {
                out[out.lastIndex] = Duration(last.range.first..d.range.last, last.seconds + d.seconds)
            } else {
                out += d
            }
        }
        return out
    }

    private fun amountOf(raw: String): Double? {
        val s = raw.trim().lowercase()
        WORDS[s]?.let { return it }
        val fraction = s.lastOrNull()?.let { VULGAR[it] }
        val digits = if (fraction != null) s.dropLast(1).trim() else s
        val whole = if (digits.isEmpty()) 0.0 else digits.replace(',', '.').toDoubleOrNull() ?: return null
        return whole + (fraction ?: 0.0)
    }

    private fun unitSeconds(unit: String): Int? = when (unit.lowercase()) {
        "uur", "uren", "uurtje", "u", "hour", "hours", "hr", "hrs", "h" -> 3600
        "minuut", "minuten", "minuutje", "min", "mins", "minute", "minutes" -> 60
        "seconde", "seconden", "sec", "secs", "second", "seconds" -> 1
        else -> null
    }

    private val VULGAR = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75)

    private val WORDS = mapOf(
        "een" to 1.0, "één" to 1.0, "twee" to 2.0, "drie" to 3.0, "vier" to 4.0, "vijf" to 5.0,
        "zes" to 6.0, "zeven" to 7.0, "acht" to 8.0, "negen" to 9.0, "tien" to 10.0, "twaalf" to 12.0,
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
        "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "twelve" to 12.0, "anderhalf" to 1.5, "anderhalve" to 1.5,
    )

    private const val NUMBER = "\\d+(?:[.,]\\d+)?\\s*[½¼¾]?|[½¼¾]"
    private val AMOUNT = "(?:$NUMBER|\\b(?:${WORDS.keys.joinToString("|")})\\b)"
    private const val SEP = "(?:\\s*[-–—/]\\s*|\\s+(?:tot|to|à|of|or)\\s+)"
    private const val UNIT = "(?<unit>uurtje|uren|uur|u|hours?|hrs?|h|" +
        "minuutje|minuten|minuut|minutes?|mins?|" +
        "seconden|seconde|seconds?|secs?)"

    // No letter, digit or decimal mark right before the amount: "12 minuten" must not also
    // yield "2 minuten", and "1.5" must not be read as "5".
    private val CLAUSE = Regex(
        "(?<![\\p{L}\\d.,])(?<a>$AMOUNT)(?:$SEP(?<b>$AMOUNT))?\\s*$UNIT(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )

    /** Idioms with no number in them. */
    private val SPECIALS = listOf(
        Regex("(?<![\\p{L}])(?:een\\s+)?half\\s*uur(?:tje)?(?![\\p{L}])", RegexOption.IGNORE_CASE) to 1800,
        Regex("(?<![\\p{L}])half\\s+an\\s+hour(?![\\p{L}])", RegexOption.IGNORE_CASE) to 1800,
        Regex("(?<![\\p{L}])(?:een\\s+)?kwartier(?:tje)?(?![\\p{L}])", RegexOption.IGNORE_CASE) to 900,
        Regex("(?<![\\p{L}])(?:a\\s+)?quarter\\s+of\\s+an\\s+hour(?![\\p{L}])", RegexOption.IGNORE_CASE) to 900,
    )

    private val JOIN = Regex("\\s*(?:en|and|,)?\\s*", RegexOption.IGNORE_CASE)
}
