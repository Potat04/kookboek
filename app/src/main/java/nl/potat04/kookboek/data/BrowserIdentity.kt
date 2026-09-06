package nl.potat04.kookboek.data

import android.content.Context
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Who the app says it is, everywhere.
 *
 * The string starts as the WebView's own, asked of the platform rather than written by
 * hand, and [ChromeUserAgent] takes the embedded-browser markers out of it. Every route
 * to a site sends this one string: the plain HTTP request, the image download, and the
 * WebView itself. They have to agree, because a clearance cookie is tied to the agent
 * that earned it.
 *
 * ## Why the disguise needs [disguise]
 *
 * Changing only the user agent string makes things worse, and that is measured, not
 * guessed. Chromium keeps sending `Sec-CH-UA` client hints describing its real brand and
 * version, an edge that cares asks for those by name in `Critical-CH`, and an agent that
 * contradicts its own hints is a louder bot signal than no disguise at all. Back when the
 * app put a hand-written Chrome string on the WebView and left the hints alone, checks
 * ran until the timeout.
 *
 * So the hints are rewritten to match. [disguise] sets the string and then restates the
 * brand list through `androidx.webkit`, turning "Android WebView" into "Google Chrome" at
 * the version the string claims. Both halves of the story then say the same thing.
 */
object BrowserIdentity {

    @Volatile private var cached: String? = null

    /** The user agent for every request the app makes, WebView and HTTP alike. */
    fun userAgent(context: Context): String = cached ?: runCatching {
        ChromeUserAgent.disguise(WebSettings.getDefaultUserAgent(context))
    }.getOrDefault(ChromeUserAgent.disguise(ImageStore.USER_AGENT)).also { cached = it }

    /**
     * Puts that identity on [webView], client hints included.
     *
     * A device whose WebView is too old to expose its user agent metadata keeps the
     * hints it was going to send. The string still helps there, and there is nothing
     * else on offer.
     */
    fun disguise(webView: WebView) {
        val agent = userAgent(webView.context)
        webView.settings.userAgentString = agent

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
        val major = ChromeUserAgent.majorVersion(agent) ?: return
        val full = ChromeUserAgent.fullVersion(agent) ?: return

        runCatching {
            val metadata = WebSettingsCompat.getUserAgentMetadata(webView.settings)
            val brands = metadata.brandVersionList.map { entry ->
                // Anything else in the list is Chromium's random decoy brand, which is
                // meant to be passed along untouched.
                val brand = when (entry.brand) {
                    WEBVIEW_BRAND -> CHROME_BRAND
                    CHROMIUM_BRAND -> CHROMIUM_BRAND
                    else -> return@map entry
                }
                UserAgentMetadata.BrandVersion.Builder()
                    .setBrand(brand)
                    .setMajorVersion(major)
                    .setFullVersion(full)
                    .build()
            }
            WebSettingsCompat.setUserAgentMetadata(
                webView.settings,
                UserAgentMetadata.Builder(metadata)
                    .setBrandVersionList(brands)
                    .setFullVersion(full)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "could not restate the client hints", it) }
    }

    private const val TAG = "BrowserIdentity"
    private const val WEBVIEW_BRAND = "Android WebView"
    private const val CHROMIUM_BRAND = "Chromium"
    private const val CHROME_BRAND = "Google Chrome"
}
