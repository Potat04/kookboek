package nl.potat04.kookboek.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The one line a reader may leave when tapping "Made it", stamped with the day and
 * appended to the notes. The date is written out in the locale of that moment: notes
 * are the reader's own text, so unlike the labels elsewhere they can hold a language.
 */
object CookedNote {

    fun append(notes: String, note: String?, at: Long, locale: Locale): String {
        val line = note?.trim().orEmpty()
        if (line.isEmpty()) return notes
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(at))
        val entry = "$date · $line"
        return if (notes.isBlank()) entry else notes.trimEnd() + "\n\n" + entry
    }
}
