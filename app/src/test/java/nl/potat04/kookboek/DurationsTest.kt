package nl.potat04.kookboek

import nl.potat04.kookboek.parse.Duration
import nl.potat04.kookboek.parse.Durations
import nl.potat04.kookboek.parse.RecipeParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Durations are checked against the steps of real pages first, and a list of phrases
 * second. The fixtures are the same ones the parser is tested on, so what the timer
 * offers is what people actually get shown.
 */
class DurationsTest {

    private fun steps(name: String, url: String): List<String> {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/$name.html")) {
            "missing fixture $name"
        }.bufferedReader().use { it.readText() }
        return RecipeParser.parse(html, url).steps.map { it.text }
    }

    /** What was found, as the words in the text paired with the seconds, so failures read well. */
    private fun found(text: String): List<Pair<String, Int>> =
        Durations.find(text).map { text.substring(it.range) to it.seconds }

    @Test
    fun `24kitchen, dutch with circa and hours`() {
        val steps = steps("24kitchen", "https://www.24kitchen.nl/recepten/boeuf-bourguignon")
        assertEquals(emptyList<Pair<String, Int>>(), found(steps[0]))
        assertEquals(listOf("5 minuten" to 300, "4 uur" to 14400), found(steps[2]))
        assertEquals(listOf("15 minuten" to 900), found(steps[3]))
    }

    @Test
    fun `bbcgoodfood, english with mins and an en dash range`() {
        val steps = steps("bbcgoodfood", "https://www.bbcgoodfood.com/recipes/classic-lasagne")
        // "a few mins" has no number and is rightly left alone.
        assertEquals(listOf("5 mins" to 300), found(steps[0]))
        assertEquals(listOf("1 min" to 60, "6 mins" to 360), found(steps[1]))
        assertEquals(listOf("1 min" to 60, "20 mins" to 1200), found(steps[2]))
        // "200C/180C fan/gas 6" must not become a timer.
        assertEquals(emptyList<Pair<String, Int>>(), found(steps[3]))
        assertEquals(listOf("25–30 mins" to 1800), found(steps[4]))
    }

    @Test
    fun `leukerecepten, dutch with een paar minuten left alone`() {
        val steps = steps("leukerecepten", "https://www.leukerecepten.nl/recepten/loempia-maken-in-de-airfryer/")
        assertEquals(emptyList<Pair<String, Int>>(), found(steps[2]))
        assertEquals(listOf("10 minuten" to 600), found(steps[6]))
    }

    @Test
    fun `cheffatty, seconds`() {
        val steps = steps("cheffatty", "https://www.cheffatty.com/recipes/chili-crisp-scallion-oil-noodles")
        assertEquals(listOf("20 seconds" to 20), found(steps[1]))
    }

    @Test
    fun `uitpaulineskeuken, hyphen range in dutch`() {
        val steps = steps("uitpaulineskeuken", "https://uitpaulineskeuken.nl/recept/gevulde-portobello")
        assertEquals(listOf("25-30 minuten" to 1800), found(steps[2]))
    }

    @Test
    fun `dutch phrases`() {
        assertEquals(listOf("25 minuten" to 1500), found("Bak 25 minuten in de oven."))
        assertEquals(listOf("25 min" to 1500), found("Bak 25 min in de oven."))
        // The full stop after an abbreviation is punctuation, and stays out of the underline.
        assertEquals(listOf("25 min" to 1500), found("Bak 25 min. in de oven."))
        assertEquals(listOf("1 uur" to 3600), found("Laat 1 uur rijzen."))
        assertEquals(listOf("1,5 uur" to 5400), found("Stoof 1,5 uur."))
        assertEquals(listOf("1½ uur" to 5400), found("Stoof 1½ uur."))
        assertEquals(listOf("anderhalf uur" to 5400), found("Laat anderhalf uur rijzen."))
        assertEquals(listOf("30 seconden" to 30), found("Roer 30 seconden."))
        assertEquals(listOf("30 sec" to 30), found("Roer 30 sec door."))
        assertEquals(listOf("2-3 minuten" to 180), found("Bak 2-3 minuten."))
        assertEquals(listOf("2 tot 3 minuten" to 180), found("Bak 2 tot 3 minuten."))
        assertEquals(listOf("10 à 15 min" to 900), found("Bak 10 à 15 min."))
        assertEquals(listOf("een half uur" to 1800), found("Laat een half uur staan."))
        assertEquals(listOf("een kwartier" to 900), found("Laat een kwartier rusten."))
        assertEquals(listOf("een uurtje" to 3600), found("Laat een uurtje trekken."))
        assertEquals(listOf("1 uur en 20 minuten" to 4800), found("Gaar 1 uur en 20 minuten."))
    }

    @Test
    fun `english phrases`() {
        assertEquals(listOf("20 minutes" to 1200), found("Bake for 20 minutes."))
        assertEquals(listOf("1 hour 20 minutes" to 4800), found("Roast for 1 hour 20 minutes."))
        assertEquals(listOf("1 hour and 20 minutes" to 4800), found("Roast for 1 hour and 20 minutes."))
        assertEquals(listOf("half an hour" to 1800), found("Rest for half an hour."))
        assertEquals(listOf("an hour" to 3600), found("Chill for an hour."))
        assertEquals(listOf("2 hrs" to 7200), found("Slow cook for 2 hrs."))
        assertEquals(listOf("30 secs" to 30), found("Blitz for 30 secs."))
        assertEquals(listOf("2 to 3 minutes" to 180), found("Fry for 2 to 3 minutes."))
    }

    @Test
    fun `numbers that are not durations stay untouched`() {
        assertEquals(emptyList<Pair<String, Int>>(), found("Verwarm de oven voor op 200 graden."))
        assertEquals(emptyList<Pair<String, Int>>(), found("Snipper 1 ui."))
        assertEquals(emptyList<Pair<String, Int>>(), found("125 gr taugé, 2 el olie, 3 tenen knoflook"))
        assertEquals(emptyList<Pair<String, Int>>(), found("Bak nog een paar minuten."))
        assertEquals(emptyList<Pair<String, Int>>(), found("Stap 3 van 5"))
    }

    @Test
    fun `a longer number is never read from its tail`() {
        assertEquals(listOf("12 minuten" to 720), found("Bak 12 minuten."))
        assertEquals(listOf("120 minutes" to 7200), found("Simmer 120 minutes."))
    }

    @Test
    fun `ranges point at the exact characters`() {
        val text = "Bak 2-3 minuten, dan 1 uur."
        assertEquals(
            listOf(Duration(4..14, 180), Duration(21..25, 3600)),
            Durations.find(text),
        )
    }
}
