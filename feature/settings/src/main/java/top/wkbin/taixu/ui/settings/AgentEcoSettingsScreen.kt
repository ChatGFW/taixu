package top.wkbin.taixu.ui.settings

import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.settings.R
import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.RuntimeIconName
/**
 * 二级子页 1：智能体与 AI 模型生态
 */
@Composable
fun AgentEcoSettingsScreen(
    onBack: () -> Unit,
    onOpenModelProfiles: () -> Unit,
    onOpenLocalLlm: () -> Unit,
    onOpenAgentSettings: () -> Unit,
    onOpenSubagentSettings: () -> Unit,
    onOpenSkillSettings: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenQuickPhrases: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPhoneAgent: () -> Unit,
) {
    PersonalizationScaffold(stringResource(R.string.personal_agents_title), onBack) {
        item {
            PersonalizationSection("模型与对话") {
                PreferenceSurface(first = true, last = false) {
                    SettingsRow(
                        icon = RuntimeIconName.Model,
                        title = "模型档案管理",
                        subtitle = stringResource(R.string.settings_nav_models_summary),
                        onClick = onOpenModelProfiles, tonalIcon = true,
                    )
                }
                PreferenceSurface(first = false, last = false) {
                    SettingsRow(
                        icon = RuntimeIconName.Cpu,
                        title = "本地 LLM",
                        subtitle = stringResource(R.string.settings_nav_local_summary),
                        onClick = onOpenLocalLlm, tonalIcon = true,
                    )
                }
                PreferenceSurface(first = false, last = false) {
                    SettingsRow(
                        icon = RuntimeIconName.Chat,
                        title = "快捷短语与常用指令",
                        subtitle = stringResource(R.string.settings_nav_phrases_summary),
                        onClick = onOpenQuickPhrases, tonalIcon = true,
                    )
                }
                PreferenceSurface(first = false, last = true) {
                    SettingsRow(
                        icon = RuntimeIconName.NavDashboard,
                        title = "数据统计与用量分析",
                        subtitle = stringResource(R.string.settings_nav_stats_summary),
                        onClick = onOpenStats, tonalIcon = true,
                    )
                }
            }
        }

        item {
            PersonalizationSection("虚拟屏") {
                PreferenceSurface(first = true, last = true) {
                    PhoneAgentSettingsEntry(onOpen = onOpenPhoneAgent)
                }
            }
        }

        item {
            PersonalizationSection("智能体能力") {
                PreferenceSurface(first = true, last = false) {
                    SettingsRow(icon = RuntimeIconName.Bot, title = "Agent 执行与上下文", subtitle = stringResource(R.string.settings_nav_execution_summary), onClick = onOpenAgentSettings, tonalIcon = true)
                }
                PreferenceSurface(first = false, last = false) {
                    SettingsRow(icon = RuntimeIconName.Bot, title = "子智能体角色", subtitle = stringResource(R.string.settings_nav_roles_summary), onClick = onOpenSubagentSettings, tonalIcon = true)
                }
                PreferenceSurface(first = false, last = false) {
                    SettingsRow(icon = RuntimeIconName.Sparkles, title = "Skills 与插件", subtitle = stringResource(R.string.settings_nav_skills_summary), onClick = onOpenSkillSettings, tonalIcon = true)
                }
                PreferenceSurface(first = false, last = true) {
                    SettingsRow(
                        icon = RuntimeIconName.Network,
                        title = "MCP 协议生态与服务",
                        subtitle = stringResource(R.string.settings_nav_mcp_summary),
                        onClick = onOpenMcpSettings, tonalIcon = true,
                    )
                }
            }
        }
    }
}
