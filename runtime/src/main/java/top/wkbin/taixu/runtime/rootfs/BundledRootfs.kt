package top.wkbin.taixu.runtime.rootfs

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** APK 随包携带的发行版归档；[sha256] 来自构建产物 manifest.json。 */
data class BundledRootfs(
    val archive: File,
    val version: String,
    val assetId: String,
    val sha256: String,
)

/** 提供随包携带的 RootFS；当前构建未携带时返回 null，调用方回退到 OCI 安装。 */
interface BundledRootfsSource {
    suspend fun materialize(distroId: String, targetDir: File): BundledRootfs?
}

@Serializable
internal data class BundledRootfsManifest(
    val schema: Int = 1,
    val abi: String,
    val assetId: String,
    val archive: Archive,
    val distribution: Distribution,
) {
    @Serializable
    internal data class Archive(val name: String, val bytes: Long, val sha256: String)

    @Serializable
    internal data class Distribution(val id: String, val version: String)
}

private val manifestJson = Json { ignoreUnknownKeys = true }

internal fun parseBundledRootfsManifest(raw: String): BundledRootfsManifest? =
    runCatching { manifestJson.decodeFromString<BundledRootfsManifest>(raw) }.getOrNull()

internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
