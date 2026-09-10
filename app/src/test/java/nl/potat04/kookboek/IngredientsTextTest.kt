package nl.potat04.kookboek

import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.ui.IngredientsText
import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientsTextTest {

    private val grouped = listOf(
        Ingredient("2 eieren", "Voor de cake"),
        Ingredient("100 g suiker", "Voor de cake"),
        Ingredient("1 citroen", "Voor het glazuur"),
    )

    @Test
    fun `one line per ingredient, headings on their own line`() {
        assertEquals(
            "Voor de cake\n2 eieren\n100 g suiker\n\nVoor het glazuur\n1 citroen",
            IngredientsText.clipboard(grouped),
        )
    }

    @Test
    fun `amounts scale with the stepper`() {
        assertEquals(
            listOf("Voor de cake", "4 eieren", "200 g suiker", "", "Voor het glazuur", "2 citroen"),
            IngredientsText.lines(grouped, 2.0),
        )
    }

    @Test
    fun `no groups means no headings and no blank lines`() {
        val plain = listOf(Ingredient("1 ui"), Ingredient("2 tenen knoflook"))
        assertEquals("1 ui\n2 tenen knoflook", IngredientsText.clipboard(plain))
    }

    @Test
    fun `a heading is written once for the lines under it`() {
        val lines = listOf(
            Ingredient("a"),
            Ingredient("b", "Saus"),
            Ingredient("c", "Saus"),
        )
        assertEquals(listOf("a", "", "Saus", "b", "c"), IngredientsText.lines(lines))
    }
}
