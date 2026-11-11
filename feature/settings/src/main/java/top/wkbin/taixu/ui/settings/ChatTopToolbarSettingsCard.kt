package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.wkbin.taixu.core.datastore.AppearancePreferences
import top.wkbin.taixu.core.model.ChatTopToolbarAction
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeFilterChip
import top.wkbin.taixu.ui.components.RuntimeFloatingToolbar
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun ChatTopToolbarSettingsCard() {
    val preferences = previewOrNull { koinInject<AppearancePreferences>() }
    val pinned by preferences?.chatTopToolbarPinned
        ?.collectAsStateWithLifecycle(ChatTopToolbarAction.DEFAULT_PINNED)
        ?: previewStateOf(ChatTopToolbarAction.DEFAULT_PINNED)
    val pinnedSet = pinned.toSet()
    val atLimit = pinned.size >= ChatTopToolbarAction.MAX_PINNED
    val scope = rememberCoroutineScope()
    val previewLabel = stringResource(R.string.settings_chat_top_toolbar_preview)
    val moreLabel = stringResource(R.string.settings_chat_top_toolbar_more)
    PersonalizationSection(stringResource(R.string.settings_chat_top_toolbar)) {
        RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.settings_chat_top_toolbar_description),
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = previewLabel },
                    horizontalArrangement = Arrangement.End,
                ) {
                    RuntimeFloatingToolbar {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            pinned.forEach { action ->
                                val label = stringResource(action.settingsLabelRes())
                                RuntimeIconButton(
                                    onClick = {
                                        scope.launch { preferences?.setChatTopToolbarPinned(action, false) }
                                    },
                                    modifier = Modifier.size(40.dp),
                                    contentDescription = label,
                                ) {
                                    RuntimeIcon(
                                        action.settingsIcon(),
                                        Modifier.size(22.dp),
                                        RuntimeTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            RuntimeIconButton(
                                onClick = {},
                                modifier = Modifier.size(40.dp),
                                contentDescription = moreLabel,
                            ) {
                                RuntimeIcon(
                                    RuntimeIconName.More,
                                    Modifier.size(22.dp),
                                    RuntimeTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                if (atLimit) {
                    Text(
                        stringResource(R.string.settings_chat_top_toolbar_full),
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.tertiary,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChatTopToolbarAction.entries.forEach { action ->
                        val selected = action in pinnedSet
                        RuntimeFilterChip(
                            selected = selected,
                            enabled = selected || !atLimit,
                            onClick = {
                                scope.launch { preferences?.setChatTopToolbarPinned(action, !selected) }
                            },
                            label = { Text(stringResource(action.settingsLabelRes()), maxLines = 1) },
                            leadingIcon = {
                                RuntimeIcon(
                                    action.settingsIcon(),
                                    Modifier.size(16.dp),
                                    if (selected) {
                                        RuntimeTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        RuntimeTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            shape = RuntimeTheme.shapes.small,
                        )
                    }
                }
            }
        }
    }
}

private fun ChatTopToolbarAction.settingsLabelRes(): Int = when (this) {
    ChatTopToolbarAction.BOTS -> R.string.settings_chat_top_toolbar_action_bots
    ChatTopToolbarAction.WORKSPACE -> R.string.settings_chat_top_toolbar_action_workspace
    ChatTopToolbarAction.MEMORY -> R.string.settings_chat_top_toolbar_action_memory
    ChatTopToolbarAction.OUTLINE -> R.string.settings_chat_top_toolbar_action_outline
    ChatTopToolbarAction.EXPORT_MARKDOWN -> R.string.settings_chat_top_toolbar_action_export
    ChatTopToolbarAction.BROWSER -> R.string.settings_chat_top_toolbar_action_browser
    ChatTopToolbarAction.REPOSITORY -> R.string.settings_chat_top_toolbar_action_repository
    ChatTopToolbarAction.SPEECH -> R.string.settings_chat_top_toolbar_action_speech
    ChatTopToolbarAction.RUN_MODE -> R.string.settings_chat_top_toolbar_action_run_mode
    ChatTopToolbarAction.BRANCHES -> R.string.settings_chat_top_toolbar_action_branches
    ChatTopToolbarAction.RUNTIME -> R.string.settings_chat_top_toolbar_action_runtime
    ChatTopToolbarAction.TOOLCHAIN -> R.string.settings_chat_top_toolbar_action_toolchain
    ChatTopToolbarAction.REASONING -> R.string.settings_chat_top_toolbar_action_reasoning
    ChatTopToolbarAction.FLOATING_CHAT -> R.string.settings_chat_top_toolbar_action_floating
}

private fun ChatTopToolbarAction.settingsIcon(): RuntimeIconName = when (this) {
    ChatTopToolbarAction.BOTS -> RuntimeIconName.Bot
    ChatTopToolbarAction.WORKSPACE -> RuntimeIconName.NavRepository
    ChatTopToolbarAction.MEMORY -> RuntimeIconName.Brain
    ChatTopToolbarAction.OUTLINE -> RuntimeIconName.List
    ChatTopToolbarAction.EXPORT_MARKDOWN -> RuntimeIconName.Save
    ChatTopToolbarAction.BROWSER -> RuntimeIconName.Globe
    ChatTopToolbarAction.REPOSITORY -> RuntimeIconName.GitBranch
    ChatTopToolbarAction.SPEECH -> RuntimeIconName.VolumeOn
    ChatTopToolbarAction.RUN_MODE -> RuntimeIconName.Code
    ChatTopToolbarAction.BRANCHES -> RuntimeIconName.GitBranch
    ChatTopToolbarAction.RUNTIME -> RuntimeIconName.Logs
    ChatTopToolbarAction.TOOLCHAIN -> RuntimeIconName.Wrench
    ChatTopToolbarAction.REASONING -> RuntimeIconName.Sparkles
    ChatTopToolbarAction.FLOATING_CHAT -> RuntimeIconName.OpenInNew
}
