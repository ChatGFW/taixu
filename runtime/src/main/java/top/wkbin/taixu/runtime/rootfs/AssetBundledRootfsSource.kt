package top.wkbin.taixu.runtime.rootfs

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.logging.AppLogger

/**
 * 从 APK 的 `assets/rootfs/` 读取构建阶段产出的归档与清单（见 `tools/bundled-rootfs`）。
 *
 * 目录缺失、清单不合法、ABI 或发行版不匹配时返回 null，由调用方回退到 OCI 安装；
 * 任何情况下都不抛出，避免"没有内置包"变成安装失败。
 */
class AssetBundledRootfsSource(
    private val context: Context,
    private val logger: AppLogger,
) : BundledRootfsSource {

    override suspend fun materialize(distroId: String, targetDir: File): BundledRootfs? =
        withContext(Dispatchers.IO) {
            val manifest = readManifest() ?: return@withContext null
            if (manifest.schema != MANIFEST_SCHEMA || manifest.abi != ABI) {
                logger.w("Bundled rootfs skipped: schema=${manifest.schema} abi=${manifest.abi}")
                return@withContext null
            }
            if (!manifest.distribution.id.equals(distroId.trim(), ignoreCase = true)) return@withContext null

            targetDir.mkdirs()
            val target = File(targetDir, manifest.archive.name)
            val copied = runCatching {
                context.assets.open("$ASSET_DIR/${manifest.archive.name}").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.isSuccess
            if (!copied || target.length() != manifest.archive.bytes) {
                target.delete()
                logger.w("Bundled rootfs archive missing or truncated: ${manifest.archive.name}")
                return@withContext null
            }
            BundledRootfs(
                archive = target,
                version = manifest.distribution.version,
                assetId = manifest.assetId,
                sha256 = manifest.archive.sha256,
            )
        }

    private fun readManifest(): BundledRootfsManifest? =
        runCatching {
            context.assets.open("$ASSET_DIR/manifest.json").use { input ->
                parseBundledRootfsManifest(input.readBytes().decodeToString())
            }
        }.onFailure { logger.d("No bundled rootfs manifest: ${it.message}") }
            .getOrNull()

    private companion object {
        const val ASSET_DIR = "rootfs"
        const val MANIFEST_SCHEMA = 1
        const val ABI = "arm64"
    }
}
