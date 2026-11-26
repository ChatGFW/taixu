package top.wkbin.taixu.runtime

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JADX 在线装配地址回归。
 *
 * 发行 tag 带 v，zip 资源名不带 v。脚本若再把 tag 拼进文件名，
 * `--print-urls` 会打出 jadx-v*.zip，本测试必须失败。全程不访问网络。
 */
class JadxSetupAssetTest {
    @Test
    fun downloadUrlsKeepReleaseTagOutOfAssetName() {
        val script = repoFile("app/src/main/assets/scripts/setup_jadx.sh")
        val text = script.readText()
        assertTrue(text.startsWith("#!/bin/sh\n"))
        assertFalse(text.contains('\r'))
        assertEquals(2, text.split("for url in \$JADX_URLS").size - 1)
        val urlLines = text.lines().filter { "releases/download/" in it }
        assertEquals(3, urlLines.size)
        urlLines.forEach { line ->
            assertTrue(line.contains("\${JADX_RELEASE_TAG}/\${JADX_ASSET}"))
            assertFalse(line.contains("jadx-\${JADX_RELEASE_TAG}"))
            assertFalse(line.contains("jadx-\${JADX_VERSION}"))
        }

        val syntaxOutput = runShell(listOf("sh", "-n", script.absolutePath), "sh -n")
        assertEquals("", syntaxOutput)

        val printedOutput = runShell(listOf("sh", script.absolutePath, "--print-urls"), "setup_jadx.sh --print-urls")

        val urls = printedOutput.lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(
            listOf(
                "https://mirror.ghproxy.com/https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip",
                "https://gh-proxy.com/https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip",
                "https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip",
            ),
            urls,
        )
        urls.forEach { url ->
            val asset = url.substringAfterLast('/')
            assertFalse("资源名不应带 tag 前缀: $url", asset.matches(Regex("jadx-v.*\\.zip")))
            assertTrue(asset.matches(Regex("jadx-\\d+\\.\\d+\\.\\d+\\.zip")))
        }

        val asset = "jadx-1.5.0.zip"
        val releaseUrl = "https://github.com/skylot/jadx/releases/download/v1.5.0/$asset"
        val downloads = repoFile("assets/plugins/android-suite-offline/DOWNLOADS.md").readText()
        val installer = repoFile(
            "assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh",
        ).readText()
        assertTrue(downloads.contains(releaseUrl))
        assertTrue(installer.contains(asset))
        assertFalse(Regex("jadx-v[0-9]").containsMatchIn(downloads))
        assertFalse(Regex("jadx-v[0-9]").containsMatchIn(installer))
    }

    private fun runShell(command: List<String>, label: String): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = java.io.ByteArrayOutputStream()
        val pump = Thread { process.inputStream.copyTo(output) }
        pump.start()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            pump.join(1_000)
            throw AssertionError("$label 未及时退出，下载循环不应在此运行\n${output.toString(Charsets.UTF_8)}")
        }
        pump.join(1_000)
        val text = output.toString(Charsets.UTF_8)
        assertEquals(text, 0, process.exitValue())
        return text
    }

    private fun repoFile(relative: String): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/assets/scripts/setup_jadx.sh").isFile }
            ?: error("找不到 setup_jadx.sh，cwd=$start")
        return File(root, relative)
    }
}
