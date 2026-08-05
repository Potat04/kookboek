package nl.potat04.kookboek

import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.parse.ParseSource
import nl.potat04.kookboek.parse.ParsedRecipe
import nl.potat04.kookboek.parse.RecipeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is tested against real pages saved under src/test/resources/fixtures,
 * because "works on my hand-written HTML" says nothing about the actual web.
 */
class RecipeParserTest {

    private fun fixture(name: String, url: String): ParsedRecipe {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/$name.html")) {
            "missing fixture $name"
        }.bufferedReader().use { it.readText() }
        return RecipeParser.parse(html, url)
    }

    @Test
    fun `cheffatty json-ld`() {
        val r = fixture("cheffatty", "https://www.cheffatty.com/recipes/chili-crisp-scallion-oil-noodles")
        assertEquals("Chili Crisp Scallion Noodles", r.title)
        assertEquals(ParseSource.JSON_LD, r.source)
        assertEquals(13, r.ingredients.size)
        assertEquals(4, r.steps.size)
        assertEquals(15, r.totalMinutes)
        assertEquals(2, r.servings)
        assertEquals("cheffatty.com", r.siteName)
        assertTrue(r.ingredients.first().contains("Scallions"))
        assertTrue(r.steps.first().text.startsWith("Boil dried udon"))
        assertNotNull(r.imageUrl)
        assertEquals(ParseQuality.FULL, r.quality)
    }

    @Test
    fun `leukerecepten flattens HowToSection and keeps dutch yield`() {
        val r = fixture("leukerecepten", "https://www.leukerecepten.nl/recepten/loempia-maken-in-de-airfryer/")
        assertEquals("Loempia maken in de Airfryer", r.title)
        assertEquals(15, r.ingredients.size)
        assertTrue("steps were not flattened out of the section", r.steps.size >= 4)
        assertEquals(50, r.totalMinutes)
        assertEquals(15, r.servings)
        assertEquals("15 stuks", r.servingsLabel)
        assertEquals(ParseQuality.FULL, r.quality)
        assertEquals("Sandra Waterschoot", r.author)
        assertTrue(r.ingredients.any { it.contains("loempiavellen") })
    }

    @Test
    fun `english yields are said in dutch`() {
        assertEquals("4 porties", RecipeParser.localizeYield("4 persons"))
        assertEquals("2 porties", RecipeParser.localizeYield("2 servings"))
        assertEquals("1 portie", RecipeParser.localizeYield("1 serving"))
        assertEquals("6 porties", RecipeParser.localizeYield("Serves 6"))
        // Anything more specific than a serving count carries information — leave it.
        assertEquals("15 stuks", RecipeParser.localizeYield("15 stuks"))
        assertEquals("1 loaf", RecipeParser.localizeYield("1 loaf"))
        assertEquals("24 koekjes", RecipeParser.localizeYield("24 koekjes"))
    }

    @Test
    fun `24kitchen handles P0DT0H30M durations`() {
        val r = fixture("24kitchen", "https://www.24kitchen.nl/recepten/boeuf-bourguignon")
        assertEquals("Boeuf bourguignon", r.title)
        assertEquals(30, r.totalMinutes)
        assertEquals(4, r.servings)
        assertEquals(15, r.ingredients.size)
        assertEquals(4, r.steps.size)
        // "Stap 1" lives in `name`, the real text in `text` — we must pick the text.
        assertTrue(r.steps.first().text.startsWith("Pel en snipper"))
    }

    @Test
    fun `bbcgoodfood numeric yield and image list`() {
        val r = fixture("bbcgoodfood", "https://www.bbcgoodfood.com/recipes/classic-lasagne")
        assertEquals("Easy classic lasagne", r.title)
        assertEquals(75, r.totalMinutes)
        assertEquals(6, r.servings)
        assertEquals("6 porties", r.servingsLabel)
        assertEquals(5, r.steps.size)
        assertTrue(r.imageUrl!!.startsWith("https://"))
        assertTrue(r.tags.isNotEmpty())
    }

    @Test
    fun `page without any recipe data still yields a usable entry`() {
        val r = fixture("ah", "https://www.ah.nl/allerhande/recept/R-R1197438/pasta-pesto-met-kip")
        assertTrue("must never return a blank title", r.title.isNotBlank())
        assertEquals(ParseQuality.LINK_ONLY, r.quality)
    }

    @Test
    fun `falls back to head tags when there is no structured data`() {
        val html = """
            <html><head>
              <meta property="og:title" content="Zelfgemaakte pesto">
              <meta property="og:image" content="https://example.com/pesto.jpg">
              <meta property="og:description" content="In tien minuten klaar.">
              <meta property="og:site_name" content="Kooksels">
            </head><body>
              <h2>Ingrediënten</h2>
              <ul><li>50 g basilicum</li><li>2 tenen knoflook</li><li>100 ml olijfolie</li></ul>
              <h2>Bereiding</h2>
              <ol><li>Doe alles in de blender.</li><li>Meng tot een gladde massa.</li></ol>
            </body></html>
        """.trimIndent()
        val r = RecipeParser.parse(html, "https://kooksels.nl/pesto")
        assertEquals("Zelfgemaakte pesto", r.title)
        assertEquals("Kooksels", r.siteName)
        assertEquals("https://example.com/pesto.jpg", r.imageUrl)
        assertEquals(listOf("50 g basilicum", "2 tenen knoflook", "100 ml olijfolie"), r.ingredients)
        assertEquals(2, r.steps.size)
        assertEquals(ParseSource.HTML_HEURISTIC, r.source)
    }

    @Test
    fun `reads wp recipe maker markup`() {
        val html = """
            <html><head><title>Appeltaart - Ovenliefde</title></head><body>
              <h1>Appeltaart</h1>
              <div class="wprm-recipe-ingredients">
                <li class="wprm-recipe-ingredient">200 g bloem</li>
                <li class="wprm-recipe-ingredient">150 g boter</li>
                <li class="wprm-recipe-ingredient">4 appels</li>
              </div>
              <div class="wprm-recipe-instruction-text">Verwarm de oven voor op 180 graden.</div>
              <div class="wprm-recipe-instruction-text">Kneed een deeg van bloem en boter.</div>
            </body></html>
        """.trimIndent()
        val r = RecipeParser.parse(html, "https://ovenliefde.nl/appeltaart")
        assertEquals("Appeltaart", r.title)
        assertEquals(3, r.ingredients.size)
        assertEquals(2, r.steps.size)
    }

    @Test
    fun `title falls back to the page title minus the site suffix`() {
        val html = "<html><head><title>Snelle tomatensoep | Lekker Simpel</title></head><body></body></html>"
        val r = RecipeParser.parse(html, "https://lekkersimpel.nl/snelle-tomatensoep")
        assertEquals("Snelle tomatensoep", r.title)
    }

    @Test
    fun `title falls back to the url slug when there is nothing else`() {
        val r = RecipeParser.parse("<html></html>", "https://blog.nl/recepten/rode-linzensoep")
        assertEquals("Rode linzensoep", r.title)
    }

    @Test
    fun `iso durations`() {
        assertEquals(15, RecipeParser.duration("PT15M"))
        assertEquals(75, RecipeParser.duration("PT1H15M"))
        assertEquals(30, RecipeParser.duration("P0DT0H30M"))
        assertEquals(50, RecipeParser.duration("PT0H50M"))
        assertEquals(1500, RecipeParser.duration("P1DT1H"))
        assertEquals(null, RecipeParser.duration("PT0M"))
        assertEquals(null, RecipeParser.duration("gisteren"))
        assertEquals(null, RecipeParser.duration(null))
    }

    @Test
    fun `instruction blob with embedded html becomes separate steps`() {
        val html = """
            <html><body>
            <script type="application/ld+json">
            {"@type":"Recipe","name":"Test","recipeIngredient":["1 ei"],
             "recipeInstructions":"<ol><li>Kook het ei.</li><li>Pel het ei.</li></ol>"}
            </script>
            </body></html>
        """.trimIndent()
        val r = RecipeParser.parse(html, "https://x.nl/test")
        assertEquals(2, r.steps.size)
        assertEquals("Kook het ei.", r.steps[0].text)
    }

    @Test
    fun `recipe nested in an at-graph is found`() {
        val html = """
            <html><body>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebPage","name":"Niet dit"},
              {"@type":["Recipe","Article"],"name":"Wel dit","recipeIngredient":["2 uien"],
               "recipeInstructions":[{"@type":"HowToStep","text":"Snipper de uien."}]}
            ]}
            </script>
            </body></html>
        """.trimIndent()
        val r = RecipeParser.parse(html, "https://x.nl/test")
        assertEquals("Wel dit", r.title)
        assertEquals(listOf("2 uien"), r.ingredients)
        assertEquals(1, r.steps.size)
    }

    @Test
    fun `broken json-ld does not sink the whole parse`() {
        val html = """
            <html><head><meta property="og:title" content="Reddingsboei"></head><body>
            <script type="application/ld+json">{ this is not json at all ,,, }</script>
            <h2>Ingrediënten</h2><ul><li>1 ui</li><li>2 tomaten</li></ul>
            </body></html>
        """.trimIndent()
        val r = RecipeParser.parse(html, "https://x.nl/test")
        assertEquals("Reddingsboei", r.title)
        assertEquals(2, r.ingredients.size)
    }
}
