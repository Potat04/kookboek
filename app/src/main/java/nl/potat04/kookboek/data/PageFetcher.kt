package nl.potat04.kookboek.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import org.jsoup.Jsoup
import kotlin.coroutines.resume

/** What came back for a page. */
sealed interface FetchResult {
    data class Page(val html: String) : FetchResult
    /** A bot check stood in the way and would not step aside. */
    data object Blocked : FetchResult
    /** No connection, a 404, a timeout — the page itself never arrived. */
    data object Unreachable : FetchResult
}

/**
 * Gets the HTML of a page, by two routes.
 *
 * The plain HTTP request is the one that runs for almost every site, and it stays the
 * first attempt: one round trip, no browser, no rendering. But a growing number of food
 * blogs sit behind a Cloudflare check that answers a bare request with "Just a moment..."
 * and hands the real page over only to something that runs the challenge script. So when
 * that happens the URL goes to a WebView, which is a real Chromium and passes the check
 * the ordinary way — the same way it passes when you open the page in your browser.
 *
 * The cookie Cloudflare hands out afterwards is kept in the shared [CookieManager], so
 * the next recipe from that site usually comes back over plain HTTP again, and so
 * [ImageStore] can fetch the photo without being turned away in turn.
 */
class PageFetcher(context: Context) {

    private val appContext = context.applicationContext

    suspend fun fetch(url: String, acceptLanguage: String): FetchResult {
        val direct = fetchDirect(url, acceptLanguage)
        if (direct !is FetchResult.Blocked) return direct

        Log.i(TAG, "bot check on $url, trying again in a browser")
        return fetchInBrowser(url, acceptLanguage)
    }

    private suspend fun fetchDirect(url: String, acceptLanguage: String): FetchResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = Jsoup.connect(url)
                    .userAgent(BrowserIdentity.userAgent(appContext))
                    .header("Accept", ACCEPT)
                    .header("Accept-Language", acceptLanguage)
                    .cookies(SiteCookies.forUrl(url))
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .timeout(25_000)
                    .maxBodySize(MAX_BODY)
                    .execute()

