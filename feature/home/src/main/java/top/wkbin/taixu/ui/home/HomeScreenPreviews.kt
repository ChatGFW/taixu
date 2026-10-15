package top.wkbin.taixu.ui.home

import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Composable
private fun HomeScreenPreview() {
    RuntimePreview {
        LocalHomeScreen(onNavigate = { _ -> }, onOpenTerminal = { })
    }
}
