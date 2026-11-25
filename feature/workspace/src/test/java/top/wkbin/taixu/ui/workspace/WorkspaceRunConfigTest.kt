package top.wkbin.taixu.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceRunConfigTest {
    @Test
    fun sampleRunsInTheMappedProject() {
        val sample = parseWorkspaceRunConfigs(workspaceRunSample).single()
        val command = sample.shellCommand("/workspace/项目 a")
        assertEquals("/workspace/项目 a", command.workingDirectory)
        assertEquals("pwd", command.commandLine)
    }

    @Test
    fun relativeDirectoryAndPreviewPortAreResolved() {
        val input = """
            {"version":1,"configurations":[{"name":"Web","command":"npm run dev","cwd":"./web app/","previewPort":5173}]}
        """.trimIndent()
        val config = parseWorkspaceRunConfigs(input).single()
        assertEquals("/workspace/demo/web app", config.shellCommand("/workspace/demo").workingDirectory)
        assertEquals("http://127.0.0.1:5173/", config.previewUrl)
    }

    @Test
    fun rejectsEscapingPathsInvalidPortsAndUnknownVersions() {
        for (path in listOf("../sibling", "/root", "web/../../sibling", "web\\..")) {
            assertThrows(IllegalArgumentException::class.java) { WorkspaceRunConfig("Dev", "npm run dev", cwd = path) }
        }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceRunConfig("Dev", "npm run dev", previewPort = 65_536) }
        assertThrows(IllegalArgumentException::class.java) { parseWorkspaceRunConfigs("""{"version":2,"configurations":[]}""") }
    }
}
