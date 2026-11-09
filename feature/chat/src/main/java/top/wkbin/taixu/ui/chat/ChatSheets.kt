package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import top.wkbin.taixu.ui.miuix.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateColorAsState
import top.wkbin.taixu.ui.miuix.Slider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.ModalBottomSheet
import top.wkbin.taixu.ui.miuix.Surface
import top.wkbin.taixu.ui.miuix.SwitchDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import top.wkbin.taixu.ui.miuix.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.McpServerRow
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.core.model.McpConnectionState
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.model.McpServerConfig

/** 底部弹层：能力挂载面板、推理强度滑块、审批模式选择。 */
@Composable
internal fun SkillsAndMcpSheet(
    allSkills: List<AgentSkill>,
    mcpServers: List<McpServerConfig>,
    mcpConnectionStates: Map<String, McpConnectionState>,
    mcpToolCounts: Map<String, Int> = emptyMap(),
    pinnedMentionIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onToggleSkill: (String, Boolean) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onTogglePin: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val activeSkillsCount = remember(allSkills) { allSkills.count { it.isEnabled } }
    val activeMcpCount = remember(mcpServers) { mcpServers.count { it.isEnabled } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 头部标题与统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_capabilities),
                        style = RuntimeTheme.typography.headlineSmall,
                        color = RuntimeTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.chat_capabilities_description),
                        style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = RuntimeTheme.colorScheme.primaryContainer,
                    shape = RuntimeTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.chat_capabilities_enabled, activeSkillsCount + activeMcpCount),
                        style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = RuntimeTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // 分段标签页
            top.yukonga.miuix.kmp.basic.TabRow(
                tabs = listOf(
                    stringResource(R.string.chat_skills_count, activeSkillsCount, allSkills.size),
                    stringResource(R.string.chat_mcp_count, activeMcpCount, mcpServers.size),
                ),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            // 列表内容展示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
            ) {
                if (selectedTab == 0) {
                    // Skills 列表
                    if (allSkills.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.chat_no_skills),
                                style = RuntimeTheme.typography.bodyMedium,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(allSkills.size, key = { allSkills[it].id }) { index ->
                                val skill = allSkills[index]
                                val isPinned = skill.id in pinnedMentionIds || skill.name.lowercase() in pinnedMentionIds
                                RuntimeCard(
                                    containerColor = if (skill.isEnabled) RuntimeTheme.colorScheme.surfaceContainerHigh
                                    else RuntimeTheme.colorScheme.surfaceContainer,
                                    shape = RuntimeTheme.shapes.large, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            FlowRow(
                                                itemVerticalAlignment = Alignment.CenterVertically, verticalArrangement = Arrangement.spacedBy(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(
                                                    text = skill.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    style = RuntimeTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Medium,
                                                    ),
                                                    color = RuntimeTheme.colorScheme.onSurface,
                                                )
                                                Surface(
                                                    color = RuntimeTheme.colorScheme.secondaryContainer,
                                                    shape = RuntimeTheme.shapes.small,
                                                ) {
                                                    Text(
                                                        text = skill.category,
                                                        style = RuntimeTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.Medium,
                                                        ),
                                                        color = RuntimeTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    )
                                                }
                                            }
                                            if (skill.description.isNotBlank()) {
                                                Text(
                                                    text = skill.description,
                                                    style = RuntimeTheme.typography.bodyMedium,
                                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 2.dp),
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                        ) {
                                            Surface(
                                                shape = RuntimeTheme.shapes.medium,
                                                color = if (isPinned) RuntimeTheme.colorScheme.primaryContainer else RuntimeTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .clickable { onTogglePin(skill.id) },
                                            ) {
                                                Text(
                                                    if (isPinned) stringResource(R.string.chat_pinned_on) else stringResource(R.string.chat_pinned_off),
                                                    style = RuntimeTheme.typography.labelMedium.copy(
                                                        fontWeight = if (isPinned) FontWeight.Medium else FontWeight.Normal,
                                                    ),
                                                    color = if (isPinned) RuntimeTheme.colorScheme.onPrimaryContainer else RuntimeTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                )
                                            }

                                            Switch(
                                                checked = skill.isEnabled,
                                                onCheckedChange = { onToggleSkill(skill.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = RuntimeTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = RuntimeTheme.colorScheme.primary,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // MCP 插件列表
                    if (mcpServers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.chat_no_mcp),
                                style = RuntimeTheme.typography.bodyMedium,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(mcpServers.size, key = { mcpServers[it].id }) { index ->
                                val server = mcpServers[index]
                                val isPinned = server.id in pinnedMentionIds || server.name.lowercase() in pinnedMentionIds
                                val endpointDesc = if (server.command.isNotBlank()) server.command else server.serverUrl
                                McpServerRow(
                                    name = server.name,
                                    subtitle = listOf(server.transportType.name, endpointDesc)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    enabled = server.isEnabled,
                                    connectionState = mcpConnectionStates[server.id] ?: McpConnectionState.UNKNOWN,
                                    toolCount = mcpToolCounts[server.id],
                                    bottomActions = {
                                        Surface(
                                            shape = RuntimeTheme.shapes.medium,
                                            color = if (isPinned) RuntimeTheme.colorScheme.tertiaryContainer else RuntimeTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .clickable { onTogglePin(server.id) },
                                        ) {
                                            Text(
                                                if (isPinned) stringResource(R.string.chat_pinned_on) else stringResource(R.string.chat_pinned_off),
                                                style = RuntimeTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isPinned) FontWeight.Medium else FontWeight.Normal,
                                                ),
                                                color = if (isPinned) RuntimeTheme.colorScheme.onTertiaryContainer else RuntimeTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            )
                                        }

                                        Switch(
                                            checked = server.isEnabled,
                                            onCheckedChange = { onToggleMcpServer(server.id, it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = RuntimeTheme.colorScheme.onPrimary,
                                                checkedTrackColor = RuntimeTheme.colorScheme.primary,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 底部操作区：直达管理
            Surface(
                color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                shape = RuntimeTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSettings),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Settings,
                            modifier = Modifier.size(16.dp),
                            tint = RuntimeTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.chat_manage_capabilities),
                            style = RuntimeTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.primary,
                        )
                    }
                    RuntimeIcon(
                        name = RuntimeIconName.ChevronRight,
                        modifier = Modifier.size(14.dp),
                        tint = RuntimeTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * ⚡ 思考/推理强度悬浮调节面板（ChatGPT 同款平滑胶囊滑块）
 */
@Composable

internal fun ReasoningEffortSlider(
    currentMode: String?,
    currentEffort: String?,
    onSelect: (mode: String?, effort: String?) -> Unit,
    onClose: () -> Unit,
) {
    val levels = listOf(
        Triple(stringResource(R.string.chat_reasoning_disabled), stringResource(R.string.chat_reasoning_disabled_description), "disabled" to null),
        Triple(stringResource(R.string.chat_reasoning_low), stringResource(R.string.chat_reasoning_low_description), "enabled" to "low"),
        Triple(stringResource(R.string.chat_reasoning_medium), stringResource(R.string.chat_reasoning_medium_description), "enabled" to "medium"),
        Triple(stringResource(R.string.chat_reasoning_high), stringResource(R.string.chat_reasoning_high_description), "enabled" to "high"),
        Triple(stringResource(R.string.chat_reasoning_extreme), stringResource(R.string.chat_reasoning_extreme_description), "enabled" to "max"),
    )

    val currentIndex = when {
        currentMode == "disabled" -> 0
        currentMode == "enabled" && currentEffort == "low" -> 1
        currentMode == "enabled" && currentEffort == "medium" -> 2
        currentMode == "enabled" && currentEffort == "high" -> 3
        currentMode == "enabled" && (currentEffort == "extreme" || currentEffort == "max") -> 4
        else -> 2 // 默认中推理
    }

    val haptics = LocalHapticFeedback.current
    var sliderValue by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    // 拖动过程中跟随滑块预览档位（英雄图/文案即时变化），松手后才提交
    LaunchedEffect(currentIndex) { sliderValue = currentIndex.toFloat() }
    LaunchedEffect(Unit) {
        snapshotFlow { sliderValue.roundToInt() }
            .drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    var selectedIndex by remember(currentIndex) { mutableIntStateOf(currentIndex) }
    val activeLevel = levels.getOrElse(selectedIndex) { levels[2] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "思考强度",
                    style = RuntimeTheme.typography.labelLarge,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = activeLevel.first,
                    style = RuntimeTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                    color = RuntimeTheme.colorScheme.onSurface,
                )
                Text(
                    text = activeLevel.second,
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            ReasoningHero(index = selectedIndex)
            IconButton(
                onClick = onClose,
                contentDescription = stringResource(R.string.chat_close),
            ) {
                RuntimeIcon(RuntimeIconName.Close, Modifier.size(16.dp), RuntimeTheme.colorScheme.outline)
            }
        }

        // 步进滑杆：粗短轨道 + 竖条拇指
        Slider(
            value = sliderValue,
            valueRange = 0f..(levels.size - 1).toFloat(),
            steps = levels.size - 2,
            onValueChange = {
                sliderValue = it
                selectedIndex = it.roundToInt().coerceIn(0, levels.size - 1)
            },
            onValueChangeFinished = {
                val snapped = sliderValue.roundToInt().coerceIn(0, levels.size - 1)
                sliderValue = snapped.toFloat()
                selectedIndex = snapped
                val (mode, effort) = levels[snapped].third
                onSelect(mode, effort)
            },
            modifier = Modifier.fillMaxWidth(),

        )

        // 💡 说明提示：必须模型支持才生效
        Surface(
            color = RuntimeTheme.colorScheme.surfaceContainer,
            shape = RuntimeTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(
                    name = RuntimeIconName.Info,
                    modifier = Modifier.size(15.dp),
                    tint = RuntimeTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.chat_reasoning_model_support_notice),
                    style = RuntimeTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReasoningHero(index: Int, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(
        targetValue = if (index == 0) RuntimeTheme.colorScheme.surfaceContainerHighest else RuntimeTheme.colorScheme.primaryContainer,
        animationSpec = RuntimeTheme.motionScheme.defaultEffectsSpec(),
    )
    top.yukonga.miuix.kmp.basic.Surface(
        modifier = modifier.size(72.dp),
        shape = RuntimeTheme.shapes.extraExtraLarge,
        color = containerColor,
        contentColor = RuntimeTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RuntimeIcon(if (index == 0) RuntimeIconName.Brain else RuntimeIconName.Sparkles, Modifier.size(28.dp))
        }
    }
}

/**
 * 审批模式选择底部弹层（完全访问 / 询问 / 每次审批）。
 */
@Composable
internal fun ApprovalModeSheet(
    currentApprovalMode: ApprovalMode,
    onSelect: (ApprovalMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.chat_menu_approval_mode),
                    style = RuntimeTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.chat_close),
                ) {
                    RuntimeIcon(RuntimeIconName.Close, Modifier.size(20.dp))
                }
            }

            Text(
                text = stringResource(R.string.chat_approval_mode_hint),
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )

            listOf(
                Triple(ApprovalMode.REQUEST, stringResource(R.string.chat_approval_request_title), stringResource(R.string.chat_approval_request_description)),
                Triple(ApprovalMode.ASSISTED, stringResource(R.string.chat_approval_assisted_title), stringResource(R.string.chat_approval_assisted_description)),
                Triple(ApprovalMode.FULL_ACCESS, stringResource(R.string.chat_approval_full_access_title), stringResource(R.string.chat_approval_full_access_description)),
            ).forEach { (mode, title, desc) ->
                val isSelected = mode == currentApprovalMode
                Surface(
                    onClick = { onSelect(mode) },
                    shape = RuntimeTheme.shapes.medium,
                    color = if (isSelected) RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else RuntimeTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RuntimeIcon(
                            name = if (isSelected) RuntimeIconName.Check else RuntimeIconName.Shield,
                            modifier = Modifier.size(22.dp),
                            tint = if (isSelected) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (isSelected) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = desc,
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 运行意图选择底部弹层（构建 / 规划）。
 *
 * 与审批权限模式正交：本层决定「这一轮动手还是只看」，审批模式决定「要不要问」。
 */
@Composable
internal fun RunModeSheet(
    currentRunMode: RunMode,
    onSelect: (RunMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.chat_menu_run_mode),
                    style = RuntimeTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.chat_close),
                ) {
                    RuntimeIcon(RuntimeIconName.Close, Modifier.size(20.dp))
                }
            }

            Text(
                text = stringResource(R.string.chat_run_mode_hint),
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )

            listOf(
                Triple(RunMode.BUILD, stringResource(R.string.chat_run_mode_build_title), stringResource(R.string.chat_run_mode_build_description)),
                Triple(RunMode.PLAN, stringResource(R.string.chat_run_mode_plan_title), stringResource(R.string.chat_run_mode_plan_description)),
            ).forEach { (mode, title, desc) ->
                val isSelected = mode == currentRunMode
                Surface(
                    onClick = { onSelect(mode) },
                    shape = RuntimeTheme.shapes.medium,
                    color = if (isSelected) RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else RuntimeTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RuntimeIcon(
                            name = if (isSelected) RuntimeIconName.Check else runModeIcon(mode),
                            modifier = Modifier.size(22.dp),
                            tint = if (isSelected) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (isSelected) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = desc,
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 运行意图图标：构建=写代码，规划=只读检索。 */
internal fun runModeIcon(mode: RunMode): RuntimeIconName = when (mode) {
    RunMode.BUILD -> RuntimeIconName.Code
    RunMode.PLAN -> RuntimeIconName.Search
}

