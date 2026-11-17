package top.wkbin.taixu.core.tools

import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import top.wkbin.taixu.core.datastore.RemoteServerRepository
import top.wkbin.taixu.core.network.ChecksumVerifier
import top.wkbin.taixu.core.network.DownloadEvent
import top.wkbin.taixu.core.network.DownloadError
import top.wkbin.taixu.core.network.DownloadRequest
import top.wkbin.taixu.core.network.FileDownloader
import top.wkbin.taixu.runtime.ElfInspector
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.environment.LocalBackendAccess
import top.wkbin.taixu.runtime.rootfs.RootfsValidator
import top.wkbin.taixu.runtime.rootfs.TarStreamExtractor
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.ShellCommand

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidSdkManagerTest {
    @Test fun apiRateLimitFallsBackToGitHubFeedAndAssetPage() = runBlocking {
        val fixture = Fixture()
        fixture.rateLimited = true
        fixture.metadata = mapOf(
            "${AndroidSdkReleases.REPOSITORY}/releases.atom" to """<feed><link href="${AndroidSdkReleases.REPOSITORY}/releases/tag/37.0.0"/></feed>""",
            "${AndroidSdkReleases.REPOSITORY}/releases/expanded_assets/37.0.0" to """<li><a href="/HomuHomu833/android-sdk-custom/releases/download/37.0.0/android-sdk-aarch64-linux-musl.tar.xz">SDK</a><span>sha256:${"ab".repeat(32)}</span></li>""",
        )
        val releases = fixture.manager.releases("ubuntu")
        assertEquals(listOf("37.0.0"), releases.map { it.version })
        assertEquals("ab".repeat(32), releases.single().sha256)
        assertEquals(3, fixture.downloads)
    }

    @Test fun downloadsPublishesAndValidatesInCapturedDistribution() = runBlocking {
        val fixture = Fixture()
        val orphan = File(fixture.artifacts, ".install-orphan").apply { mkdirs() }
        File(orphan, "partial").writeText("interrupted extraction")
        val sdk = fixture.manager.install("ubuntu", fixture.release()) { _, _ -> }
        assertFalse(orphan.exists())
        assertTrue(sdk.path.contains("homu-37.0.0-musl/android-sdk"))
        assertTrue(sdk.aapt2Path.endsWith("build-tools/37.0.0/aapt2"))
        assertEquals(2, fixture.manager.installed("ubuntu").size)
        assertTrue(fixture.manager.installed("debian").isEmpty())
        fixture.manager.validate("ubuntu", sdk)
        assertEquals("ubuntu", fixture.executedDistro)
        assertTrue(fixture.executedScript.contains(sdk.aapt2Path))
        assertTrue(fixture.executedScript.contains("ln -s /opt/android-sdk/build-tools/35.0.0"))
        val downloads = fixture.downloads
        fixture.manager.install("ubuntu", fixture.release()) { _, _ -> }
        assertEquals(downloads, fixture.downloads)
        assertEquals("old-aapt2", fixture.baseAapt2.readText())
    }

    @Test fun badArchitectureNeverPublishesAndCanBeRetried() = runBlocking {
        val fixture = Fixture()
        fixture.archive = archive(machine = 0x3e)
        val failure = runCatching { fixture.manager.install("ubuntu", fixture.release()) { _, _ -> } }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(1, fixture.manager.installed("ubuntu").size)
        assertFalse(fixture.artifacts.listFiles().orEmpty().any { it.name.startsWith(".install-") || it.name.startsWith("homu-") })
        assertEquals("old-aapt2", fixture.baseAapt2.readText())
        fixture.archive = archive()
        fixture.manager.install("ubuntu", fixture.release()) { _, _ -> }
        assertEquals(2, fixture.manager.installed("ubuntu").size)
    }

    @Test fun missingDigestIsRejectedBeforeAnyDownload() = runBlocking {
        val fixture = Fixture()
        val failure = runCatching {
            fixture.manager.install("ubuntu", fixture.release().copy(sha256 = null)) { _, _ -> }
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("SHA-256"))
        assertEquals(0, fixture.downloads)
        assertEquals("old-aapt2", fixture.baseAapt2.readText())
    }

    @Test fun cancelledDownloadLeavesExistingSdkUsable() = runBlocking {
        val fixture = Fixture()
        fixture.cancelDownload = true
        assertTrue(runCatching {
            fixture.manager.install("ubuntu", fixture.release()) { _, _ -> }
        }.exceptionOrNull() is CancellationException)
        assertEquals(1, fixture.manager.installed("ubuntu").size)
        assertEquals("old-aapt2", fixture.baseAapt2.readText())
        fixture.cancelDownload = false
        assertEquals("37.0.0", fixture.manager.install("ubuntu", fixture.release()) { _, _ -> }.version)
    }

    @Test fun refusesFailedValidationAndChangedDistribution() = runBlocking {
        val fixture = Fixture()
        val sdk = fixture.manager.install("ubuntu", fixture.release()) { _, _ -> }
        fixture.exitCode = 2
        assertTrue(runCatching { fixture.manager.validate("ubuntu", sdk) }.isFailure)
        fixture.exitCode = 0
        fixture.activeDistro.value = "debian"
        assertTrue(runCatching { fixture.manager.validate("ubuntu", sdk) }.isFailure)
        assertTrue(runCatching { fixture.manager.validate("debian", sdk) }.isFailure)
    }

    private class Fixture {
        private val context = RuntimeEnvironment.getApplication().apply {
            applicationInfo.nativeLibraryDir = File(cacheDir, "native-libs").apply { mkdirs() }.path
        }
        val paths = RuntimePathManager(context, RootfsValidator(ElfInspector()))
        val artifacts = File(paths.taixuRootDir("ubuntu"), "toolchains/android/sdk-tools/artifacts")
        val baseAapt2 = File(paths.distroOptDir("ubuntu"), "android-sdk/build-tools/35.0.0/aapt2")
        val activeDistro = MutableStateFlow("ubuntu")
        var archive = archive()
        var downloads = 0
        var cancelDownload = false
        var rateLimited = false
        var metadata: Map<String, String> = emptyMap()
        var exitCode = 0
        var executedDistro: String? = null
        var executedScript = ""
        private val checksums = ChecksumVerifier()
        private val runtime = Proxy.newProxyInstance(LinuxRuntime::class.java.classLoader, arrayOf(LinuxRuntime::class.java)) { _, method, args ->
            when (method.name) {
                "getActiveDistroId" -> activeDistro
                "execute" -> {
                    executedScript = (args[0] as ShellCommand).commandLine
                    executedDistro = args[1] as String
                    CommandResult(exitCode, "probe", "", 1)
                }
                else -> error("Unexpected runtime call ${method.name}")
            }
        } as LinuxRuntime
        private val servers = Proxy.newProxyInstance(RemoteServerRepository::class.java.classLoader, arrayOf(RemoteServerRepository::class.java)) { _, method, _ ->
            check(method.name == "getActiveId")
            MutableStateFlow<String?>(null)
        } as RemoteServerRepository
        private val downloader = object : FileDownloader {
            override fun download(request: DownloadRequest) = flow {
                downloads++
                if (cancelDownload) throw CancellationException("cancelled")
                if (rateLimited && request.url == AndroidSdkReleases.INDEX) throw DownloadError.Http(403, "rate limited")
                request.destination.parentFile!!.mkdirs()
                request.destination.writeBytes(metadata[request.url]?.toByteArray() ?: archive)
                request.sha256?.let { checksums.verify(request.destination, it) }
                emit(DownloadEvent.Completed(request.destination))
            }
        }
        val manager = AndroidSdkManager(downloader, checksums, paths,
            TarStreamExtractor(AppLogger(context, SensitiveDataRedactor { it })), runtime, LocalBackendAccess(servers))

        init {
            paths.baseDir.deleteRecursively()
            baseAapt2.parentFile!!.mkdirs()
            baseAapt2.writeText("old-aapt2")
        }

        fun release(): AndroidSdkRelease {
            val file = File(context.cacheDir, "test-sdk.xz").apply { writeBytes(archive) }
            return AndroidSdkRelease("37.0.0", "${AndroidSdkReleases.REPOSITORY}/releases/download/37.0.0/android-sdk-aarch64-linux-musl.tar.xz",
                checksums.sha256(file), archive.size.toLong())
        }
    }

    companion object {
        private fun archive(machine: Int = 0xb7): ByteArray {
            val elf = ByteArray(64).apply {
                this[0] = 0x7f
                this[1] = 'E'.code.toByte()
                this[2] = 'L'.code.toByte()
                this[3] = 'F'.code.toByte()
                this[4] = 2
                this[5] = 1
                this[18] = machine.toByte()
            }
            val tar = ByteArrayOutputStream()
            mapOf("aapt2" to elf, "lib/d8.jar" to byteArrayOf(1)).forEach { (name, bytes) ->
                val header = ByteArray(512)
                fun field(offset: Int, length: Int, value: String) {
                    value.toByteArray().copyInto(header, offset, endIndex = minOf(length, value.length))
                }
                field(0, 100, "android-sdk/build-tools/37.0.0/$name")
                field(100, 8, "0000755")
                field(124, 12, bytes.size.toString(8).padStart(11, '0'))
                header[156] = '0'.code.toByte()
                field(257, 6, "ustar")
                tar.write(header)
                tar.write(bytes)
                tar.write(ByteArray((512 - bytes.size % 512) % 512))
            }
            tar.write(ByteArray(1024))
            val result = ByteArrayOutputStream()
            XZOutputStream(result, LZMA2Options()).use { it.write(tar.toByteArray()) }
            return result.toByteArray()
        }
    }
}
