package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Composable
private fun AgentEcoSettingsScreenPreview() {
    RuntimePreview {
        AgentEcoSettingsScreen(onBack = { }, onOpenModelProfiles = { }, onOpenLocalLlm = { }, onOpenToolCenter = { }, onOpenAgentSettings = { }, onOpenSubagentSettings = { }, onOpenSkillSettings = { }, onOpenMcpSettings = { }, onOpenQuickPhrases = { }, onOpenStats = { }, onOpenPhoneAgent = { })
    }
}
