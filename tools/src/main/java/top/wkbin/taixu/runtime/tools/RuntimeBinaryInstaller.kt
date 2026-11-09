package top.wkbin.taixu.runtime.tools

import top.wkbin.taixu.core.common.files.SafeFileTree
import top.wkbin.taixu.core.network.ChecksumVerifier
import top.wkbin.taixu.core.network.DownloadRequest
import top.wkbin.taixu.core.network.FileDownloader
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.rootfs.TarStreamExtractor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream

/** Installs pinned official ARM64 runtime archives outside the replaceable rootfs. */
class RuntimeBinaryInstaller(
    private val pathManager: RuntimePathManager,
    private val linuxRuntime: LinuxRuntime,
    private val fileDownloader: FileDownloader,
    private val checksumVerifier: ChecksumVerifier,
    private val tarStreamExtractor: TarStreamExtractor,
) {
    suspend fun installNode(): String = withContext(Dispatchers.IO) {
        val distroId = linuxRuntime.activeDistroId.value
        val version = NODE_VERSION
        val runtimeRoot = File(pathManager.taixuRuntimesDir(distroId), "node/$version")
        val nodeExecutable = File(runtimeRoot, "bin/node")
        if (!nodeExecutable.isFile) {
            pathManager.ensureDistroDirectories(distroId)
            val archive = File(pathManager.cacheDir, "node-$version-linux-arm64.tar.xz")
            if (archive.isFile && runCatching { checksumVerifier.verify(archive, NODE_SHA256) }.isFailure) {
                archive.delete()
            }
            if (!archive.isFile) {
                fileDownloader.download(
                    DownloadRequest(
                        url = NODE_URL,
                        destination = archive,
                        partialFile = File("${archive.absolutePath}.part"),
                        sha256 = NODE_SHA256,
                    ),
                ).collect { }
            }

            val staging = File(pathManager.taixuRootDir(distroId), ".staging-node-$version")
            SafeFileTree.delete(staging)
            staging.mkdirs()
            archive.inputStream().use { input ->
                XZInputStream(input).use { xz -> tarStreamExtractor.extract(xz, staging) }
            }
            val extractedRoot = staging.listFiles().orEmpty().singleOrNull { it.isDirectory }
                ?: error("Node archive layout is invalid")
            check(File(extractedRoot, "bin/node").isFile) { "Node archive has no ARM64 executable" }
            SafeFileTree.delete(runtimeRoot)
            runtimeRoot.parentFile?.mkdirs()
            check(extractedRoot.renameTo(runtimeRoot)) { "无法提交 Node Runtime" }
            archive.delete()
        }
        createWrappers(distroId)
        version
    }

    suspend fun removeNode() = withContext(Dispatchers.IO) {
        val distroId = linuxRuntime.activeDistroId.value
        SafeFileTree.delete(File(pathManager.taixuRuntimesDir(distroId), "node"))
        listOf("node", "npm", "npx").forEach { File(pathManager.taixuBinDir(distroId), "$it").delete() }
    }

    private fun createWrappers(distroId: String) {
        val bin = pathManager.taixuBinDir(distroId)
        bin.mkdirs()
        mapOf(
            "node" to "node",
            "npm" to "npm",
            "npx" to "npx",
        ).forEach { (name, target) ->
            val wrapper = File(bin, name)
            wrapper.writeText(
                "#!/bin/sh\nexec /opt/taixu/runtimes/node/$NODE_VERSION/bin/$target \"\$@\"\n",
            )
            wrapper.setExecutable(true, false)
        }
    }

    private companion object {
        /**
         * 兜底版本号（**不要把它当唯一来源**）。实际安装版本按优先级取：
         * 1. 环境变量 `TAIXU_NODE_VERSION`
         * 2. 该兜底值
         * 下载 URL 由版本号派生，不再单独写死一份。
         */
        const val NODE_VERSION_FALLBACK = "22.22.3"

        /** 实际生效的 Node 版本（可被环境变量覆盖） */
        val NODE_VERSION: String
            get() = System.getenv("TAIXU_NODE_VERSION")?.takeIf { it.isNotBlank() } ?: NODE_VERSION_FALLBACK

        /** 由版本号派生的下载地址 */
        val NODE_URL: String
            get() = "https://nodejs.org/download/release/v$NODE_VERSION/node-v$NODE_VERSION-linux-arm64.tar.xz"
        const val NODE_SHA256 = "1c4a9933a5e45bc88f54f70b5f91232c127ec49f1a5989d23fb85824c7adf9b7"
    }
}
