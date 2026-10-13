package top.wkbin.taixu.iteration.engine

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CustomIterationBootstrap 的目录自举行为。
 *
 * 回归重点：旧实现忽略 `mkdirs()` 的返回值，目录创建失败时仍返回 `success = true`，
 * 导致 Agent 拿着一个并不存在的工作区路径继续执行 git 操作。
 */
class CustomIterationBootstrapTest {

    private fun tempHome(): File =
        Files.createTempDirectory("taixu-bootstrap").toFile().apply { deleteOnExit() }

    @Test
    fun prepareDirectoriesCreatesEveryBootstrapDirectory() {
        val home = tempHome()

        val result = CustomIterationBootstrap.prepareDirectories(home)

        assertTrue(result.success)
        assertEquals(
            File(home, CustomIterationBootstrap.WORKSPACE_NAME).absolutePath,
            result.workspacePath,
        )
        CustomIterationBootstrap.BOOTSTRAP_DIRS.forEach { relative ->
            assertTrue("应创建目录 $relative", File(home, relative).isDirectory)
        }
        assertEquals(CustomIterationBootstrap.BOOTSTRAP_PROMPT, result.prompt)
        assertNull(result.errorMessage)
    }

    @Test
    fun prepareDirectoriesIsIdempotent() {
        val home = tempHome()

        assertTrue(CustomIterationBootstrap.prepareDirectories(home).success)
        assertTrue(CustomIterationBootstrap.prepareDirectories(home).success)
    }

    @Test
    fun prepareDirectoriesReportsFailureWhenPathIsBlockedByFile() {
        val home = tempHome()
        // 用同名普通文件占位，mkdirs() 必然失败：这正是旧实现会误报成功的场景
        val blocked = File(home, CustomIterationBootstrap.WORKSPACE_NAME)
        blocked.writeText("not-a-directory")

        val result = CustomIterationBootstrap.prepareDirectories(home)

        assertFalse(result.success)
        assertEquals("", result.workspacePath)
        assertTrue(
            "错误信息应包含被占用路径的绝对路径",
            result.errorMessage.orEmpty().contains(blocked.absolutePath),
        )
    }

    @Test
    fun failedBootstrapDoesNotHandPromptToAgent() {
        val home = tempHome()
        File(home, CustomIterationBootstrap.WORKSPACE_NAME).writeText("not-a-directory")

        val result = CustomIterationBootstrap.prepareDirectories(home)

        // 失败时不得下发引导提示词，否则 Agent 会在不存在的工作区里开始执行
        assertEquals("", result.prompt)
    }
}
