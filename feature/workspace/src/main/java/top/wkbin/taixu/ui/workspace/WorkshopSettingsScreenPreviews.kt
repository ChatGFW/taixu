package top.wkbin.taixu.ui.workspace

import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Composable
private fun WorkshopSettingsScreenPreview() {
    RuntimePreview {
        WorkshopSettingsScreen(onBack = { }, onOpenEnvironment = { })
    }
}

@FoldablePreviews
@Composable
private fun WorkshopEnvironmentSettingsScreenPreview() {
    RuntimePreview {
        WorkshopEnvironmentSettingsScreen(onBack = { })
    }
}

@FoldablePreviews
@Composable
private fun WorkshopScriptEditorScreenPreview() {
    RuntimePreview {
        WorkshopScriptEditorScreen(type = WorkshopScriptType.entries.first(), onBack = { })
    }
}
