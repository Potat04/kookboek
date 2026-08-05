package nl.potat04.kookboek.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Step
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI

/** Where the recipe data came from — useful for debugging and for telling the user what happened. */
enum class ParseSource { JSON_LD, MICRODATA, HTML_HEURISTIC, LINK_ONLY }

data class ParsedRecipe(
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val author: String? = null,
    val siteName: String? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<Step> = emptyList(),
    val totalMinutes: Int? = null,
    val servings: Int? = null,
    val servingsLabel: String? = null,
    val tags: List<String> = emptyList(),
    val source: ParseSource = ParseSource.LINK_ONLY,
) {
    val quality: ParseQuality
        get() = when {
            ingredients.isNotEmpty() && steps.isNotEmpty() -> ParseQuality.FULL
            ingredients.isNotEmpty() || steps.isNotEmpty() -> ParseQuality.PARTIAL
            else -> ParseQuality.LINK_ONLY
        }
}

/**
 * Reads a recipe off an arbitrary web page.
 *
 * Tries, in order of trustworthiness:
 *  1. schema.org/Recipe as JSON-LD  (what most recipe sites and food blogs publish)
 *  2. the same schema expressed as microdata attributes
 *  3. known recipe-plugin markup (WP Recipe Maker, Tasty Recipes, Mediavine Create)
 *  4. plain HTML heuristics: head tags for the title/image, headings like
 *     "Ingrediënten" / "Bereiding" for the lists
 *
 * Whatever happens it returns something — worst case just a title and the link.
 */
object RecipeParser {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(html: String, url: String): ParsedRecipe = parse(Jsoup.parse(html, url), url)

    fun parse(doc: Document, url: String): ParsedRecipe {
        val site = siteName(doc, url)
        val fromLd = jsonLd(doc)?.let { fromSchema(it, site) }
        if (fromLd != null && fromLd.quality == ParseQuality.FULL) return fromLd

        val fromMicro = microdata(doc, site)
        val best = listOfNotNull(fromLd, fromMicro).maxByOrNull { it.score() }
        if (best != null && best.quality == ParseQuality.FULL) return best

        val fromHtml = heuristics(doc, url, site)
        return listOfNotNull(best, fromHtml).maxByOrNull { it.score() } ?: fromHtml
    }

    private fun ParsedRecipe.score(): Int =
        ingredients.size + steps.size * 2 + (if (title.isNotBlank()) 1 else 0)

    // ---------------------------------------------------------------- JSON-LD

