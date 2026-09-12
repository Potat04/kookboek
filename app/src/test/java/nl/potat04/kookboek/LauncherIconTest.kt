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
            // Not MainActivity: a task rooted at an alias is destroyed when that alias is
            // disabled, and that task would be the reader's own session. See LauncherRouter.
            assertEquals(
                "${alias.attr("android:name")} must point at LauncherRouter, not MainActivity",
                ".LauncherRouter",
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

    /**
     * The whole point of the router is that it gets a throwaway task of its own. Lose any
     * one of these three attributes and the reader's session ends up back on the alias,
     * where changing the palette destroys it.
     */
    @Test
    fun `the router stays out of the way`() {
        val router = manifest().select("activity")
            .single { it.attr("android:name") == ".LauncherRouter" }
        assertEquals(
            "the router needs its own task, or MainActivity stays rooted at the alias",
            "",
            router.attr("android:taskAffinity"),
        )
        assertTrue(
            "taskAffinity must be declared as empty, not left out",
            router.hasAttr("android:taskAffinity"),
        )
        assertEquals("the router's task must not show up in Recents", "true", router.attr("android:excludeFromRecents"))
        assertEquals("the router must be launchable through the aliases", "true", router.attr("android:exported"))
    }

    /**
     * The app shortcuts hang off the launcher entries, which are the aliases.
     *
     * Android reads `android.app.shortcuts` from the component that answers
     * MAIN/LAUNCHER, and that is the enabled alias, never [LauncherRouter] behind it,
     * which has no filter of its own. Put the meta-data on the router and a long press
     * on the icon offers nothing, with nothing to see in a build or a log. Because the
     * alias in play changes with the palette, all six have to carry it.
     */
    @Test
    fun `every launcher entry declares the app shortcuts`() {
        aliases.forEach { alias ->
            val declared = alias.select("meta-data")
                .filter { it.attr("android:name") == "android.app.shortcuts" }
                .map { it.attr("android:resource") }
            assertEquals(
                "${alias.attr("android:name")} does not point at @xml/shortcuts, so a long " +
                    "press on that palette's icon would offer nothing",
                listOf("@xml/shortcuts"),
                declared,
            )
        }
    }

    /**
     * The shortcuts themselves: what they start and what they carry. The extras are read
     * by `Shortcuts.consume`, and a name that drifts apart from that object is a shortcut
     * that opens the library and does nothing else.
     */
    @Test
    fun `the shortcuts start MainActivity with the extras Shortcuts reads`() {
        val shortcuts = Jsoup.parse(File(res("xml/shortcuts.xml")), "UTF-8", "", Parser.xmlParser())
            .select("shortcut")
        assertEquals(
            "ids of the declared shortcuts",
            listOf("add_link", "favourites"),
            shortcuts.map { it.attr("android:shortcutId") }.sorted(),
        )
        shortcuts.forEach { shortcut ->
            val id = shortcut.attr("android:shortcutId")
            val intent = shortcut.select("intent").single()
            assertEquals(
                "$id must start MainActivity; there is one window and the library is where it lands",
                "nl.potat04.kookboek.MainActivity",
                intent.attr("android:targetClass"),
            )
            assertEquals("$id targets another app", "nl.potat04.kookboek", intent.attr("android:targetPackage"))
            assertTrue(
                "$id has no label under the icon",
                shortcut.attr("android:shortcutShortLabel").startsWith("@string/"),
            )
        }
        val extras = shortcuts.select("extra").map { it.attr("android:name") }.sorted()
        assertEquals(
            "the extras must be the ones Shortcuts.consume looks for",
            listOf(Shortcuts.EXTRA_ADD_LINK, Shortcuts.EXTRA_FAVOURITES).sorted(),
            extras,
        )
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
