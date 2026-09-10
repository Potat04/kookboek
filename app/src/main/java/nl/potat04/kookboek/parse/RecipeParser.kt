package nl.potat04.kookboek.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Step
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
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
    /** Lines with the group heading they sat under, e.g. "Voor het deeg". */
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val servings: Int? = null,
    val servingsLabel: String? = null,
    val tags: List<String> = emptyList(),
    val videoUrl: String? = null,
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
        if (fromLd != null && fromLd.quality == ParseQuality.FULL) return fromLd.withGroupsFrom(doc)

        val fromMicro = microdata(doc, site)
        val best = listOfNotNull(fromLd, fromMicro).maxByOrNull { it.score() }
        if (best != null && best.quality == ParseQuality.FULL) return best.withGroupsFrom(doc)

        val fromHtml = heuristics(doc, url, site)
        return (listOfNotNull(best, fromHtml).maxByOrNull { it.score() } ?: fromHtml).withGroupsFrom(doc)
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

        val lines = mutableListOf<Ingredient>()
        // A plugin that keeps its groups puts them in a list of its own; the plain
        // property is the same lines with the headings thrown away.
        flattenIngredients(
            r["ingredientGroups"] ?: r["recipeIngredientGroups"]
                ?: r["recipeIngredient"] ?: r["ingredients"],
            null,
            lines,
        )

        val prep = duration(r.str("prepTime"))
        val cook = duration(r.str("cookTime"))
        val total = duration(r.str("totalTime"))
            ?: listOfNotNull(prep, cook).takeIf { it.isNotEmpty() }?.sum()

        return ParsedRecipe(
            title = clean(title),
            description = r.str("description")?.let { clean(stripHtml(it)) }?.takeIf { it.isNotBlank() },
            imageUrl = imageFrom(r["image"] ?: r["thumbnailUrl"]),
            author = personName(r["author"]),
            siteName = site,
            ingredients = dedupeSections(lines, Ingredient::section) { it.copy(section = null) },
            steps = dedupeSections(steps, Step::section) { it.copy(section = null) },
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total,
            servings = servingsCount(yieldEl),
            servingsLabel = servingsText(yieldEl),
            tags = tagsFrom(r),
            videoUrl = videoFrom(r["video"]),
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

    /**
     * The video the page published with the recipe, when the link is usable.
     *
     * `contentUrl` is the file, `embedUrl` the player; either one opens. Sites do get
     * this wrong — cookieandkate.com publishes a contentUrl with typographic quotes
     * baked into it — and a link that cannot open is worse than no link at all.
     */
    private fun videoFrom(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> primitiveOf(e)?.let(::usableLink)
        is JsonArray -> e.firstNotNullOfOrNull { videoFrom(it) }
        is JsonObject -> (primitiveOf(e["contentUrl"]) ?: primitiveOf(e["embedUrl"]) ?: primitiveOf(e["url"]))
            ?.let(::usableLink)
        else -> null
    }

    private fun usableLink(raw: String): String? = clean(raw)
        .takeIf { url -> url.startsWith("http") && url.none { it.isWhitespace() || it in QUOTES } }

    /**
     * Ingredients, keeping the heading of the group they sat under.
     *
     * Mirrors [flattenInstructions], because the shapes are the same mess: a list of
     * strings, a list of objects, or groups with their lines nested inside them.
     */
    private fun flattenIngredients(
        e: JsonElement?,
        section: String?,
        out: MutableList<Ingredient>,
        depth: Int = 0,
    ) {
        if (e == null || depth > 6) return
        when (e) {
            is JsonPrimitive -> primitiveOf(e)?.let { out += Ingredient(clean(it), section) }
            is JsonArray -> e.forEach { flattenIngredients(it, section, out, depth + 1) }
            is JsonObject -> {
                val children = e["ingredients"] ?: e["recipeIngredient"] ?: e["itemListElement"]
                if (children != null) {
                    val name = primitiveOf(e["name"] ?: e["title"])
                        ?.let { clean(it) }?.takeIf { it.length in 1..60 }
                    flattenIngredients(children, name ?: section, out, depth + 1)
                } else {
                    primitiveOf(e["name"] ?: e["text"])?.let { out += Ingredient(clean(it), section) }
                }
            }
            else -> Unit
        }
        if (depth == 0) out.removeAll { it.text.isBlank() }
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

    /** If every line carries the same section name it carries no information — drop it. */
    private fun <T> dedupeSections(items: List<T>, section: (T) -> String?, without: (T) -> T): List<T> {
        val names = items.mapNotNull(section).distinct()
        val allSame = names.size <= 1 && items.all { section(it) != null }
        return if (allSame) items.map(without) else items
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

        fun minutes(name: String): Int? {
            val el = prop(name) ?: return null
            val raw = el.attr("datetime").takeIf { it.isNotBlank() }
                ?: el.attr("content").takeIf { it.isNotBlank() }
                ?: el.text()
            return duration(raw) ?: minutesFromText(raw)
        }

        val prep = minutes("prepTime")
        val cook = minutes("cookTime")
        val yieldTxt = propText("recipeYield")

        return ParsedRecipe(
            title = title,
            description = propText("description"),
            imageUrl = prop("image")?.let { absUrl(it) },
            author = propText("author"),
            siteName = site,
            ingredients = ingredients.map { Ingredient(it) },
            steps = steps.filter { it.isNotBlank() }.map { Step(it) },
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = minutes("totalTime")
                ?: listOfNotNull(prep, cook).takeIf { it.isNotEmpty() }?.sum(),
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

        val ingredients = groupedIngredients(doc).ifEmpty {
            pluginList(doc, PLUGIN_INGREDIENTS)
                .ifEmpty { looseList(doc, LOOSE_INGREDIENTS) }
                .ifEmpty { listAfterHeading(doc, INGREDIENT_WORDS) }
                .map { Ingredient(it) }
        }
        val steps = pluginList(doc, PLUGIN_STEPS)
            .ifEmpty { looseList(doc, LOOSE_STEPS) }
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

    /**
     * The ingredient list as the recipe plugin laid it out, headings included.
     *
     * WP Recipe Maker wraps every group in its own div with an `h4` on top; Tasty
     * Recipes writes one body with an `h4` before each `ul`. Neither puts the headings
     * in its JSON-LD, so this is the only place "Voor het deeg" exists on the page.
     */
    private fun groupedIngredients(doc: Document): List<Ingredient> {
        val out = mutableListOf<Ingredient>()
        for (group in doc.select(".wprm-recipe-ingredient-group")) {
            val name = group.selectFirst(".wprm-recipe-group-name, h3, h4, h5")?.let { heading(it) }
            lineTexts(group.select(".wprm-recipe-ingredient")).forEach { out += Ingredient(it, name) }
        }
        if (out.isEmpty()) {
            for (body in doc.select(".tasty-recipes-ingredients-body, .tasty-recipes-ingredients")) {
                var name: String? = null
                for (el in body.select("h3, h4, h5, ul, ol")) {
                    if (el.normalName().startsWith("h")) {
                        name = heading(el)
                        continue
                    }
                    // A nested list is part of its parent item, not a group of its own.
                    if (el.parent()?.normalName() == "li") continue
                    lineTexts(el.select("> li")).forEach { out += Ingredient(it, name) }
                }
                if (out.isNotEmpty()) break
            }
        }
        // Without a heading this is only the flat list the other selectors already read.
        return if (out.size >= 2 && out.any { it.section != null }) out else emptyList()
    }

    /**
     * Lifts group headings off the page onto a list that came in flat.
     *
     * The structured data wins on everything else, but it drops the groups, so when the
     * plugin markup lists exactly the same number of lines in the same order the
     * headings can be laid over it. Different counts mean the two are not describing
     * the same list, and then nothing is added.
     */
    private fun ParsedRecipe.withGroupsFrom(doc: Document): ParsedRecipe {
        if (ingredients.isEmpty() || ingredients.any { it.section != null }) return this
        val grouped = groupedIngredients(doc)
        if (grouped.size != ingredients.size) return this
        return copy(ingredients = ingredients.mapIndexed { i, line -> line.copy(section = grouped[i].section) })
    }

    private fun heading(el: Element): String? =
        clean(el.text()).takeIf { it.isNotBlank() && it.length <= 60 }

    private val PLUGIN_INGREDIENTS = listOf(
        ".wprm-recipe-ingredient",
        ".tasty-recipes-ingredients li",
        ".mv-create-ingredients li",
        ".recipe-ingredients li",
        ".ingredients-list li",
    )
    private val PLUGIN_STEPS = listOf(
        ".wprm-recipe-instruction-text",
        ".tasty-recipes-instructions li",
        ".mv-create-instructions li",
        ".recipe-instructions li",
    )

    /** Words a container may name itself after when no plugin markup was found. */
    private val LOOSE_INGREDIENTS = listOf("ingredient")
    private val LOOSE_STEPS = listOf("instruction", "direction")

    /** The menu, the header and the footer are the site, not the recipe. */
    private const val FURNITURE = "nav, header, footer, aside, [role=navigation], [class*=menu]"

    /** No recipe lists this many things; past it we are reading the whole page. */
    private const val MAX_ITEMS = 80

    private fun pluginList(doc: Document, selectors: List<String>): List<String> {
        for (sel in selectors) {
            val items = runCatching { doc.select(sel) }.getOrNull() ?: continue
            val texts = itemTexts(items)
            if (texts.size >= 2) return texts
        }
        return emptyList()
    }

    /**
     * Last resort: a container that names itself after what we are looking for.
     *
     * This needs guarding, because a class name is not a promise. uitpaulineskeuken.nl
     * puts `wprm-no-ingredients` on its `<body>` to say the recipe card is empty, and
     * a bare `[class*=ingredient] li` read that as "here are the ingredients" and
     * handed back all 216 links in the site's menu.
     */
    private fun looseList(doc: Document, words: List<String>): List<String> {
        for (word in words) {
            for (container in doc.select("[class*=$word]")) {
                if (!container.namesItself(word)) continue
                val texts = itemTexts(container.select("li"))
                if (texts.size >= 2) return texts
            }
        }
        return emptyList()
    }

    /** True when a class on this element claims the word, and does not deny it. */
    private fun Element.namesItself(word: String): Boolean {
        if (normalName() == "body" || normalName() == "html") return false
        return classNames().any {
            val c = it.lowercase()
            c.contains(word) && !c.contains("no-$word") && !c.contains("without-$word")
        }
    }

    private fun itemTexts(items: List<Element>): List<String> {
        val texts = lineTexts(items).distinct()
        return if (texts.size > MAX_ITEMS) emptyList() else texts
    }

    /**
     * The same lines without the de-duplication, for a list read group by group.
     * A recipe may well ask for vanilla sugar twice, once per group, and dropping the
     * second one would put every heading after it on the wrong line.
     */
    private fun lineTexts(items: List<Element>): List<String> =
        items.filter { it.closest(FURNITURE) == null }
            .map { clean(it.text()) }
            .filter { it.isNotBlank() && it.length < 400 }

    /** Finds a heading whose text mentions one of [words] and returns the list that follows it. */
    private fun listAfterHeading(doc: Document, words: List<String>): List<String> {
        val headings = doc.select("h1, h2, h3, h4, h5, strong, b, legend, caption, dt")
        for (h in headings) {
            val t = h.text().lowercase()
            if (t.length > 60 || words.none { t.contains(it) }) continue
            if (h.closest(FURNITURE) != null) continue
            val inline = inlineLinesAfter(h)
            if (inline.size >= 2) return inline
            // One line behind the heading is the first item; the rest follows in the
            // paragraphs after it, which is how these blogs write their steps.
            val combined = inline + listFollowing(h).orEmpty()
            if (combined.size >= 2) return combined
        }
        return emptyList()
    }

    /**
     * Reads the lines that sit behind the heading in its own paragraph.
     *
     * Blogs older than the recipe plugins type the whole list into one `<p>`: the
     * heading in bold, then a line per `<br>`. Those lines are text nodes, so
     * [listFollowing], which walks over sibling *elements*, never sees them.
     */
    private fun inlineLinesAfter(heading: Element): List<String> {
        val parent = heading.parent() ?: return emptyList()
        if (parent.select("br").isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        val line = StringBuilder()
        var passedHeading = false
        for (node in parent.childNodes()) {
            if (node === heading) {
                passedHeading = true
                continue
            }
            if (!passedHeading) continue
            when {
                node is Element && node.normalName() == "br" -> {
                    lines += line.toString()
                    line.setLength(0)
                }
                node is Element -> line.append(node.text())
                node is TextNode -> line.append(node.text())
            }
        }
        lines += line.toString()
        return lines.map(::clean).filter { it.isNotBlank() && it.length < 400 }
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

    /** Typographic quotes in a link mean the site pasted its editor's output into the markup. */
    private val QUOTES = setOf('"', '\'', '‘', '’', '“', '”', '″')

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