    /** Finds the first object typed as a Recipe anywhere in any ld+json block (incl. @graph). */
    private fun jsonLd(doc: Document): JsonObject? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val raw = script.data().trim().ifEmpty { script.html().trim() }
            if (raw.isEmpty()) continue
            val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: continue
            val hits = mutableListOf<JsonObject>()
            collectRecipes(root, hits)
            // Prefer the object that actually carries ingredients.
            hits.firstOrNull { it["recipeIngredient"] != null || it["ingredients"] != null }?.let { return it }
            hits.firstOrNull()?.let { return it }
        }
        return null
    }

    private fun collectRecipes(node: JsonElement, out: MutableList<JsonObject>, depth: Int = 0) {
        if (depth > 12) return
        when (node) {
            is JsonArray -> node.forEach { collectRecipes(it, out, depth + 1) }
            is JsonObject -> {
                if (typesOf(node["@type"]).any { it.equals("Recipe", true) }) out += node
                node.values.forEach { collectRecipes(it, out, depth + 1) }
            }
            else -> Unit
        }
    }

    private fun typesOf(e: JsonElement?): List<String> = when (e) {
        is JsonPrimitive -> listOf(e.content)
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.content }
        else -> emptyList()
    }

    private fun fromSchema(r: JsonObject, site: String?): ParsedRecipe {
        val title = r.str("name") ?: r.str("headline") ?: return ParsedRecipe("", source = ParseSource.JSON_LD)
        val yieldEl = r["recipeYield"] ?: r["yield"]
        val steps = mutableListOf<Step>()
        flattenInstructions(r["recipeInstructions"], null, steps)

        val time = duration(r.str("totalTime"))
            ?: listOfNotNull(duration(r.str("prepTime")), duration(r.str("cookTime")))
                .takeIf { it.isNotEmpty() }?.sum()

        return ParsedRecipe(
            title = clean(title),
            description = r.str("description")?.let { clean(stripHtml(it)) }?.takeIf { it.isNotBlank() },
            imageUrl = imageFrom(r["image"] ?: r["thumbnailUrl"]),
            author = personName(r["author"]),
            siteName = site,
            ingredients = stringsFrom(r["recipeIngredient"] ?: r["ingredients"]).map { clean(it) }
                .filter { it.isNotBlank() },
            steps = dedupeSections(steps),
            totalMinutes = time,
            servings = servingsCount(yieldEl),
            servingsLabel = servingsText(yieldEl),
            tags = tagsFrom(r),
            source = ParseSource.JSON_LD,
        )
    }

    private fun tagsFrom(r: JsonObject): List<String> {
        val raw = buildList {
            addAll(stringsFrom(r["keywords"]))
            addAll(stringsFrom(r["recipeCategory"]))
            addAll(stringsFrom(r["recipeCuisine"]))
        }
        return raw.flatMap { it.split(",") }
            .map { clean(it) }
            .filter { it.isNotBlank() && it.length <= 30 }
            .distinctBy { it.lowercase() }
            .take(6)
    }

    private fun JsonObject.str(key: String): String? = primitiveOf(this[key])

    private fun primitiveOf(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.content.takeIf { it.isNotBlank() && it != "null" }
        is JsonArray -> e.firstNotNullOfOrNull { primitiveOf(it) }
        is JsonObject -> primitiveOf(e["@value"]) ?: primitiveOf(e["name"]) ?: primitiveOf(e["text"])
        else -> null
    }

    private fun stringsFrom(e: JsonElement?): List<String> = when (e) {
        is JsonPrimitive -> listOf(e.content)
        is JsonArray -> e.flatMap { stringsFrom(it) }
        is JsonObject -> listOfNotNull(primitiveOf(e["name"]) ?: primitiveOf(e["text"]))
        else -> emptyList()
    }

    private fun personName(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.content.takeIf { it.isNotBlank() }
        is JsonArray -> e.firstNotNullOfOrNull { personName(it) }
        is JsonObject -> primitiveOf(e["name"])
        else -> null
    }?.let { clean(it) }

    /** image can be a string, a list, an ImageObject, or a list of those. Prefer the widest. */
    private fun imageFrom(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.content.takeIf { it.startsWith("http") }
        is JsonArray -> e.mapNotNull { imageFrom(it) }.firstOrNull()
        is JsonObject -> {
            val urls = e["url"] ?: e["contentUrl"]
            imageFrom(urls)
        }
        else -> null
    }

    private fun flattenInstructions(
        e: JsonElement?,
        section: String?,
        out: MutableList<Step>,
        depth: Int = 0,
    ) {
        if (e == null || depth > 6) return
        when (e) {
            is JsonPrimitive -> splitStepText(e.content).forEach { out += Step(it, section) }
            is JsonArray -> e.forEach { flattenInstructions(it, section, out, depth + 1) }
            is JsonObject -> {
                val type = typesOf(e["@type"]).firstOrNull().orEmpty()
                val children = e["itemListElement"] ?: e["steps"]
                when {
                    type.equals("HowToSection", true) || (children != null && type.contains("List", true)) -> {
                        val name = primitiveOf(e["name"])?.let { clean(it) }?.takeIf { it.length in 1..60 }
                        flattenInstructions(children, name ?: section, out, depth + 1)
                    }
                    children != null -> flattenInstructions(children, section, out, depth + 1)
                    else -> {
                        // HowToStep: "text" is the real instruction; some sites duplicate it into "name".
                        val text = primitiveOf(e["text"]) ?: primitiveOf(e["name"])
                        text?.let { splitStepText(it).forEach { s -> out += Step(s, section) } }
                    }
                }
            }
        }
    }

    /** Instruction blobs are sometimes one string holding HTML or several newline separated steps. */
    private fun splitStepText(raw: String): List<String> {
        val text = if (raw.contains('<') && raw.contains('>')) {
            val frag = Jsoup.parseBodyFragment(raw)
            val items = frag.select("li").map { it.text() }
                .ifEmpty { frag.select("p").map { it.text() } }
            if (items.size > 1) return items.map { clean(it) }.filter { it.isNotBlank() }.map(::stripStepNumber)
            stripHtml(raw)
        } else raw

        val parts = text.split(Regex("\\r?\\n+"))
            .map { clean(it) }
            .filter { it.isNotBlank() }
        val useful = parts.filter { it.length > 1 }
        return (if (useful.isEmpty()) listOf(clean(text)) else useful)
            .filter { it.isNotBlank() }
            .map(::stripStepNumber)
    }

    /** Steps are numbered in the UI, so drop a leading "1." / "Stap 2:" the site baked in. */
    private fun stripStepNumber(s: String): String = s
        .replace(Regex("^\\s*(?:stap|step)\\s*\\d{1,2}\\s*[:.)-]?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\s*\\d{1,2}\\s*[.)]\\s+"), "")
        .trim()

    /** If every step carries the same section name it carries no information — drop it. */
    private fun dedupeSections(steps: List<Step>): List<Step> {
        val sections = steps.mapNotNull { it.section }.distinct()
        val allSame = sections.size <= 1 && steps.all { it.section != null }
        return if (allSame) steps.map { it.copy(section = null) } else steps
    }

    // ------------------------------------------------------------- microdata

    private fun microdata(doc: Document, site: String?): ParsedRecipe? {
        val scope = doc.selectFirst("[itemtype~=(?i)schema.org/Recipe]") ?: return null
        fun prop(name: String): Element? = scope.selectFirst("[itemprop=$name]")
        fun propText(name: String): String? = prop(name)?.let { el ->
            (el.attr("content").takeIf { it.isNotBlank() } ?: el.text()).let(::clean)
        }?.takeIf { it.isNotBlank() }

        val title = propText("name") ?: return null
        val ingredients = scope.select("[itemprop=recipeIngredient], [itemprop=ingredients]")
            .map { clean(it.text()) }.filter { it.isNotBlank() }

        val instrEl = scope.select("[itemprop=recipeInstructions]")
        val steps = mutableListOf<String>()
        for (el in instrEl) {
            val items = el.select("li")
            if (items.isNotEmpty()) items.forEach { steps += clean(it.text()) }
            else splitStepText(el.wholeText().ifBlank { el.text() }).forEach { steps += it }
        }

        val timeEl = prop("totalTime") ?: prop("cookTime")
        val timeRaw = timeEl?.attr("datetime")?.takeIf { it.isNotBlank() }
            ?: timeEl?.attr("content")?.takeIf { it.isNotBlank() }
            ?: timeEl?.text()
        val yieldTxt = propText("recipeYield")

        return ParsedRecipe(
            title = title,
            description = propText("description"),
            imageUrl = prop("image")?.let { absUrl(it) },
            author = propText("author"),
            siteName = site,
            ingredients = ingredients,
            steps = steps.filter { it.isNotBlank() }.map { Step(it) },
            totalMinutes = duration(timeRaw) ?: minutesFromText(timeRaw),
            servings = yieldTxt?.let { firstInt(it) },
            servingsLabel = descriptiveYield(yieldTxt),
            source = ParseSource.MICRODATA,
        )
    }

    private fun absUrl(el: Element): String? {
        val raw = listOf("src", "content", "href", "data-src")
            .firstNotNullOfOrNull { el.attr("abs:$it").takeIf { v -> v.isNotBlank() } }
        return raw?.takeIf { it.startsWith("http") }
    }

    // ------------------------------------------------------------ heuristics

    private val INGREDIENT_WORDS = listOf(
        "ingredi", "benodigdheden", "boodschappen", "nodig", "what you need", "shopping",
    )
    private val STEP_WORDS = listOf(
        "bereiding", "bereidingswijze", "instructie", "stappen", "aan de slag", "zo maak je",
        "werkwijze", "instructions", "directions", "method", "preparation", "steps",
    )

    private fun heuristics(doc: Document, url: String, site: String?): ParsedRecipe {
        val title = meta(doc, "og:title")
            ?: meta(doc, "twitter:title")
            ?: doc.selectFirst("h1")?.text()?.let(::clean)?.takeIf { it.isNotBlank() }
            ?: doc.title().let(::clean).let(::stripSiteSuffix)
            ?: urlTitle(url)

        val ingredients = pluginList(doc, PLUGIN_INGREDIENTS)
            .ifEmpty { listAfterHeading(doc, INGREDIENT_WORDS) }
        val steps = pluginList(doc, PLUGIN_STEPS)
            .ifEmpty { listAfterHeading(doc, STEP_WORDS) }

        return ParsedRecipe(
            title = title.ifBlank { urlTitle(url) },
            description = meta(doc, "og:description") ?: meta(doc, "description"),
            imageUrl = meta(doc, "og:image") ?: meta(doc, "twitter:image") ?: biggestImage(doc),
            author = meta(doc, "author") ?: meta(doc, "article:author"),
            siteName = site,
            ingredients = ingredients,
            steps = steps.map { Step(it) },
            totalMinutes = null,
            source = if (ingredients.isEmpty() && steps.isEmpty()) ParseSource.LINK_ONLY
            else ParseSource.HTML_HEURISTIC,
        )
    }

    private val PLUGIN_INGREDIENTS = listOf(
        ".wprm-recipe-ingredient",
        ".tasty-recipes-ingredients li",
        ".mv-create-ingredients li",
        ".recipe-ingredients li",
        ".ingredients-list li",
        "[class*=ingredient] li",
    )
    private val PLUGIN_STEPS = listOf(
        ".wprm-recipe-instruction-text",
        ".tasty-recipes-instructions li",
        ".mv-create-instructions li",
        ".recipe-instructions li",
        "[class*=instruction] li",
        "[class*=direction] li",
    )

    private fun pluginList(doc: Document, selectors: List<String>): List<String> {
        for (sel in selectors) {
            val items = runCatching { doc.select(sel) }.getOrNull() ?: continue
            val texts = items.map { clean(it.text()) }.filter { it.isNotBlank() && it.length < 400 }
                .distinct()
            if (texts.size >= 2) return texts
        }
        return emptyList()
    }

    /** Finds a heading whose text mentions one of [words] and returns the list that follows it. */
    private fun listAfterHeading(doc: Document, words: List<String>): List<String> {
        val headings = doc.select("h1, h2, h3, h4, h5, strong, b, legend, caption, dt")
        for (h in headings) {
            val t = h.text().lowercase()
            if (t.length > 60 || words.none { t.contains(it) }) continue
            listFollowing(h)?.let { if (it.size >= 2) return it }
        }
        return emptyList()
    }

    private fun listFollowing(heading: Element): List<String>? {
        var anchor: Element? = heading
        var levels = 0
        while (anchor != null && levels < 4) {
            var sib = anchor.nextElementSibling()
            var hops = 0
            val paragraphs = mutableListOf<String>()
            while (sib != null && hops < 10) {
                val list = if (sib.normalName() in setOf("ul", "ol")) sib else sib.selectFirst("ul, ol")
                if (list != null) {
                    val items = list.select("> li").ifEmpty { list.select("li") }
                        .map { clean(it.text()) }.filter { it.isNotBlank() && it.length < 400 }
                    if (items.size >= 2) return items
                }
                if (sib.normalName() == "p") clean(sib.text()).takeIf { it.length > 20 }?.let { paragraphs += it }
                if (sib.normalName().matches(Regex("h[1-4]")) && paragraphs.isNotEmpty()) break
                sib = sib.nextElementSibling()
                hops++
            }
            if (paragraphs.size >= 2) return paragraphs
            anchor = anchor.parent()
            levels++
        }
        return null
    }

    private fun biggestImage(doc: Document): String? = doc.select("img")
        .mapNotNull { img ->
            val src = img.attr("abs:src").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val w = img.attr("width").toIntOrNull() ?: 0
            val h = img.attr("height").toIntOrNull() ?: 0
            src to (w * h)
        }
        .maxByOrNull { it.second }
        ?.first

    private fun meta(doc: Document, name: String): String? {
        val el = doc.selectFirst("meta[property=$name]") ?: doc.selectFirst("meta[name=$name]")
        return el?.attr("content")?.let(::clean)?.takeIf { it.isNotBlank() }
    }

    // ----------------------------------------------------------------- utils

    private fun siteName(doc: Document, url: String): String? =
        meta(doc, "og:site_name") ?: host(url)

    fun host(url: String): String? = runCatching {
        URI(url).host?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun urlTitle(url: String): String {
        val slug = runCatching { URI(url).path }.getOrNull().orEmpty()
            .trimEnd('/').substringAfterLast('/')
            .substringBeforeLast('.')
        val words = slug.replace('-', ' ').replace('_', ' ').trim()
        // No Dutch fallback here: a title with nothing left to guess from stays blank,
        // and the screen fills in "Naamloos recept" in the language that is set.
        return if (words.isBlank()) host(url).orEmpty()
        else words.replaceFirstChar { it.uppercase() }
    }

    private fun stripSiteSuffix(title: String): String {
        for (sep in listOf(" | ", " - ", " — ", " · ", " » ")) {
            val idx = title.lastIndexOf(sep)
            if (idx > title.length / 2) return title.substring(0, idx).trim()
        }
        return title
    }

    private fun stripHtml(s: String): String =
        if (s.contains('<')) Jsoup.parseBodyFragment(s).text() else s

    private fun clean(s: String): String =
        Parser.unescapeEntities(s, false)
            .replace(Regex("[\\u00A0\\u200B\\uFEFF]"), " ")
            .replace(Regex("[ \\t]+"), " ")
            .trim()

    /** ISO-8601 durations as used by schema.org: PT15M, PT1H15M, P0DT0H30M. */
    fun duration(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val m = Regex(
            "^P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:([\\d.]+)S)?)?$",
            RegexOption.IGNORE_CASE,
        ).find(raw.trim()) ?: return null
        val (w, d, h, min) = listOf(1, 2, 3, 4).map { m.groupValues[it].toIntOrNull() ?: 0 }
        val total = w * 7 * 24 * 60 + d * 24 * 60 + h * 60 + min
        return total.takeIf { it > 0 }
    }

    /** Fallback for sites that write "35 minuten" instead of a proper duration. */
    private fun minutesFromText(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val hours = Regex("(\\d+)\\s*(?:uur|u\\b|hours?|hrs?|h\\b)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val mins = Regex("(\\d+)\\s*(?:minuten|minuut|mins?|minutes?|m\\b)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + mins
        return total.takeIf { it > 0 } ?: firstInt(raw)?.takeIf { it in 1..600 }
    }

    private fun firstInt(s: String): Int? =
        Regex("\\d+").find(s)?.value?.toIntOrNull()?.takeIf { it in 1..999 }

    private fun servingsCount(e: JsonElement?): Int? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { servingsCount(it) }
        else -> primitiveOf(e)?.let { firstInt(it) }
    }

    private fun servingsText(e: JsonElement?): String? =
        stringsFrom(e).firstNotNullOfOrNull { descriptiveYield(it) }

    /**
     * The site's own words for the yield, but only when they say more than a number.
     *
     * "15 stuks", "1 loaf" and "24 koekjes" are kept: they tell you something a count
     * cannot, and they are what the page actually promised. A plain serving count is
     * dropped in either language — the number is already in [ParsedRecipe.servings],
     * and the screen words it in whichever language is set.
     *
     * This used to rewrite "4 servings" into "4 porties", back when the app only spoke
     * Dutch. That froze one language into the database for as long as the recipe was
     * kept, which is exactly the wrong place for it.
     */
    fun descriptiveYield(label: String?): String? {
        val text = label?.let(::clean).orEmpty()
        if (text.isBlank()) return null
        // A bare "4" carries nothing the count does not.
        if (text.none(Char::isLetter)) return null
        val portion = "servings?|serves|persons?|people|portions?|porties|portie|personen|persoon"
        if (Regex("^\\d+\\s*($portion)$", RegexOption.IGNORE_CASE).matches(text)) return null
        if (Regex("^(?:serves|for|voor)\\s*\\d+$", RegexOption.IGNORE_CASE).matches(text)) return null
        return text
    }
}
