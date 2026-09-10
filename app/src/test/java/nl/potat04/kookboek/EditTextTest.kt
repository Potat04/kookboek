package nl.potat04.kookboek

import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.ui.ingredientsToText
import nl.potat04.kookboek.ui.insertHeading
import nl.potat04.kookboek.ui.textToIngredients
import org.junit.Assert.assertEquals
import org.junit.Test

/** The text conventions the editor runs on: "# heading" lines, and the chip that writes one. */
class EditTextTest {

    @Test
    fun `a heading starts its own line`() {
        assertEquals("# " to 2, insertHeading("", 0))
        assertEquals("2 uien\n# " to 9, insertHeading("2 uien", 6))
        // Already at the start of a line, so no empty line is opened in front of it.
        assertEquals("2 uien\n# " to 9, insertHeading("2 uien\n", 7))
        // Mid-line: the caret is behind the marker, ready for the name of the group.
        assertEquals("2 ui\n# en\n1 ei" to 7, insertHeading("2 uien\n1 ei", 4))
    }

    @Test
    fun `groups survive the round trip through the boxes`() {
        val lines = listOf(
            Ingredient("200 g bloem", "Voor het deeg"),
            Ingredient("150 g boter", "Voor het deeg"),
            Ingredient("4 appels", "Voor de vulling"),
        )
        val text = ingredientsToText(lines)
        assertEquals(
            "# Voor het deeg\n200 g bloem\n150 g boter\n\n# Voor de vulling\n4 appels",
            text,
        )
        assertEquals(lines, textToIngredients(text))
    }
}
