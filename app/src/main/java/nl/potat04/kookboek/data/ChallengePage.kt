package nl.potat04.kookboek.data

/**
 * Recognises a bot check standing in front of the page you actually asked for.
 *
 * Kept apart from the fetching so it can be tested against saved challenge pages the
 * way the parser is tested against saved recipe pages. No Android APIs, no network.
 */
object ChallengePage {

    /**
     * Whether a response is a check rather than the page.
     *
     * [cfMitigated] is Cloudflare's `cf-mitigated` header, which it documents as the way
     * to tell a challenge apart from an ordinary refusal. When it says "challenge" there
     * is nothing left to work out. Reading the body still earns its keep for the hop
     * before that one: a site's own edge may serve a plain JavaScript redirect first,
     * with no Cloudflare header on it at all.
     */
    fun isChallenge(cfMitigated: String?, html: String, status: Int): Boolean =
        cfMitigated == "challenge" || looksLikeChallenge(html, status)

    /**
     * True when [html] is the interstitial rather than the article.
     *
     * [status] is the HTTP status where one is known; leave it at the default when
     * judging a DOM that has already been rendered, because by then there is none.
     *
     * The title check stands on its own: Cloudflare's interstitial is always called
     * "Just a moment...", and no recipe page is. The script markers are only trusted
     * when the server also refused, because a site is free to put Turnstile on its
     * comment form and still serve a perfectly readable recipe above it.
     */
    fun looksLikeChallenge(html: String, status: Int = 200): Boolean {
        val lower = html.lowercase()
        if (INTERSTITIAL.any { it in lower }) return true
        if (TITLES.any { "<title>$it" in lower }) return true
        if (status in 200..299) return false
        return MARKERS.any { it in lower }
    }

    /**
     * Only Cloudflare's own interstitial defines these, and it defines them whatever
     * language it speaks. That matters once the challenge has run: it rewrites the
     * title into the reader's language, so a Dutch phone gets "Even geduld..." and the
     * English titles below never match. A 28 KB wall then passes for a page and the
     * recipe is saved as a bare link. The embeddable Turnstile widget, which a site may
     * legitimately put on a comment form, does not define them.
     */
    private val INTERSTITIAL = listOf(
        "cf_chl_opt",
        "__cf_chl",
    )

    private val TITLES = listOf(
        "just a moment",
        "checking your browser",
        "attention required!",
        "one more step",
    )

    private val MARKERS = listOf(
        "/cdn-cgi/challenge-platform/",
        "challenges.cloudflare.com",
        "id=\"challenge-form\"",
        "ki-cf-botcl",
    )
}
