package nl.potat04.kookboek

import nl.potat04.kookboek.data.ChromeUserAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChromeUserAgentTest {

    @Test
    fun `strips the webview markers from a real device agent`() {
        val webView = "Mozilla/5.0 (Linux; Android 15; Pixel 8 Build/AP4A.250105.002; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.35 " +
            "Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.7339.35 Mobile Safari/537.36",
            ChromeUserAgent.disguise(webView),
        )
    }

    @Test
    fun `leaves an agent that is already chrome alone`() {
        val chrome = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/140.0.0.0 Mobile Safari/537.36"

        assertEquals(chrome, ChromeUserAgent.disguise(chrome))
    }

    @Test
    fun `keeps the chrome version the device reported`() {
        val agent = ChromeUserAgent.disguise(
            "Mozilla/5.0 (Linux; Android 13; SM-S911B Build/TP1A; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/118.0.5993.111 Mobile Safari/537.36",
        )

        assertEquals("118", ChromeUserAgent.majorVersion(agent))
        assertEquals("118.0.5993.111", ChromeUserAgent.fullVersion(agent))
    }

    @Test
    fun `pads a version that is only a major`() {
        val agent = "Mozilla/5.0 (Linux; Android 10; K) Chrome/141 Mobile Safari/537.36"

        assertEquals("141", ChromeUserAgent.majorVersion(agent))
        assertEquals("141.0.0.0", ChromeUserAgent.fullVersion(agent))
    }

    @Test
    fun `reports nothing for an agent without a chrome version`() {
        assertNull(ChromeUserAgent.majorVersion("Kookboek/1.0"))
        assertNull(ChromeUserAgent.fullVersion("Kookboek/1.0"))
    }
}
