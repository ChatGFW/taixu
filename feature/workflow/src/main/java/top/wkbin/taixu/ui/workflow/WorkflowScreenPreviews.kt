package top.wkbin.taixu.ui.workflow

import androidx.compose.runtime.Composable
import top.wkbin.taixu.core.model.workflow.WorkflowDefinition
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Composable
private fun WorkflowScreenPreview() {
    RuntimePreview {
        WorkflowScreen(onBack = { })
    }
}

@FoldablePreviews
@Composable
private fun WorkflowEditorPreview() {
    RuntimePreview {
        WorkflowEditorView(
            state = WorkflowEditorUiState(definition = WorkflowDefinition(id = "preview", name = "预览工作流")),
            onSelectNode = { _ -> },
            onMoveNode = { _, _, _ -> },
            onBeginConnection = { _ -> },
            onCancelConnection = { },
            onConnect = { _ -> },
            onAddNode = { _ -> },
            onUpdateNode = { _ -> },
            onUpdateMetadata = { _, _, _, _ -> },
            onRemoveNode = { },
            onUpdateEdge = { _ -> },
            onRemoveEdge = { _ -> },
            onUndo = { },
            onRedo = { },
            onSave = { },
            onAutoLayout = { },
        )
    }
}
