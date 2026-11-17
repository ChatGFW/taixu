package top.wkbin.taixu.runtime.rootfs

import java.io.File
import kotlinx.coroutines.CancellationException
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.runtime.DistributionSpec
import top.wkbin.taixu.runtime.DownloadProgress
import top.wkbin.taixu.runtime.RegistryRoute

/**
 * 首装 RootFS 的取源策略：APK 携带内置包时优先本地安装，缺失或损坏时回退 OCI 下载。
 *
 * 只负责"选源、按清单校验、转调既有安装方法"，staging / validate / replace 仍由
 * [RootfsInstaller] 执行，不存在第二条安装链。
 */
class BundledRootfsProvisioner(
    private val rootfsInstaller: RootfsInstaller,
    private val source: BundledRootfsSource,
    private val cacheDir: File,
    private val logger: AppLogger,
) {
    suspend fun install(
        distribution: DistributionSpec,
        route: RegistryRoute,
        onStage: suspend (String) -> Unit = {},
        onProgress: suspend (DownloadProgress) -> Unit,
    ): AppResult<File> {
        val distroId = distribution.id.lowercase().trim()
        val bundled = lookup(distroId)
        if (bundled != null) {
            onStage("校验内置 RootFS 完整性")
            val result = verifyAndImport(distroId, bundled, onStage)
            bundled.archive.delete()
            if (result.errorOrNull() == null) return result
            logger.w("Bundled rootfs unusable for $distroId, falling back to OCI: ${result.errorOrNull()?.message}")
            onStage("内置包不可用，回退 OCI 镜像下载")
        } else {
            onStage("未检测到随包内置 RootFS，使用 OCI 镜像下载")
        }
        return rootfsInstaller.installOci(distribution, route, onProgress)
    }

    private suspend fun lookup(distroId: String): BundledRootfs? = try {
        source.materialize(distroId, File(cacheDir, "bundled-rootfs"))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        logger.w("Bundled rootfs lookup failed for $distroId", throwable)
        null
    }

    private suspend fun verifyAndImport(
        distroId: String,
        bundled: BundledRootfs,
        onStage: suspend (String) -> Unit,
    ): AppResult<File> {
        val expected = bundled.sha256.trim().lowercase()
        val actual = runCatching { sha256Hex(bundled.archive) }.getOrNull()
        if (actual == null || expected.length != 64 || actual != expected) {
            return AppResult.Failure(
                AppError(ErrorCode.INSTALLATION_FAILED, "内置 RootFS 完整性校验失败：归档 SHA-256 与清单不一致"),
            )
        }
        onStage("解压并安装内置 RootFS")
        return rootfsInstaller.importArchive(
            distroId,
            bundled.archive,
            version = bundled.version,
            digest = "bundled:${bundled.assetId}",
        )
    }
}
