package nl.potat04.kookboek

import nl.potat04.kookboek.data.PaletteId
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The launcher icon follows the palette through one activity-alias per palette, exactly
 * one of them enabled. The failure modes are bad out of proportion to the feature:
 *
 *  - an alias missing for a palette → picking it leaves the app with no icon at all,
 *    and the only way back into the app is the app list in Settings;
 *  - no alias enabled in the manifest → a fresh install has no icon;
 *  - two enabled → the app appears twice on the home screen.
 *
 * None of that is visible in a build, and all of it is a manifest edit away, so it is
 * checked here rather than discovered on a phone.
 */
class LauncherIconTest {

    private val aliases: List<Element> = manifest().select("activity-alias")

    @Test
    fun `every palette has an alias with a launcher intent-filter`() {
        val declared = aliases.map { it.attr("android:name") }.toSet()
        val missing = PaletteId.entries
            .map { ".Launcher" + it.stored.replaceFirstChar(Char::uppercase) }
            .filterNot { it in declared }
        assertEquals("palettes without a launcher alias", emptyList<String>(), missing)

        aliases.forEach { alias ->
            val categories = alias.select("category").map { it.attr("android:name") }
            assertTrue(
                "${alias.attr("android:name")} is not a launcher entry",
                "android.intent.category.LAUNCHER" in categories,
            )
            assertEquals(
                "${alias.attr("android:name")} must point at MainActivity",
                ".MainActivity",
                alias.attr("android:targetActivity"),
            )
            assertEquals(
                "${alias.attr("android:name")} must be exported to be launchable",
                "true",
                alias.attr("android:exported"),
            )
        }
    }

    @Test
    fun `exactly one alias is enabled, and it is the default palette`() {
        val enabled = aliases.filter { it.attr("android:enabled") == "true" }
        assertEquals(
            "exactly one alias may ship enabled, found ${enabled.map { it.attr("android:name") }}",
            1,
            enabled.size,
        )
        val default = PaletteId.entries.first()
        assertEquals(
            "the enabled alias must match the palette a fresh install starts on",
            ".Launcher" + default.stored.replaceFirstChar(Char::uppercase),
            enabled.single().attr("android:name"),
        )
    }

    @Test
    fun `each palette gets its own icon and its own colour`() {
        val icons = aliases.map { it.attr("android:icon") }
        assertEquals("two palettes share an icon", icons.size, icons.toSet().size)

        val colours = File(res("values/colors.xml")).readText()
        PaletteId.entries.forEach {
            assertTrue(
                "no launcher_${it.stored} colour for the ${it.stored} icon",
                "\"launcher_${it.stored}\"" in colours,
            )
            val icon = File(res("mipmap-anydpi/ic_launcher_${it.stored}.xml"))
            assertTrue("missing ${icon.name}", icon.exists())
            assertTrue(
                "${icon.name} does not use @color/launcher_${it.stored}",
                "@color/launcher_${it.stored}" in icon.readText(),
            )
        }
    }

    /** MainActivity must not carry a launcher filter of its own, or the app shows twice. */
    @Test
    fun `MainActivity is not itself a launcher entry`() {
        val main = manifest().select("activity")
            .single { it.attr("android:name") == ".MainActivity" }
        val categories = main.select("category").map { it.attr("android:name") }
        assertTrue(
            "MainActivity still has a LAUNCHER filter alongside the aliases",
            "android.intent.category.LAUNCHER" !in categories,
        )
    }

    private fun manifest() =
        Jsoup.parse(File(src("AndroidManifest.xml")), "UTF-8", "", Parser.xmlParser())

    private fun res(path: String) = src("res/$path")

    private fun src(path: String) =
        listOf("src/main/$path", "app/src/main/$path")
            .firstOrNull { File(it).exists() }
            ?: error("cannot find src/main/$path from ${File("").absolutePath}")
}
