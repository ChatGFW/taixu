package top.wkbin.taixu.ui.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.theme.chatBackdropSurface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
@Composable
internal fun EmptyChatGuidance(
    workspaceProject: WorkspaceProject? = null,
    onboardingPrivilege: OnboardingPrivilege? = null,
    onSelectCommand: (SlashCommandItem) -> Unit,
) {
    val context = LocalContext.current
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 5..11 -> "早上好，今天想开辟什么工程？"
            in 12..17 -> "下午好，准备好开始 AI 结对了吗？"
            else -> "晚上好，灵感闪烁时，太墟常伴。"
        }
    }

    val quickCommands = remember(workspaceProject) {
        when (workspaceProject?.projectType) {
            ProjectType.ANDROID -> listOf(
                SlashCommandItem("/android-check", context.getString(R.string.chat_android_check), context.getString(R.string.chat_android_check_description), context.getString(R.string.chat_android_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/android-build-install", context.getString(R.string.chat_android_build), context.getString(R.string.chat_android_build_description), context.getString(R.string.chat_android_build_prompt), RuntimeIconName.Play),
                SlashCommandItem("/android-debug", context.getString(R.string.chat_android_debug), context.getString(R.string.chat_android_debug_description), context.getString(R.string.chat_android_debug_prompt), RuntimeIconName.Alert),
                SlashCommandItem("/reverse-analyze", context.getString(R.string.chat_reverse_analyze), context.getString(R.string.chat_reverse_analyze_description), context.getString(R.string.chat_reverse_analyze_prompt), RuntimeIconName.Search),
            )
            ProjectType.FLUTTER -> listOf(
                SlashCommandItem("/flutter-check", context.getString(R.string.chat_flutter_check), context.getString(R.string.chat_flutter_check_description), context.getString(R.string.chat_flutter_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/flutter-build-install", context.getString(R.string.chat_flutter_build), context.getString(R.string.chat_flutter_build_description), context.getString(R.string.chat_flutter_build_prompt), RuntimeIconName.Play),
                SlashCommandItem("/flutter-debug", context.getString(R.string.chat_flutter_debug), context.getString(R.string.chat_flutter_debug_description), context.getString(R.string.chat_flutter_debug_prompt), RuntimeIconName.Alert),
                SlashCommandItem("/init", context.getString(R.string.chat_command_init), context.getString(R.string.chat_command_init_description), context.getString(R.string.chat_command_init_description), RuntimeIconName.Code),
            )
            ProjectType.REVERSE -> listOf(
                SlashCommandItem("/reverse-analyze", context.getString(R.string.chat_reverse_analyze), context.getString(R.string.chat_reverse_analyze_description), context.getString(R.string.chat_reverse_analyze_prompt), RuntimeIconName.Search),
                SlashCommandItem("/reverse-decode", context.getString(R.string.chat_reverse_decode), context.getString(R.string.chat_reverse_decode_description), context.getString(R.string.chat_reverse_decode_prompt), RuntimeIconName.Code),
                SlashCommandItem("/android-check", context.getString(R.string.chat_android_check), context.getString(R.string.chat_android_check_description), context.getString(R.string.chat_android_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/git", context.getString(R.string.chat_command_git), context.getString(R.string.chat_command_git_description), "git status", RuntimeIconName.GitBranch),
            )
            else -> defaultBentoCommands(context)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TaiXuBrandBadge(size = 56.dp)
                Text(
                    text = greeting,
                    modifier = chatGreetingModifier(),
                    style = RuntimeTheme.typography.headlineSmall,
                    color = RuntimeTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.chat_welcome_subtitle),
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(RuntimeTheme.colorScheme.primary),
                )
                Text(
                    text = "Linux 沙箱就绪",
                    modifier = Modifier.padding(start = 6.dp),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "·",
                    modifier = Modifier.padding(horizontal = 6.dp),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = workspaceProject?.name ?: stringResource(R.string.chat_default_workspace),
                    modifier = Modifier.weight(1f, fill = false),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.chat_welcome_suggestions),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = RuntimeTheme.typography.labelMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RuntimeTheme.shapes.chip,
                    containerColor = chatBackdropSurface(),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    quickCommands.forEach { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clickable { onSelectCommand(cmd) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = cmd.label,
                                modifier = Modifier.weight(1f),
                                style = RuntimeTheme.typography.bodyMedium,
                                color = RuntimeTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            top.yukonga.miuix.kmp.basic.Icon(
                                imageVector = top.yukonga.miuix.kmp.icon.MiuixIcons.Basic.ArrowRight,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp).size(width = 10.dp, height = 16.dp),
                                tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                    }
                }
            }

            onboardingPrivilege?.takeUnless { it == OnboardingPrivilege.SANDBOX }?.let { privilege ->
                Text(
                    text = stringResource(
                        when (privilege) {
                            OnboardingPrivilege.SANDBOX -> R.string.chat_onboarding_sandbox
                            OnboardingPrivilege.SANDBOX_UNLOCKABLE -> R.string.chat_onboarding_unlockable
                            OnboardingPrivilege.SHIZUKU_READY -> R.string.chat_onboarding_shizuku
                            OnboardingPrivilege.ROOT_READY -> R.string.chat_onboarding_root
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}


private fun defaultBentoCommands(context: Context): List<SlashCommandItem> = listOf(
    SlashCommandItem("/init", "开辟新工程", "向导化初始化 Android / Flutter / Web 模板", "请列出可初始化的项目模板，协助我创建一个新工程", RuntimeIconName.Code),
    SlashCommandItem("/terminal", "演武终端", "进入 Linux PRoot 沙箱执行命令与编译", "请在 Linux 沙箱中检查当前开发环境并汇报工具链", RuntimeIconName.Terminal),
    SlashCommandItem("/doctor", "环境体检自愈", "一键诊断 SDK、构建脚本与依赖环境", "请对当前开发环境执行体检并修复潜在环境缺陷", RuntimeIconName.Wrench),
    SlashCommandItem("/explore", "智能结对审计", "阅读项目结构、分析构建报错与代码重构", "请阅读当前工作区工程结构并分析关键逻辑与优化点", RuntimeIconName.Search),
)
