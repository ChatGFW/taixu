package top.wkbin.taixu.ui.navigation

import androidx.navigation3.runtime.NavKey
import top.wkbin.taixu.ui.components.MainDestination

/** Projects and system overview share the caller's history, like other secondary pages. */
internal fun MutableList<NavKey>.openMainOverlay(destination: MainDestination): Boolean {
    val page = when (destination) {
        MainDestination.Workspace -> WorkspaceDestination
        MainDestination.Home -> HomeDestination
        else -> return false
    }
    if (lastOrNull() != page) add(page)
    return true
}
