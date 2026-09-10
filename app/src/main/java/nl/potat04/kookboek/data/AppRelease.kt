package nl.potat04.kookboek.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/** The single APK published with a stable GitHub release. */
data class AppRelease(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String?,
) {
    companion object {
        const val REPOSITORY = "Potat04/kookboek"
        const val MAX_APK_BYTES = 100L * 1024 * 1024

        fun parse(json: String): AppRelease {
            val root = Json.parseToJsonElement(json).jsonObject
            require(root["draft"]?.jsonPrimitive?.content == "false")
            require(root["prerelease"]?.jsonPrimitive?.content == "false")
            val version = root.getValue("tag_name").jsonPrimitive.content
            require(versionParts(version) != null) { "Unsupported release version" }
            val apk = root.getValue("assets").jsonArray.map { it.jsonObject }
                .filter { it["name"]?.jsonPrimitive?.content?.endsWith(".apk", true) == true }
                .single()
            val url = apk.getValue("browser_download_url").jsonPrimitive.content
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null)
            require(uri.port == -1 || uri.port == 443)
            require(uri.path.startsWith("/$REPOSITORY/releases/download/"))
            val size = apk.getValue("size").jsonPrimitive.content.toLong()
            require(size in 1..MAX_APK_BYTES)
            val digest = apk["digest"]?.jsonPrimitive?.content?.takeUnless { it == "null" }
            require(digest == null || digest.matches(Regex("sha256:[a-fA-F0-9]{64}")))
            return AppRelease(
                version = version,
                notes = root["body"]?.jsonPrimitive?.content?.takeUnless { it == "null" }.orEmpty(),
                downloadUrl = url,
                size = size,
                sha256 = digest?.substringAfter(':')?.lowercase(),
            )
        }

        /** Numeric comparison, with absent trailing components treated as zero. */
        fun isNewer(candidate: String, installed: String): Boolean {
            val next = requireNotNull(versionParts(candidate)) { "Unsupported release version" }
            val current = requireNotNull(versionParts(installed)) { "Unsupported installed version" }
            for (index in 0 until maxOf(next.size, current.size)) {
                val comparison = (next.getOrNull(index) ?: 0).compareTo(current.getOrNull(index) ?: 0)
                if (comparison != 0) return comparison > 0
            }
            return false
        }

        private fun versionParts(value: String): List<Long>? {
            val normalized = value.removePrefix("v")
            if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]+){0,3}"))) return null
            return normalized.split('.').map { it.toLongOrNull() ?: return null }
        }
    }
}
