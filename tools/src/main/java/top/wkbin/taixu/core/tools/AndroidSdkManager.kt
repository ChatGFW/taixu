package top.wkbin.taixu.core.tools

import java.io.File
import java.io.FilterInputStream
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import top.wkbin.taixu.core.common.shell.ShellQuote
import top.wkbin.taixu.core.network.ChecksumVerifier
import top.wkbin.taixu.core.network.DownloadEvent
import top.wkbin.taixu.core.network.DownloadError
import top.wkbin.taixu.core.network.DownloadRequest
import top.wkbin.taixu.core.network.FileDownloader
import top.wkbin.taixu.runtime.ElfInspector
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.environment.LocalBackendAccess
import top.wkbin.taixu.runtime.rootfs.TarStreamExtractor
import top.wkbin.taixu.runtime.shell.ShellCommand

data class InstalledAndroidSdk(val version: String, val path: String, val aapt2Path: String)

/** Immutable version directories keep downloads and validation away from the selected SDK. */
class AndroidSdkManager(
    private val downloader: FileDownloader,
    private val checksums: ChecksumVerifier,
    private val paths: RuntimePathManager,
    private val extractor: TarStreamExtractor,
    private val runtime: LinuxRuntime,
    private val local: LocalBackendAccess,
) {
    private val mutex = Mutex()

    suspend fun releases(distroId: String): List<AndroidSdkRelease> = mutex.withLock {
        withContext(Dispatchers.IO) {
            local.requireLocal()
            val index = File(cache(distroId), "releases.json")
            File("${index.path}.part").delete()
            try {
                downloader.download(DownloadRequest(
                    url = AndroidSdkReleases.INDEX,
                    destination = index,
                    maxBytes = 16L * 1024 * 1024,
                )).collect { }
                AndroidSdkReleases.parse(index.readText())
            } catch (error: DownloadError.Http) {
                if (error.statusCode != 403) throw error
                releasesFromFeed(distroId)
            }
        }
    }

    suspend fun installed(distroId: String): List<InstalledAndroidSdk> = withContext(Dispatchers.IO) {
        local.requireLocal()
        buildList {
            val base = File(paths.distroOptDir(distroId), "android-sdk")
            if (File(base, "build-tools/35.0.0/aapt2").isFile) {
                add(InstalledAndroidSdk("本地套件 · 35.0.0", "/opt/android-sdk", "/opt/android-sdk/build-tools/35.0.0/aapt2"))
            }
            artifacts(distroId).listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("homu-") }.forEach { dir ->
                val revision = File(dir, "ready").takeIf { it.isFile }?.readText()?.trim() ?: return@forEach
                if (!revision.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]+)?"))) return@forEach
                val root = "$ARTIFACTS/${dir.name}/android-sdk"
                if (File(dir, "android-sdk/build-tools/$revision/aapt2").isFile) {
                    add(InstalledAndroidSdk(dir.name.removePrefix("homu-").removeSuffix("-musl"), root, "$root/build-tools/$revision/aapt2"))
                }
            }
        }
    }

    suspend fun install(
        distroId: String,
        release: AndroidSdkRelease,
        onProgress: (String, Float?) -> Unit,
    ): InstalledAndroidSdk = mutex.withLock {
        withContext(Dispatchers.IO) {
            val checksum = requireNotNull(release.sha256) { "上游未提供 SHA-256，已拒绝安装可执行 SDK" }
            local.requireLocal()
            // Reconstruct and validate the destination independently of UI state or remote metadata.
            require(release.directoryName.matches(Regex("homu-[0-9]+(?:\\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]+)?-musl")))
            require(release.url == "${AndroidSdkReleases.REPOSITORY}/releases/download/${release.version}/android-sdk-aarch64-linux-musl.tar.xz")
            installed(distroId).firstOrNull { it.version == release.version }?.let { return@withContext it }
            val archive = File(cache(distroId), "${release.directoryName}.tar.xz")
            val verifiedCache = archive.isFile && runCatching {
                checksums.verify(archive, checksum)
            }.isSuccess
            if (!verifiedCache) {
                downloader.download(DownloadRequest(
                    url = release.url, destination = archive, sha256 = checksum,
                    maxBytes = 512L * 1024 * 1024,
                )).collect { event ->
                    when (event) {
                        is DownloadEvent.Progress -> onProgress("正在下载 ${release.version}", event.totalBytes?.takeIf { it > 0 }?.let { event.downloadedBytes.toFloat() / it })
                        DownloadEvent.Verifying -> onProgress("正在校验 SHA-256", null)
                        else -> Unit
                    }
                }
            }
            val parent = artifacts(distroId).apply { mkdirs() }
            val destination = File(parent, release.directoryName)
            check(!destination.exists()) { "版本目录已存在但安装记录不完整，请检查 ${destination.name}" }
            parent.listFiles().orEmpty().filter { it.name.startsWith(".install-") }.forEach(::deleteStaging)
            val staging = File(parent, ".install-${UUID.randomUUID()}")
            try {
                onProgress("正在解压 ${release.version}", null)
                val installContext = coroutineContext
                val input = object : FilterInputStream(archive.inputStream().buffered()) {
                    override fun read(): Int {
                        installContext.ensureActive()
                        return super.read()
                    }
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        installContext.ensureActive()
                        return super.read(bytes, offset, length)
                    }
                }
                input.use { compressed ->
                    XZInputStream(compressed, 128 * 1024).use { extractor.extract(it, staging) }
                }
                coroutineContext.ensureActive()
                val sdk = File(staging, "android-sdk")
                val buildTools = File(sdk, "build-tools").listFiles().orEmpty().filter {
                    it.isDirectory && File(it, "aapt2").isFile && File(it, "lib/d8.jar").isFile
                }.singleOrNull() ?: error("归档缺少唯一完整的 Build-Tools 目录")
                require(buildTools.name.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]+)?")))
                ElfInspector().requireAarch64(File(buildTools, "aapt2"))
                File(staging, "ready").writeText(buildTools.name)
                local.requireLocal()
                check(staging.renameTo(destination)) { "无法发布 SDK 安装目录" }
                archive.delete()
            } finally {
                deleteStaging(staging)
            }
            installed(distroId).first { it.version == release.version }
        }
    }

    /** Check in the captured distribution, then let the normal environment form save both paths. */
    suspend fun validate(distroId: String, sdk: InstalledAndroidSdk) = mutex.withLock {
        withContext(Dispatchers.IO) {
            local.requireLocal()
            check(sdk in installed(distroId)) { "SDK 安装已失效，请重新扫描" }
            val root = ShellQuote.of(sdk.path)
            val aapt2 = ShellQuote.of(sdk.aapt2Path)
            val script = """
                set -eu
                sdk=$root
                if [ "${'$'}sdk" != /opt/android-sdk ]; then
                    for name in platforms platform-tools; do
                        if [ ! -e "${'$'}sdk/${'$'}name" ] && [ -d "/opt/android-sdk/${'$'}name" ]; then
                            ln -s "/opt/android-sdk/${'$'}name" "${'$'}sdk/${'$'}name"
                        fi
                    done
                    if [ ! -e "${'$'}sdk/build-tools/35.0.0" ] && [ -d /opt/android-sdk/build-tools/35.0.0 ]; then
                        ln -s /opt/android-sdk/build-tools/35.0.0 "${'$'}sdk/build-tools/35.0.0"
                    fi
                fi
                test -f "${'$'}sdk/platforms/android-34/android.jar" || { echo '请先安装 Android Platform 34 基础环境'; exit 2; }
                test -f "${'$'}sdk/build-tools/35.0.0/lib/d8.jar" || { echo '请先安装 Build-Tools 35 基础环境'; exit 2; }
                test "${'$'}(od -An -t x1 -j 18 -N 2 $aapt2 | tr -d '[:space:]')" = b700
                timeout 10 $aapt2 version
            """.trimIndent()
            val result = runtime.execute(ShellCommand(script, timeoutMs = 30_000), distroId)
            check(result.isSuccess) { "SDK 验证失败\n${result.stdout}\n${result.stderr}" }
            local.requireLocal()
            check(runtime.activeDistroId.value == distroId) { "Linux 系统已切换，请在当前系统重新选择 SDK" }
        }
    }

    private fun artifacts(distroId: String) = File(paths.taixuRootDir(distroId), "toolchains/android/sdk-tools/artifacts")
    private fun cache(distroId: String) = File(paths.taixuRootDir(distroId), "android-sdk-cache").apply { mkdirs() }

    private fun deleteStaging(staging: File) {
        if (!Files.exists(staging.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(staging.toPath()).use { entries ->
            entries.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private suspend fun releasesFromFeed(distroId: String): List<AndroidSdkRelease> {
        val directory = cache(distroId)
        val feed = File(directory, "releases.atom")
        File("${feed.path}.part").delete()
        downloader.download(DownloadRequest(
            "${AndroidSdkReleases.REPOSITORY}/releases.atom", feed, maxBytes = 2L * 1024 * 1024,
        )).collect { }
        return AndroidSdkReleases.feedVersions(feed.readText()).mapNotNull { version ->
            val page = File(directory, "assets-$version.html")
            File("${page.path}.part").delete()
            downloader.download(DownloadRequest(
                "${AndroidSdkReleases.REPOSITORY}/releases/expanded_assets/$version", page,
                maxBytes = 4L * 1024 * 1024,
            )).collect { }
            AndroidSdkReleases.assetPage(version, page.readText())
        }
    }

    companion object {
        private const val ARTIFACTS = "/opt/taixu/toolchains/android/sdk-tools/artifacts"
    }
}
