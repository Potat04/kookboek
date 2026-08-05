package nl.potat04.kookboek

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the two languages in step.
 *
 * A mismatch here is invisible in the build and shows up on somebody's phone: a missing
 * key silently falls back to the other language, and a placeholder that differs between
 * the two throws an IllegalFormatException the moment that screen opens. Neither the
 * compiler nor the app can catch it, so this does.
 *
 * English is the unqualified default — the fallback for any language the app does not
 * have — and Dutch is the translation. See .claude/knowledge/localization.md.
 */
class StringResourcesTest {

    private val english = parse("values")
    private val dutch = parse("values-nl")

    @Test
    fun `both languages declare exactly the same keys`() {
        assertEquals(
            "keys only in English (values/) — add the Dutch, or Dutch readers get English",
            emptyList<String>(),
            (english.keys - dutch.keys).sorted(),
        )
        assertEquals(
            "keys only in Dutch (values-nl/) — values/ is the fallback, so these are missing " +
                "for everyone whose phone is not set to Dutch",
            emptyList<String>(),
            (dutch.keys - english.keys).sorted(),
        )
    }

    @Test
    fun `a key is a string in both languages or a plural in both`() {
        val differing = english.keys
            .filter { english.getValue(it).isPlural != dutch.getValue(it).isPlural }
            .sorted()
        assertEquals("string in one language and plurals in the other", emptyList<String>(), differing)
    }

    @Test
    fun `plurals declare the same quantities`() {
        english.filterValues { it.isPlural }.forEach { (key, en) ->
            assertEquals(
                "quantities differ for $key",
                en.byQuantity.keys.sorted(),
                dutch.getValue(key).byQuantity.keys.sorted(),
            )
        }
    }

    /** The one that would actually crash the app rather than merely read oddly. */
    @Test
    fun `format placeholders match between the languages`() {
        english.forEach { (key, en) ->
            val nl = dutch.getValue(key)
            en.byQuantity.forEach { (quantity, enText) ->
                val nlText = nl.byQuantity[quantity].orEmpty()
                assertEquals(
                    "placeholders differ for $key${if (quantity.isEmpty()) "" else "[$quantity]"}: " +
                        "en=\"$enText\" nl=\"$nlText\"",
                    placeholders(enText),
                    placeholders(nlText),
                )
            }
        }
    }

    @Test
    fun `dutch is a translation and not a copy`() {
        // Guards against someone copying values/ into values-nl to silence the tests
        // above. A handful of entries are legitimately identical — proper nouns, the
        // https:// hint, endonyms — but most of the file has to differ.
        val identical = english.count { (key, en) -> en.byQuantity == dutch.getValue(key).byQuantity }
        assertTrue(
            "$identical of ${english.size} entries are byte-identical to the English",
            identical < english.size / 3,
        )
    }

    // ------------------------------------------------------------------ helpers

    private data class Entry(val isPlural: Boolean, val byQuantity: Map<String, String>)

    private fun parse(folder: String): Map<String, Entry> {
        val file = resFile("$folder/strings.xml")
        val doc = Jsoup.parse(file, "UTF-8", "", Parser.xmlParser())
        val out = mutableMapOf<String, Entry>()
        doc.select("string").forEach {
            out[it.attr("name")] = Entry(isPlural = false, byQuantity = mapOf("" to it.text()))
        }
        doc.select("plurals").forEach { plural ->
            out[plural.attr("name")] = Entry(
                isPlural = true,
                byQuantity = plural.select("item").associate { it.attr("quantity") to it.text() },
            )
        }
        assertTrue("no strings found in res/$folder", out.isNotEmpty())
        return out
    }

    /** Unit tests run from the module directory, but do not depend on it. */
    private fun resFile(path: String): File =
        listOf("src/main/res/$path", "app/src/main/res/$path")
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("cannot find res/$path from ${File("").absolutePath}")

    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    private companion object {
        val PLACEHOLDER = Regex("%(\\d+\\$)?[sd]")
    }
}
