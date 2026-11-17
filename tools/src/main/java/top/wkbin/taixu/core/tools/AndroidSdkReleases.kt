package top.wkbin.taixu.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AndroidSdkRelease(
    val version: String,
    val url: String,
    val sha256: String?,
    val size: Long,
) {
    val directoryName get() = "homu-$version-musl"
}

/** Only the portable Linux ARM64 archive runs across the supported PRoot distributions. */
object AndroidSdkReleases {
    const val REPOSITORY = "https://github.com/HomuHomu833/android-sdk-custom"
    const val INDEX = "https://api.github.com/repos/HomuHomu833/android-sdk-custom/releases?per_page=100"
    private const val ASSET = "android-sdk-aarch64-linux-musl.tar.xz"
    private val versionPattern = Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]+)?")

    fun feedVersions(content: String): List<String> = Regex("href=\"${Regex.escape(REPOSITORY)}/releases/tag/([^\"]+)\"")
        .findAll(content).map { it.groupValues[1] }.filter { versionPattern.matches(it) }.distinct().take(20).toList()

    fun assetPage(version: String, content: String): AndroidSdkRelease? {
        if (!versionPattern.matches(version)) return null
        val path = "/HomuHomu833/android-sdk-custom/releases/download/$version/$ASSET"
        val row = Regex("<li\\b[\\s\\S]*?</li>").findAll(content)
            .firstOrNull { it.value.contains("href=\"$path\"") }?.value ?: return null
        val digest = Regex("sha256:([a-fA-F0-9]{64})").find(row)?.groupValues?.get(1)
        val sizeMatch = Regex(">([0-9.]+) ([KMG]B)</span>").find(row)
        val unit = mapOf("KB" to 1_000, "MB" to 1_000_000, "GB" to 1_000_000_000)
        val size = sizeMatch?.let { (it.groupValues[1].toDouble() * unit.getValue(it.groupValues[2])).toLong() } ?: 0
        return AndroidSdkRelease(version, "https://github.com$path", digest, size)
    }

    fun parse(content: String): List<AndroidSdkRelease> = Json.parseToJsonElement(content).jsonArray.mapNotNull { element ->
        val release = element.jsonObject
        if (release["draft"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
        val version = release["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (!versionPattern.matches(version)) return@mapNotNull null
        val asset = release["assets"]?.jsonArray?.firstOrNull {
            it.jsonObject["name"]?.jsonPrimitive?.content == ASSET
        }?.jsonObject ?: return@mapNotNull null
        val expectedUrl = "$REPOSITORY/releases/download/$version/$ASSET"
        if (asset["browser_download_url"]?.jsonPrimitive?.content != expectedUrl) return@mapNotNull null
        val digest = asset["digest"]?.jsonPrimitive?.content
            ?.removePrefix("sha256:")?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
        AndroidSdkRelease(version, expectedUrl, digest, asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0)
    }.distinctBy { it.version }
}
