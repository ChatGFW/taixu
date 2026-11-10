package top.wkbin.taixu.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.feature.components.R as ComponentsR
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.wkbin.taixu.ui.theme.semanticColors
import top.yukonga.miuix.kmp.basic.Text

/** Roomy search and compose actions above the conversation list. */
@Composable
internal fun SessionDrawerSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onNew: () -> Unit,
    onCollapse: (() -> Unit)? = null,
) {
    val searchLabel = stringResource(R.string.chat_drawer_search)
    val newTaskLabel = stringResource(R.string.chat_drawer_new_task)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuntimeChatIdentityAvatar(size = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(LocalChatIdentity.current.name, style = RuntimeTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.chat_sidebar_subtitle), style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant)
            }
            if (onCollapse != null) RuntimeIconButton(onClick = onCollapse, modifier = Modifier.size(40.dp),
                contentDescription = stringResource(ComponentsR.string.components_collapse_sidebar)) {
                RuntimeIcon(RuntimeIconName.PanelLeftClose, Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            top.yukonga.miuix.kmp.basic.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.weight(1f).semantics { contentDescription = searchLabel },
                label = searchLabel,
            )
            RuntimeIconButton(
                onClick = onNew,
                modifier = Modifier.size(40.dp),
                contentDescription = newTaskLabel,
            ) {
                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), RuntimeTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Keep app destinations and plugin contributions reachable from a fixed footer. */
@Composable
internal fun SessionDrawerFooter(
    totalSessions: Int,
    onSettings: () -> Unit,
    navigationContent: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
                navigationContent()
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).clip(RuntimeTheme.shapes.medium).clickable(onClick = onSettings)
                .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RuntimeIcon(RuntimeIconName.Settings, Modifier.size(20.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(ComponentsR.string.components_drawer_settings),
                        style = RuntimeTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.chat_session_count, totalSessions), style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RuntimeTextButton(onClick = { expanded = !expanded }) {
                RuntimeIcon(if (expanded) RuntimeIconName.ChevronDown else RuntimeIconName.Menu, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.chat_sidebar_functions), style = RuntimeTheme.typography.labelMedium)
            }
        }
    }
}

/** 统一分区头：名称 + 待处理提示点 + 数量 + 展开/收起箭头，整行可点。 */@Composable
internal fun SessionSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    alert: Boolean = false,
) {
    val toggleLabel = stringResource(
        if (expanded) R.string.chat_drawer_section_collapse else R.string.chat_drawer_section_expand,
        title,
    )
    val stateLabel = stringResource(
        if (expanded) R.string.chat_drawer_section_expanded else R.string.chat_drawer_section_collapsed,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RuntimeTheme.shapes.small)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = toggleLabel
                stateDescription = stateLabel
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = RuntimeTheme.typography.labelMedium,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (alert) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(semanticColors().warning))
        }
        Text(
            text = "$count",
            style = RuntimeTheme.typography.labelSmall,
            color = RuntimeTheme.colorScheme.outline,
        )
        RuntimeIcon(
            name = if (expanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
            modifier = Modifier.size(14.dp),
            tint = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 跨会话的待处理提示：审批等待或执行失败的会话置顶一条，点按直接跳过去。 */
@Composable
internal fun SessionAttentionBanner(
    count: Int,
    firstTitle: String,
    onOpen: () -> Unit,
) {
    Surface(
        color = RuntimeTheme.colorScheme.surfaceContainerHigh,
        shape = RuntimeTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clip(RuntimeTheme.shapes.small).clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(semanticColors().warning))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = stringResource(R.string.chat_drawer_attention, count),
                    style = RuntimeTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = firstTitle,
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RuntimeIcon(
                RuntimeIconName.ChevronRight,
                Modifier.size(18.dp),
                RuntimeTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
