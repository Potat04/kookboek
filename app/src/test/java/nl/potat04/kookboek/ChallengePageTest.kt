package nl.potat04.kookboek

import nl.potat04.kookboek.data.ChallengePage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tested against two challenge pages a recipe site actually served on 2026-09-06, for
 * the same reason the parser is tested against saved recipe pages. Both were saved with
 * a plain curl using the app's own user agent. Only the host name in them was replaced,
 * since nothing here reads it.
 */
class ChallengePageTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name.html")) {
            "missing fixture $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `the js redirect interstitial is a challenge`() {
        assertTrue(ChallengePage.looksLikeChallenge(fixture("challenge-redirect"), 403))
    }

    @Test
    fun `the turnstile page is a challenge`() {
        assertTrue(ChallengePage.looksLikeChallenge(fixture("challenge-turnstile"), 403))
    }

    @Test
    fun `a title alone is enough, whatever the status says`() {
        assertTrue(ChallengePage.looksLikeChallenge(fixture("challenge-turnstile"), 200))
    }

    @Test
    fun `a challenge that has run is caught in any language`() {
        // Saved off a Dutch phone after the challenge script had rewritten the document.
        // Its title is "Even geduld...", so nothing in TITLES matches, and the response
        // it came from is long gone by the time the DOM is read. Before the interstitial
        // markers were trusted on their own this 28 KB wall passed for a recipe page.
        val rendered = fixture("challenge-rendered-localised")

        assertFalse("fixture should not be in English", "just a moment" in rendered.lowercase())
        assertTrue(ChallengePage.looksLikeChallenge(rendered))
        assertTrue(ChallengePage.looksLikeChallenge(rendered, 200))
    }

    @Test
    fun `real recipe pages are left alone`() {
        for (name in listOf("cheffatty", "ah", "leukerecepten", "24kitchen", "bbcgoodfood")) {
            assertFalse(name, ChallengePage.looksLikeChallenge(fixture(name), 200))
        }
    }

    @Test
    fun `the cf-mitigated header settles it on its own`() {
        // Body and status both look innocent; Cloudflare says otherwise and wins.
        assertTrue(ChallengePage.isChallenge("challenge", "<html><title>Soep</title>", 200))
        assertFalse(ChallengePage.isChallenge(null, "<html><title>Soep</title>", 200))
        assertFalse(ChallengePage.isChallenge("captcha", "<html><title>Soep</title>", 200))
    }

    @Test
    fun `the body still decides when there is no cloudflare header`() {
        // The site's own edge serves this one, and it carries no cf-mitigated.
        assertTrue(ChallengePage.isChallenge(null, fixture("challenge-redirect"), 403))
    }

    @Test
    fun `a widget on an otherwise fine page is not a challenge`() {
        val page = """
            <html><head><title>Kip kokos limoen curry</title>
            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script>
            </head><body><h1>Ingrediënten</h1></body></html>
        """.trimIndent()
        assertFalse(ChallengePage.looksLikeChallenge(page, 200))
    }
}
