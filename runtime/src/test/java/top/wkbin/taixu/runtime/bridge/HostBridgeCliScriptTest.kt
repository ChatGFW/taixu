package top.wkbin.taixu.runtime.bridge

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用假 curl 跑真正的 `taixu-host` 脚本。
 * screen 的坐标和时长必须先通过整数校验，否则可以改写 JSON 里的 op。
 */
class HostBridgeCliScriptTest {
    private val cli = Cli()

    @Test fun screenNumbersCannotRewriteOp() {
        val tapInjected = cli.run("screen", "tap", "1,\"op\":\"text\",\"text\":\"pwned\"", "2")
        assertEquals(tapInjected.stderr, 2, tapInjected.exit)
        assertFalse(tapInjected.invoked)

        val swipeInjected = cli.run("screen", "swipe", "1", "2", "3", "4", "5,\"op\":\"text\"")
        assertEquals(swipeInjected.stderr, 2, swipeInjected.exit)
        assertFalse(swipeInjected.invoked)

        val leadingZero = cli.run("screen", "tap", "08", "1")
        assertEquals(leadingZero.stderr, 2, leadingZero.exit)
        assertFalse(leadingZero.invoked)

        val tap = cli.run("screen", "tap", "10", "20")
        assertEquals(tap.stderr, 0, tap.exit)
        assertEquals("tap", tap.body.str("op"))
        assertEquals("10", tap.body.str("x"))
        assertEquals("20", tap.body.str("y"))
        assertFalse(tap.body.containsKey("text"))

        val swipe = cli.run("screen", "swipe", "1", "2", "3", "4", "50")
        assertEquals(swipe.stderr, 0, swipe.exit)
        assertEquals("swipe", swipe.body.str("op"))
        assertEquals("1", swipe.body.str("x1"))
        assertEquals("50", swipe.body.str("duration_ms"))
    }

    @Test fun logcatLineCountCannotBreakTheBody() {
        val injected = cli.run("logcat", "pkg", "tag", "V", "kw", "1,\"op\":\"shell\"")
        assertEquals(injected.stderr, 2, injected.exit)
        assertFalse(injected.invoked)

        val ok = cli.run("logcat", "pkg", "tag", "V", "kw", "40")
        assertEquals(ok.stderr, 0, ok.exit)
        assertEquals("40", ok.body.str("lines"))
        assertEquals("pkg", ok.body.str("package"))
    }

    @Test fun settingValuesKeepSpacesAndNewlinesStayInsideJson() {
        val put = cli.run("setting", "put", "system", "device_name", "living", "room")
        assertEquals(put.stderr, 0, put.exit)
        assertEquals("put", put.body.str("op"))
        assertEquals("system", put.body.str("namespace"))
        assertEquals("device_name", put.body.str("key"))
        assertEquals("living room", put.body.str("value"))

        val notify = cli.run("notify", "Title \"x\"", "line1\nline2")
        assertEquals(notify.stderr, 0, notify.exit)
        assertEquals("Title \"x\"", notify.body.str("title"))
        assertEquals("line1\nline2", notify.body.str("body"))

        val clipboard = cli.run("clipboard", "set", "a\nb", "c")
        assertEquals(clipboard.stderr, 0, clipboard.exit)
        assertEquals("a\nb c", clipboard.body.str("text"))
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private class Cli {
        private val dir: File = java.nio.file.Files.createTempDirectory("taixu-cli").toFile()
        private val capture = File(dir, "body.json")
        private val invoked = File(dir, "invoked")
        private val script = File(dir, "taixu-host").apply {
            writeText(HostBridgeCliScript.TAIXU_HOST_SCRIPT)
            setExecutable(true)
        }

        init {
            File(dir, "curl").apply {
                writeText(
                    listOf(
                        "#!/bin/sh",
                        "touch \"\$INVOKED\"",
                        "while [ \"\$#\" -gt 0 ]; do",
                        "  if [ \"\$1\" = -d ]; then",
                        "    shift",
                        "    printf '%s' \"\$1\" > \"\$CAPTURE\"",
                        "    break",
                        "  fi",
                        "  shift",
                        "done",
                        "printf '%s\\n' '{\"success\":true}'",
                        "printf '%s\\n' 200",
                        "",
                    ).joinToString("\n"),
                )
                setExecutable(true)
            }
        }

        fun run(vararg args: String): Run {
            capture.delete()
            invoked.delete()
            val process = ProcessBuilder(listOf("/bin/sh", script.absolutePath) + args)
                .directory(dir)
                .apply {
                    environment()["PATH"] = dir.absolutePath + File.pathSeparator + environment()["PATH"].orEmpty()
                    environment()["TAIXU_BRIDGE_TOKEN"] = "mock"
                    environment()["CAPTURE"] = capture.absolutePath
                    environment()["INVOKED"] = invoked.absolutePath
                    environment()["LC_ALL"] = "C"
                }
                .start()
            val stdoutBuffer = java.io.ByteArrayOutputStream()
            val stderrBuffer = java.io.ByteArrayOutputStream()
            val stdoutPump = Thread { process.inputStream.copyTo(stdoutBuffer) }
            val stderrPump = Thread { process.errorStream.copyTo(stderrBuffer) }
            stdoutPump.start()
            stderrPump.start()
            assertTrue("CLI 挂起：${args.joinToString(" ")}", process.waitFor(15, TimeUnit.SECONDS))
            stdoutPump.join(1_000)
            stderrPump.join(1_000)
            val stdout = stdoutBuffer.toString(Charsets.UTF_8)
            val stderr = stderrBuffer.toString(Charsets.UTF_8)
            val body = if (capture.isFile && capture.length() > 0) {
                Json.parseToJsonElement(capture.readText()).jsonObject
            } else {
                JsonObject(emptyMap())
            }
            return Run(process.exitValue(), stdout, stderr, invoked.isFile, body)
        }
    }

    private class Run(
        val exit: Int,
        val stdout: String,
        val stderr: String,
        val invoked: Boolean,
        val body: JsonObject,
    )
}
