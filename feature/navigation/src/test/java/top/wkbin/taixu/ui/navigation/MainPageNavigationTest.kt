package top.wkbin.taixu.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.ui.components.MainDestination

class MainPageNavigationTest {
    @Test fun projectAndExplorerReturnThroughTheOriginalChatHistory() {
        val stack = mutableListOf<NavKey>(AgentDestination)
        assertTrue(stack.openMainOverlay(MainDestination.Workspace))
        stack.add(WorkspaceExplorerDestination("demo"))
        stack.removeAt(stack.lastIndex)
        assertEquals(listOf(AgentDestination, WorkspaceDestination), stack)
        stack.removeAt(stack.lastIndex)
        assertEquals(listOf(AgentDestination), stack)
    }

    @Test fun systemPreservesTheCallingPageAndDoesNotDuplicateTheTopEntry() {
        val stack = mutableListOf<NavKey>(AgentDestination, ToolCenterDestination)
        assertTrue(stack.openMainOverlay(MainDestination.Home))
        assertTrue(stack.openMainOverlay(MainDestination.Home))
        assertEquals(listOf(AgentDestination, ToolCenterDestination, HomeDestination), stack)
        stack.removeAt(stack.lastIndex)
        assertEquals(ToolCenterDestination, stack.last())
    }

    @Test fun settingsAndChatKeepTheirExistingNavigationHandling() {
        val stack = mutableListOf<NavKey>(AgentDestination)
        assertFalse(stack.openMainOverlay(MainDestination.Settings))
        assertFalse(stack.openMainOverlay(MainDestination.Agent))
        assertEquals(listOf(AgentDestination), stack)
    }
}
