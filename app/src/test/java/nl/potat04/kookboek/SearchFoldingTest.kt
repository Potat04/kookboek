package nl.potat04.kookboek

import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.foldForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typing without accents has to find recipes written with them.
 *
 * The words here are the ones that actually turn up: a Dutch page writes "crème
 * fraîche" and "jalapeño", and nobody holds down a key on a phone to type that back.
 * Both sides of the comparison go through [foldForSearch], so the test asserts the
 * pair and not the folding alone.
 */
class SearchFoldingTest {

    @Test
    fun `accents come off`() {
        assertEquals("creme fraiche", foldForSearch("Crème fraîche"))
        assertEquals("jalapeno", foldForSearch("jalapeño"))
        assertEquals("puree", foldForSearch("purée"))
        assertEquals("soufflé".let(::foldForSearch), "souffle")
        assertEquals("pate", foldForSearch("pâté"))
        assertEquals("uber", foldForSearch("Über"))
    }

    @Test
    fun `letters that are not a base plus an accent stay themselves`() {
        // No decomposition, so nothing to strip. Better a word that stays whole than a
        // fold that quietly turns it into something else.
        assertEquals("straße", foldForSearch("Straße"))
        assertEquals("smørrebrød", foldForSearch("Smørrebrød"))
    }

    @Test
    fun `folding is idempotent`() {
        val once = foldForSearch("Crème Brûlée")
        assertEquals(once, foldForSearch(once))
    }

    @Test
    fun `a recipe is found by its unaccented spelling`() {
        val recipe = Recipe(
            title = "Quiche met crème fraîche",
            siteName = "Allerhande",
            ingredients = listOf(Ingredient("2 jalapeños, fijngesneden")),
        )
        val blob = recipe.searchBlob()

        listOf("creme", "fraiche", "jalapeno", "crème", "QUICHE").forEach {
            assertTrue("\"$it\" should find the recipe", foldForSearch(it) in blob)
        }
        assertFalse("a word that is not in it must still miss", foldForSearch("tofu") in blob)
    }
}