                val html = response.body()
                val status = response.statusCode()
                when {
                    ChallengePage.isChallenge(
                        cfMitigated = response.header("cf-mitigated"),
                        html = html,
                        status = status,
                    ) -> FetchResult.Blocked
                    status in RETRY_IN_BROWSER -> FetchResult.Blocked
                    status !in 200..299 -> FetchResult.Unreachable
                    else -> FetchResult.Page(html)
                }
            }.onFailure { Log.w(TAG, "fetch failed for $url", it) }
                .getOrDefault(FetchResult.Unreachable)
        }

    /**
     * Loads the page in a WebView and reads the DOM back out.
     *
     * There is no callback for "the bot check is over": the challenge replaces the
     * document under its own steam, sometimes more than once. So the DOM is sampled
     * every [POLL_MS] until it stops looking like an interstitial and has enough in it
     * to be a page, and [TIMEOUT_MS] decides when to give up.
     *
     * Nothing here is dressed up. The WebView keeps its own user agent, loads what a
     * page normally loads, and lets the check run. That is the whole trick: an ordinary
     * browser passes, and anything that only half looks like one does not.
     *
     * It goes on the [ChallengeStage] straight away but out of sight. Most checks never
     * need to be seen and pass in a second or two; the WebView does not have to be on
     * screen, or attached to anything, for that to happen. Being on the stage already is
     * only so that a check which turns out to want a tap can be shown the instant it
     * says so, without moving a loaded WebView between windows.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchInBrowser(url: String, acceptLanguage: String): FetchResult =
        withContext(Dispatchers.Main) {
            var web: WebView? = null
            try {
                val view = WebView(appContext).also { web = it }
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(view, true)
                }
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                view.addJavascriptInterface(ChallengeWatcher(view), BRIDGE)
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(INTERACTIVE_LISTENER, null)
                    }
                }
                ChallengeStage.show(view)
                view.loadUrl(url, mapOf("Accept-Language" to acceptLanguage))

                val html = withTimeoutOrNull(TIMEOUT_MS) {
                    val startedAt = SystemClock.elapsedRealtime()
                    var page: String? = null
                    while (page == null) {
                        delay(POLL_MS)
                        if (SystemClock.elapsedRealtime() - startedAt > REVEAL_MS) {
                            // Backstop for a wall that is not Cloudflare and so never
                            // announces itself. Cloudflare's own signal is quicker.
                            ChallengeStage.reveal(view)
                        }
                        val current = view.currentHtml() ?: continue
                        if (current.length >= MIN_HTML &&
                            !ChallengePage.looksLikeChallenge(current)
                        ) {
                            page = current
                        }
                    }
                    page
                }

                if (html == null) {
                    Log.w(TAG, "browser could not get past the bot check on $url")
                    return@withContext FetchResult.Blocked
                }
                CookieManager.getInstance().flush()
                FetchResult.Page(html)
            } catch (e: Exception) {
                // A device with the WebView package disabled or updating lands here.
                Log.w(TAG, "no browser available for $url", e)
                FetchResult.Unreachable
            } finally {
                web?.let { view ->
                    ChallengeStage.clear(view)
                    view.stopLoading()
                    // Destroying a WebView that is still in a view tree crashes; the
                    // screen drops it a frame after the stage empties, so undo the
                    // attachment here rather than trusting the timing.
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                }
            }
        }

    /**
     * The one thing the page is allowed to call back into.
     *
     * Cloudflare posts a message the moment its check stops being automatic and starts
     * wanting a person. Listening for that beats guessing from a clock: an automatic
     * check that happens to be slow never flashes a browser at you, and one that needs a
     * tap gets shown at once instead of after a wait.
     *
     * A JavaScript interface is reachable by every script on the page, so this exposes
     * exactly one method and that method only makes a WebView visible.
     */
    private class ChallengeWatcher(private val web: WebView) {
        @JavascriptInterface
        fun interactiveDetected() = ChallengeStage.reveal(web)
    }

    /** The document as it stands right now, or null while the page is still empty. */
    private suspend fun WebView.currentHtml(): String? =
        suspendCancellableCoroutine { cont ->
            evaluateJavascript("document.documentElement.outerHTML") { encoded ->
                // The result arrives as a JSON string literal, escapes and all.
                val html = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
                cont.resume(html)
            }
        }

    private companion object {
        const val TAG = "PageFetcher"
        const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        const val MAX_BODY = 6 * 1024 * 1024
        const val TIMEOUT_MS = 60_000L
        const val POLL_MS = 400L

        /**
         * How long a check may run unseen before the page is brought forward anyway.
         * Only reached by a wall that never says it has gone interactive.
         */
        const val REVEAL_MS = 10_000L

        const val BRIDGE = "kookboek"

        /** Cloudflare's announcement that its check now wants a person. */
        val INTERACTIVE_LISTENER = """
            addEventListener("message", ({data}) => {
                if (data?.source === "cloudflare-challenge" && data?.event === "interactiveBegin") {
                    $BRIDGE.interactiveDetected();
                }
            })
        """.trimIndent()

        /** Below this the DOM is still the blank document a fresh WebView starts with. */
        const val MIN_HTML = 2_000

        /** Refusals that a real browser stands a chance of turning into a page. */
        val RETRY_IN_BROWSER = setOf(403, 429, 503)
    }
}

/**
 * Who the app says it is, everywhere.
 *
 * This is the WebView's own user agent, not one written by hand. A made-up string is
 * worse than useless: the WebView still sends `Sec-CH-UA` client hints describing its
 * real version, an edge that cares asks for those by name in `Critical-CH`, and a
 * user agent that contradicts them is a louder bot signal than no disguise at all. That
 * mismatch is what kept the check spinning until it was taken out.
 *
 * Cloudflare also ties its clearance cookie to the agent that earned it, so the plain
 * HTTP requests and the image downloads have to send the same one to keep using it.
 * [ImageStore.USER_AGENT] is only the fallback for a device with no usable WebView.
 */
object BrowserIdentity {

    @Volatile private var cached: String? = null

    fun userAgent(context: Context): String = cached ?: runCatching {
        WebSettings.getDefaultUserAgent(context)
    }.getOrDefault(ImageStore.USER_AGENT).also { cached = it }
}

/** The cookie jar the WebView fills, readable by the plain HTTP requests. */
object SiteCookies {

    /** Cookies for [url] as Jsoup wants them, or empty when there are none. */
    fun forUrl(url: String): Map<String, String> = runCatching {
        val raw = CookieManager.getInstance().getCookie(url) ?: return emptyMap()
        raw.split(';')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null else pair.take(index).trim() to pair.drop(index + 1).trim()
            }
            .toMap()
    }.getOrDefault(emptyMap())

    /** The same cookies as one header value, for [java.net.HttpURLConnection]. */
    fun header(url: String): String? = runCatching {
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
