package top.wkbin.taixu.ui.chat

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.QuickPhrase
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.*

@Composable
internal fun EmptyChatGuidance(
    workspaceProject: WorkspaceProject? = null,
    onboardingPrivilege: OnboardingPrivilege? = null,
    quickPhrases: List<QuickPhrase> = emptyList(),
    onSelectPhrase: (QuickPhrase) -> Unit = {},
    onSelectCommand: (SlashCommandItem) -> Unit,
) {
    val context = LocalContext.current
    val enabledPhrases = remember(quickPhrases, workspaceProject) {
        val active = quickPhrases.filter { it.isEnabled }
        if (active.isEmpty()) emptyList()
        else {
            val projectType = workspaceProject?.projectType
            val matched = when (projectType) {
                ProjectType.ANDROID -> active.filter { it.targetProjectType == "ANDROID" || it.targetProjectType == null }
                ProjectType.FLUTTER -> active.filter { it.targetProjectType == "FLUTTER" || it.targetProjectType == null }
                ProjectType.REVERSE -> active.filter { it.targetProjectType == "REVERSE" || it.targetProjectType == null }
                else -> active.filter { it.targetProjectType == null }
            }
            if (matched.isNotEmpty()) matched else active.take(4)
        }
    }

    val quickCommands = remember(workspaceProject) {
        when (workspaceProject?.projectType) {
            ProjectType.ANDROID -> listOf(
                SlashCommandItem("/android-check", context.getString(R.string.chat_android_check), context.getString(R.string.chat_android_check_description), context.getString(R.string.chat_android_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/android-build-install", context.getString(R.string.chat_android_build), context.getString(R.string.chat_android_build_description), context.getString(R.string.chat_android_build_prompt), RuntimeIconName.Play),
                SlashCommandItem("/android-debug", context.getString(R.string.chat_android_debug), context.getString(R.string.chat_android_debug_description), context.getString(R.string.chat_android_debug_prompt), RuntimeIconName.Alert),
            )
            ProjectType.FLUTTER -> listOf(
                SlashCommandItem("/flutter-check", context.getString(R.string.chat_flutter_check), context.getString(R.string.chat_flutter_check_description), context.getString(R.string.chat_flutter_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/flutter-build-install", context.getString(R.string.chat_flutter_build), context.getString(R.string.chat_flutter_build_description), context.getString(R.string.chat_flutter_build_prompt), RuntimeIconName.Play),
                SlashCommandItem("/flutter-debug", context.getString(R.string.chat_flutter_debug), context.getString(R.string.chat_flutter_debug_description), context.getString(R.string.chat_flutter_debug_prompt), RuntimeIconName.Alert),
            )
            ProjectType.REVERSE -> listOf(
                SlashCommandItem("/reverse-analyze", context.getString(R.string.chat_reverse_analyze), context.getString(R.string.chat_reverse_analyze_description), context.getString(R.string.chat_reverse_analyze_prompt), RuntimeIconName.Search),
                SlashCommandItem("/reverse-decode", context.getString(R.string.chat_reverse_decode), context.getString(R.string.chat_reverse_decode_description), context.getString(R.string.chat_reverse_decode_prompt), RuntimeIconName.Code),
            )
            else -> welcomeCommands(context)
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 32.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TaiXuBrandBadge(52.dp)
            Text(stringResource(if (workspaceProject == null) R.string.chat_welcome_title else R.string.chat_welcome_project),
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(stringResource(if (workspaceProject == null) R.string.chat_welcome_subtitle else R.string.chat_welcome_project_subtitle),
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.chat_welcome_suggestions), Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (enabledPhrases.isNotEmpty()) enabledPhrases.take(3).forEach { phrase ->
                WelcomeSuggestion(welcomeIcon(phrase.iconName), phrase.title, phrase.description.ifBlank { phrase.content }) { onSelectPhrase(phrase) }
            } else quickCommands.take(3).forEach { command ->
                WelcomeSuggestion(command.icon, command.label, command.description) { onSelectCommand(command) }
            }
            onboardingPrivilege?.let { privilege ->
                Text(stringResource(when (privilege) {
                    OnboardingPrivilege.SANDBOX -> R.string.chat_onboarding_sandbox
                    OnboardingPrivilege.SANDBOX_UNLOCKABLE -> R.string.chat_onboarding_unlockable
                    OnboardingPrivilege.SHIZUKU_READY -> R.string.chat_onboarding_shizuku
                    OnboardingPrivilege.ROOT_READY -> R.string.chat_onboarding_root
                }), Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WelcomeSuggestion(icon: RuntimeIconName, title: String, detail: String, onClick: () -> Unit) {
    RuntimeCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape = MaterialTheme.shapes.large) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RuntimeIcon(icon, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            RuntimeIcon(RuntimeIconName.ArrowUp, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun welcomeIcon(name: String) = when (name.lowercase()) {
    "play" -> RuntimeIconName.Play
    "check" -> RuntimeIconName.Check
    "alert" -> RuntimeIconName.Alert
    "code" -> RuntimeIconName.Code
    "search" -> RuntimeIconName.Search
    "terminal" -> RuntimeIconName.Terminal
    "tool", "wrench" -> RuntimeIconName.Wrench
    else -> RuntimeIconName.Bot
}

private fun welcomeCommands(context: Context): List<SlashCommandItem> = listOf(
    Triple(R.string.chat_start_build, R.string.chat_start_build_detail, R.string.chat_start_build_prompt),
    Triple(R.string.chat_start_fix, R.string.chat_start_fix_detail, R.string.chat_start_fix_prompt),
    Triple(R.string.chat_start_explore, R.string.chat_start_explore_detail, R.string.chat_start_explore_prompt),
).mapIndexed { index, (title, detail, prompt) ->
    SlashCommandItem("welcome:$index", context.getString(title), context.getString(detail), context.getString(prompt),
        listOf(RuntimeIconName.Code, RuntimeIconName.Wrench, RuntimeIconName.Search)[index])
}
