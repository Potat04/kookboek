package nl.potat04.kookboek

import nl.potat04.kookboek.ui.CookedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class CookedNoteTest {

    // 2026-09-10 00:00 UTC; the formatted date depends on the default zone, so pin it.
    private val at = 1_788_998_400_000L

    private fun <T> inUtc(block: () -> T): T {
        val before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try { return block() } finally { TimeZone.setDefault(before) }
    }

    @Test
    fun `an empty note leaves the notes alone`() {
        assertEquals("keep me", CookedNote.append("keep me", null, at, Locale.US))
        assertEquals("keep me", CookedNote.append("keep me", "   ", at, Locale.US))
    }

    @Test
    fun `the first note stands on its own`() = inUtc {
        assertEquals("Sep 10, 2026 · Less salt", CookedNote.append("", "Less salt", at, Locale.US))
    }

    @Test
    fun `later notes go under the existing ones with a blank line between`() = inUtc {
        val out = CookedNote.append("Oma's versie\n", "Minder zout", at, Locale("nl", "NL"))
        assertTrue(out, out.startsWith("Oma's versie\n\n"))
        assertTrue(out, out.endsWith(" · Minder zout"))
        assertTrue(out, out.contains("2026"))
    }
}
