package top.wkbin.taixu.ui.workflow

import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Composable
private fun WorkflowScreenPreview() {
    RuntimePreview {
        WorkflowScreen(onBack = { })
    }
}
