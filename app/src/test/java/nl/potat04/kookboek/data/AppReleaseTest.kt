package nl.potat04.kookboek.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    @Test
    fun `numeric release ordering handles double digits and optional prefixes`() {
        assertTrue(AppRelease.isNewer("v1.0.10", "1.0.9"))
        assertTrue(AppRelease.isNewer("2", "v1.99.99"))
        assertTrue(AppRelease.isNewer("1.2.3.1", "1.2.3"))
        assertFalse(AppRelease.isNewer("v1.0.0", "1"))
        assertFalse(AppRelease.isNewer("1.0.9", "v1.0.10"))
        assertFalse(AppRelease.isNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun `unsupported versions fail rather than inventing an ordering`() {
        listOf("", "latest", "1.2-rc1", "1.2+build", "1.2.3.4.5", "-1", "1..2", "99999999999999999999999")
            .forEach { version ->
                assertThrows("candidate $version", IllegalArgumentException::class.java) {
                    AppRelease.isNewer(version, "1.0")
                }
                assertThrows("installed $version", IllegalArgumentException::class.java) {
                    AppRelease.isNewer("1.0", version)
                }
            }
    }

    @Test
    fun `stable release reads notes APK and normalizes digest`() {
        val release = AppRelease.parse(releaseJson(
            assets = listOf(asset(digest = "sha256:${"AB".repeat(32)}")),
        ))
        assertEquals("v1.0.3", release.version)
        assertEquals("Fixed timers.\nAdded updates.", release.notes)
        assertEquals(DOWNLOAD_URL, release.downloadUrl)
        assertEquals(1234L, release.size)
        assertEquals("ab".repeat(32), release.sha256)
    }

    @Test
    fun `null notes and absent digest are supported`() {
        val release = AppRelease.parse(releaseJson(notes = null))
        assertEquals("", release.notes)
        assertNull(release.sha256)
        val nullDigest = JsonObject(asset().toMutableMap().apply { put("digest", JsonNull) })
        assertNull(AppRelease.parse(releaseJson(assets = listOf(nullDigest))).sha256)
    }

    @Test
    fun `draft and prerelease responses are never offered`() {
        assertRejected(releaseJson(draft = true))
        assertRejected(releaseJson(prerelease = true))
        assertRejected(releaseJson(version = "v1.0.3-beta"))
    }

    @Test
    fun `APK selection ignores other assets but rejects missing or ambiguous APKs`() {
        val checksum = asset(name = "SHA256SUMS.txt")
        assertEquals(DOWNLOAD_URL, AppRelease.parse(releaseJson(assets = listOf(checksum, asset()))).downloadUrl)
        assertRejected(releaseJson(assets = emptyList()))
        assertRejected(releaseJson(assets = listOf(checksum)))
        assertRejected(releaseJson(assets = listOf(asset(), asset(name = "other.apk"))))
    }

    @Test
    fun `asset links must use HTTPS and the configured repository`() {
        listOf(
            DOWNLOAD_URL.replace("https:", "http:"),
            DOWNLOAD_URL.replace("github.com", "github.com.example.org"),
            DOWNLOAD_URL.replace("github.com", "example.org@github.com"),
            DOWNLOAD_URL.replace("github.com", "github.com:8443"),
            DOWNLOAD_URL.replace("Potat04/kookboek", "someone/another-app"),
            DOWNLOAD_URL.replace("/releases/download/", "/archive/"),
        ).forEach { url -> assertRejected(releaseJson(assets = listOf(asset(url = url)))) }
        assertEquals(
            DOWNLOAD_URL.replace("github.com", "github.com:443"),
            AppRelease.parse(releaseJson(assets = listOf(asset(url = DOWNLOAD_URL.replace("github.com", "github.com:443"))))).downloadUrl,
        )
    }

    @Test
    fun `invalid digests and implausible sizes are rejected`() {
        listOf("", "md5:${"a".repeat(32)}", "sha256:abc", "sha256:${"z".repeat(64)}")
            .forEach { digest -> assertRejected(releaseJson(assets = listOf(asset(digest = digest)))) }
        listOf(-1L, 0L, AppRelease.MAX_APK_BYTES + 1)
            .forEach { size -> assertRejected(releaseJson(assets = listOf(asset(size = size)))) }
        assertEquals(AppRelease.MAX_APK_BYTES, AppRelease.parse(
            releaseJson(assets = listOf(asset(size = AppRelease.MAX_APK_BYTES))),
        ).size)
    }

    @Test
    fun `daily throttle allows first check full day and clock rollback`() {
        val day = 24 * 60 * 60 * 1000L
        val checked = day * 10
        assertTrue(UpdateManager.checkDue(checked, 0))
        assertFalse(UpdateManager.checkDue(checked, checked))
        assertFalse(UpdateManager.checkDue(checked + day - 1, checked))
        assertTrue(UpdateManager.checkDue(checked + day, checked))
        assertTrue(UpdateManager.checkDue(checked - 1, checked))
    }

    private fun assertRejected(json: String) {
        assertThrows(RuntimeException::class.java) { AppRelease.parse(json) }
    }

    private fun releaseJson(
        version: String = "v1.0.3",
        draft: Boolean = false,
        prerelease: Boolean = false,
        notes: String? = "Fixed timers.\nAdded updates.",
        assets: List<JsonObject> = listOf(asset()),
    ): String = buildJsonObject {
        put("tag_name", version)
        put("draft", draft)
        put("prerelease", prerelease)
        put("body", notes?.let(::JsonPrimitive) ?: JsonNull)
        put("assets", buildJsonArray { assets.forEach(::add) })
    }.toString()

    private fun asset(
        name: String = "kookboek-v1.0.3.apk",
        url: String = DOWNLOAD_URL,
        size: Long = 1234,
        digest: String? = null,
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("browser_download_url", url)
        put("size", size)
        if (digest != null) put("digest", digest)
    }

    private companion object {
        const val DOWNLOAD_URL = "https://github.com/Potat04/kookboek/releases/download/v1.0.3/kookboek-v1.0.3.apk"
    }
}
