package nl.potat04.kookboek.data

import android.webkit.CookieManager

/**
 * The cookie jar the WebView fills, readable by everything else that talks to a site.
 *
 * Whatever a bot check hands out lands in the shared [CookieManager]. Reading it back
 * means the next recipe from that site usually comes in over plain HTTP again, and the
 * photo is not turned away after the page was let through.
 */
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
