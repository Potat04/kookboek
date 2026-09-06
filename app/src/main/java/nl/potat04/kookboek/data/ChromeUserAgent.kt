package nl.potat04.kookboek.data

/**
 * Rewrites a WebView's own user agent into the one Chrome for Android would send.
 *
 * A WebView announces itself twice over. Its user agent carries a `; wv)` platform token
 * and a `Version/4.0` marker that no Chrome has, and its client hints name the brand
 * "Android WebView". Plenty of bot checks let that through. The heavier managed checks
 * do not, and they are the ones a fetch gets stuck on.
 *
 * Only the giveaways go. The Chrome version stays exactly as the device reported it,
 * because that version has to keep matching the client hints that [BrowserIdentity]
 * derives from this string. The device model becomes the same "Android 10; K" that
 * Chrome itself sends, which is a real Chrome string rather than an invented one.
 *
 * Pure, no Android APIs, so it is tested the way the parser is tested.
 */
object ChromeUserAgent {

    /** [webViewUserAgent] with the embedded-browser markers taken out. */
    fun disguise(webViewUserAgent: String): String =
        webViewUserAgent
            .replace(DEVICE, "; Android 10; K)")
            .replace(WEBVIEW_MARKER, "Chrome/")

    /** The Chrome major version in [userAgent], or null when there is none to find. */
    fun majorVersion(userAgent: String): String? =
        VERSION.find(userAgent)?.groupValues?.get(1)

    /**
     * The four-part Chrome version in [userAgent], or null when there is none.
     *
     * An agent that only carries a major version gets the ".0.0.0" that Chrome sends,
     * so the client hints can state a full version either way.
     */
    fun fullVersion(userAgent: String): String? {
        val match = VERSION.find(userAgent) ?: return null
        return match.groupValues[1] + match.groupValues[2].ifEmpty { ".0.0.0" }
    }

    /** Everything from the platform token up to its closing bracket. */
    private val DEVICE = """; Android .*?\)""".toRegex()

    /** The `Version/4.0` that sits in front of `Chrome/` in every WebView agent. */
    private val WEBVIEW_MARKER = """Version/.* Chrome/""".toRegex()

    private val VERSION = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()
}
