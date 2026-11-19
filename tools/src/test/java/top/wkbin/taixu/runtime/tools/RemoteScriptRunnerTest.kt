package top.wkbin.taixu.runtime.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定官方安装脚本执行链的防篡改要素：HTTPS、禁重定向、0700 权限、SHA-256 校验、退出清理。 */
class RemoteScriptRunnerTest {

    @Test fun `command pins script sha256 and hardens the execution chain`() {
        val command = RemoteScriptRunner.buildCommand(
            RemoteScriptSpec(
                name = "codex",
                url = "https://chatgpt.com/codex/install.sh",
                sha256 = CodexToolInstaller.CODEX_INSTALL_SCRIPT_SHA256,
            ),
        )
        assertTrue(command.contains("sha256sum -c -"))
        assertTrue(command.contains(CodexToolInstaller.CODEX_INSTALL_SCRIPT_SHA256))
        assertTrue(command.contains("--max-redirs 0"))
        assertTrue(command.contains("--proto '=https'"))
        assertTrue(command.contains("chmod 700"))
        assertTrue(command.contains("trap 'rm -f"))
    }

    @Test fun `command without sha256 skips the checksum step`() {
        val command = RemoteScriptRunner.buildCommand(
            RemoteScriptSpec(name = "codex", url = "https://chatgpt.com/codex/install.sh"),
        )
        assertFalse(command.contains("sha256sum"))
        assertTrue(command.contains("bash"))
    }
}
