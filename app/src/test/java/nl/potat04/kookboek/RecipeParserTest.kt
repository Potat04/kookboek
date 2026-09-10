package nl.potat04.kookboek

import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.parse.ParseSource
import nl.potat04.kookboek.parse.ParsedRecipe
import nl.potat04.kookboek.parse.RecipeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private val ParsedRecipe.lines: List<String> get() = ingredients.map { it.text }

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
        assertTrue(r.lines.first().contains("Scallions"))
        assertTrue(r.steps.first().text.startsWith("Boil dried udon"))
        assertNotNull(r.imageUrl)
        assertEquals(ParseQuality.FULL, r.quality)
        // Only a cookTime, so the total is that same quarter of an hour.
        assertNull(r.prepMinutes)
        assertEquals(15, r.cookMinutes)
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
        assertTrue(r.lines.any { it.contains("loempiavellen") })
        // Prep and cook are published apart here; the total is the site's own, not the sum.
        assertEquals(40, r.prepMinutes)
        assertEquals(10, r.cookMinutes)
        // The one fixture with a video: contentUrl wins over the embed player.
        assertEquals(
            "https://www.youtube.com/watch?v=pdenqmIVPpc&ab_channel=Leukerecepten.nl",
            r.videoUrl,
        )
    }

    @Test
    fun `a plain serving count is left to the screen to word`() {
        // The number is kept in `servings`; the label would only freeze one language
        // into the database. In either language, and either way round.
        assertNull(RecipeParser.descriptiveYield("4 persons"))
        assertNull(RecipeParser.descriptiveYield("2 servings"))
        assertNull(RecipeParser.descriptiveYield("1 serving"))
        assertNull(RecipeParser.descriptiveYield("Serves 6"))
        assertNull(RecipeParser.descriptiveYield("4 porties"))
        assertNull(RecipeParser.descriptiveYield("2 personen"))
        assertNull(RecipeParser.descriptiveYield("voor 4"))
        // A bare number says nothing the count does not.
        assertNull(RecipeParser.descriptiveYield("4"))
        assertNull(RecipeParser.descriptiveYield(null))
        assertNull(RecipeParser.descriptiveYield("  "))
        // Anything more specific than a serving count carries information — keep it,
        // in the site's own words.
        assertEquals("15 stuks", RecipeParser.descriptiveYield("15 stuks"))
        assertEquals("1 loaf", RecipeParser.descriptiveYield("1 loaf"))
        assertEquals("24 koekjes", RecipeParser.descriptiveYield("24 koekjes"))
        assertEquals("2 jars", RecipeParser.descriptiveYield("2 jars"))
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
        // "Serves 6" is a plain count, so no wording is stored — the screen says it.
        assertNull(r.servingsLabel)
        assertEquals(5, r.steps.size)
        assertTrue(r.imageUrl!!.startsWith("https://"))
        assertTrue(r.tags.isNotEmpty())
        assertEquals(15, r.prepMinutes)
        assertEquals(60, r.cookMinutes)
        assertNull(r.videoUrl)
    }

    @Test
    fun `laurasbakery keeps the wp recipe maker ingredient groups`() {
        val r = fixture("laurasbakery", "https://www.laurasbakery.nl/appeltaart-cheesecake/")
        assertEquals("Appeltaart cheesecake", r.title)
        assertEquals(ParseSource.JSON_LD, r.source)
        assertEquals(15, r.ingredients.size)
        // The JSON-LD lists the lines flat; the headings only exist in the recipe card.
        assertEquals(
            listOf("Voor het deeg", "Voor de vulling"),
            r.ingredients.mapNotNull { it.section }.distinct(),
        )
        assertEquals(6, r.ingredients.count { it.section == "Voor het deeg" })
        assertEquals(9, r.ingredients.count { it.section == "Voor de vulling" })
        assertEquals("200 gram ongezouten roomboter", r.ingredients.first().text)
        assertEquals("Voor de vulling", r.ingredients.last().section)
        assertEquals("12 personen (22-24 cm vorm)", r.servingsLabel)
    }

    @Test
    fun `cookieandkate keeps the tasty recipes ingredient groups`() {
        val r = fixture(
            "cookieandkate",
            "https://cookieandkate.com/vegetarian-enchilada-casserole-recipe/",
        )
        assertEquals("Roasted Veggie Enchilada Casserole", r.title)
        assertEquals(14, r.ingredients.size)
        assertEquals("Roasted veggies", r.ingredients.first().section)
        assertEquals(2, r.ingredients.mapNotNull { it.section }.distinct().size)
        assertEquals(45, r.prepMinutes)
        assertEquals(60, r.cookMinutes)
        assertEquals(105, r.totalMinutes)
        // The page's own contentUrl has typographic quotes baked into it, so it would
        // never open. A link that cannot be followed is worse than no link.
        assertNull(r.videoUrl)
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
        assertEquals(listOf("50 g basilicum", "2 tenen knoflook", "100 ml olijfolie"), r.lines)
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
    fun `uitpaulineskeuken types its recipe into one paragraph`() {
        val r = fixture(
            "uitpaulineskeuken",
            "https://uitpaulineskeuken.nl/recept/recept-gevulde-portobello-met-spinazie",
        )
        assertEquals("Gevulde portobello met spinazie", r.title)
        assertEquals(ParseSource.HTML_HEURISTIC, r.source)
        // The JSON-LD carries a Recipe with no ingredients and no steps, the recipe
        // card is empty, and the list is typed into a <p> with <br> between the lines.
        assertEquals(8, r.ingredients.size)
        assertEquals("8 portobello’s", r.lines.first())
        assertEquals("90 gr amandelen (geroosterde)", r.lines.last())
        assertEquals(4, r.steps.size)
        assertTrue(r.steps.first().text.startsWith("Verwarm de oven"))
        assertEquals(ParseQuality.FULL, r.quality)
    }

    @Test
    fun `a class that denies the word is not a list of it`() {
        // wprm-no-ingredients sits on <body> and means the opposite; reading it as a
        // container handed back every <li> on the page.
        val html = """
            <html><head><title>Portobello - Blog</title></head>
            <body class="single wprm-no-ingredients">
              <nav><ul><li>Voorgerechten</li><li>Hoofdgerechten</li><li>Nagerechten</li></ul></nav>
              <p><strong>Ingrediënten</strong><br>2 portobello's<br>200 gr spinazie<br>90 gr feta</p>
            </body></html>
        """.trimIndent()
        val r = RecipeParser.parse(html, "https://blog.nl/portobello")
        assertEquals(listOf("2 portobello's", "200 gr spinazie", "90 gr feta"), r.lines)
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
        assertEquals(listOf("2 uien"), r.lines)
        assertEquals(1, r.steps.size)
    }

    @Test
    fun `json-ld that does publish its groups keeps them`() {
        // Not every plugin throws the headings away on the way into the JSON-LD.
        val html = """
            <html><body>
            <script type="application/ld+json">
            {"@type":"Recipe","name":"Test",
             "ingredientGroups":[
               {"name":"Voor de saus","ingredients":["2 el sojasaus","1 tl suiker"]},
               {"name":"Voor de rest","ingredients":["300 g noedels"]}],
             "recipeInstructions":[{"@type":"HowToStep","text":"Meng alles."}]}
            </script>
            </body></html>
        """.trimIndent()
        val r = RecipeParser.parse(html, "https://x.nl/test")
        assertEquals(listOf("2 el sojasaus", "1 tl suiker", "300 g noedels"), r.lines)
        assertEquals(
            listOf("Voor de saus", "Voor de saus", "Voor de rest"),
            r.ingredients.map { it.section },
        )
    }

    @Test
    fun `one heading over the whole list says nothing and is dropped`() {
        val html = """
            <html><body>
            <script type="application/ld+json">
            {"@type":"Recipe","name":"Test",
             "ingredientGroups":[{"name":"Ingrediënten","ingredients":["1 ui","2 tomaten"]}],
             "recipeInstructions":[{"@type":"HowToStep","text":"Snijden."}]}
            </script>
            </body></html>
        """.trimIndent()
        val r = RecipeParser.parse(html, "https://x.nl/test")
        assertEquals(listOf(null, null), r.ingredients.map { it.section })
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
