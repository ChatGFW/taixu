package top.wkbin.taixu.runtime.tools

import top.wkbin.taixu.runtime.FakeLinuxRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteScriptRunnerTest {
    @Test
    fun downloadsToPrivateTempFileAndDoesNotPipeToShell() = runBlocking {
        val runtime = FakeLinuxRuntime()
        val runner = RemoteScriptRunner(runtime)

        runner.run(
            RemoteScriptSpec(
                name = "chatgpt",
                url = "https://chatgpt.com/codex/install.sh",
                arguments = listOf("--no-onboard"),
            ),
        )

        val command = runtime.executedCommands.single()
        assertTrue(command.contains("curl -fsSL"))
        assertTrue(command.contains("-o \"\$script_path\""))
        assertTrue(command.contains("trap 'rm -f \"\$script_path\"'"))
        assertTrue(command.contains("bash \"\$script_path\" '--no-onboard'"))
        assertFalse(command.contains("| bash"))
        assertFalse(command.contains("| sh"))
    }

    @Test
    fun optionalSha256IsVerifiedBeforeExecution() = runBlocking {
        val runtime = FakeLinuxRuntime()
        RemoteScriptRunner(runtime).run(
            RemoteScriptSpec(
                name = "codex",
                url = "https://chatgpt.com/codex/install.sh",
                sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
        )

        assertTrue(runtime.executedCommands.single().contains("sha256sum -c -"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedSha256() {
        runBlocking {
        RemoteScriptRunner(FakeLinuxRuntime()).run(
            RemoteScriptSpec(
                name = "codex",
                url = "https://chatgpt.com/codex/install.sh",
                sha256 = "not-a-sha256",
            ),
        )
        }
    }

    @Test
    fun rejectsUnregisteredScriptHost() = runBlocking {
        val runner = RemoteScriptRunner(FakeLinuxRuntime())

        var rejected = false
        try {
            runner.run(RemoteScriptSpec("demo", "https://example.com/install.sh"))
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun rawGithubAcceptsOnlyThePinnedCodexScriptAndDigest() = runBlocking {
        val runtime = FakeLinuxRuntime()
        val runner = RemoteScriptRunner(runtime)
        val spec = RemoteScriptSpec(
            name = "codex",
            url = "https://raw.githubusercontent.com/openai/codex/" +
                "53cd1894aa94185668d76e212e793e7950294f24/scripts/install/install.sh",
            sha256 = "b698d8eb50c0fbe0214ea57b5bf2af439054108404c6d2b872989db02f4bd574",
        )
        runner.run(spec)
        for (untrusted in listOf(
            spec.copy(sha256 = null),
            spec.copy(name = "other"),
            spec.copy(url = spec.url.replace("53cd1894aa94185668d76e212e793e7950294f24", "main")),
        )) {
            assertTrue(runCatching { runner.run(untrusted) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runtime.executedCommands.single().contains("sha256sum -c -"))
    }

    @Test
    fun rejectsNonHttpsScript() = runBlocking {
        val runner = RemoteScriptRunner(FakeLinuxRuntime())

        var rejected = false
        try {
            runner.run(RemoteScriptSpec("codex", "http://chatgpt.com/codex/install.sh"))
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun rejectsScriptUrlWithUserInfoOrNonStandardPort() = runBlocking {
        val runner = RemoteScriptRunner(FakeLinuxRuntime())

        val userInfo = runCatching {
            runner.run(RemoteScriptSpec("codex", "https://user@chatgpt.com/codex/install.sh"))
        }.exceptionOrNull()
        val nonStandardPort = runCatching {
            runner.run(RemoteScriptSpec("codex", "https://chatgpt.com:8443/codex/install.sh"))
        }.exceptionOrNull()

        assertTrue(userInfo is IllegalArgumentException)
        assertTrue(nonStandardPort is IllegalArgumentException)
    }
}
