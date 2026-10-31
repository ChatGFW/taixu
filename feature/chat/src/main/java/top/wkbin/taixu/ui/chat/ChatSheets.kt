package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.material3.Slider
import androidx.compose.material3.toPath
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpSize
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt
import androidx.compose.material3.ExperimentalMaterial3Api
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import top.wkbin.taixu.ui.components.RuntimeTab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.McpServerRow
import top.wkbin.taixu.ui.theme.semanticColors
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.core.model.McpConnectionState
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.model.McpServerConfig

/** 底部弹层：能力挂载面板、推理强度滑块、审批模式选择。 */
@OptIn(ExperimentalMaterial3Api::class)
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.chat_capabilities_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.chat_capabilities_enabled, activeSkillsCount + activeMcpCount),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // 分段标签页
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(MaterialTheme.shapes.large),
            ) {
                RuntimeTab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.chat_skills_count, activeSkillsCount, allSkills.size),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    },
                )
                RuntimeTab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.chat_mcp_count, activeMcpCount, mcpServers.size),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    },
                )
            }

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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    containerColor = if (skill.isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = MaterialTheme.shapes.large, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
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
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = MaterialTheme.shapes.small,
                                                ) {
                                                    Text(
                                                        text = skill.category,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    )
                                                }
                                            }
                                            if (skill.description.isNotBlank()) {
                                                Text(
                                                    text = skill.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                shape = MaterialTheme.shapes.medium,
                                                color = if (isPinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .clickable { onTogglePin(skill.id) },
                                            ) {
                                                Text(
                                                    if (isPinned) stringResource(R.string.chat_pinned_on) else stringResource(R.string.chat_pinned_off),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Normal,
                                                    ),
                                                    color = if (isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                )
                                            }

                                            Switch(
                                                checked = skill.isEnabled,
                                                onCheckedChange = { onToggleSkill(skill.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            shape = MaterialTheme.shapes.medium,
                                            color = if (isPinned) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .clickable { onTogglePin(server.id) },
                                        ) {
                                            Text(
                                                if (isPinned) stringResource(R.string.chat_pinned_on) else stringResource(R.string.chat_pinned_off),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Normal,
                                                ),
                                                color = if (isPinned) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            )
                                        }

                                        Switch(
                                            checked = server.isEnabled,
                                            onCheckedChange = { onToggleMcpServer(server.id, it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large,
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
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.chat_manage_capabilities),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    RuntimeIcon(
                        name = RuntimeIconName.ChevronRight,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
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
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    val sliderState = remember {
        androidx.compose.material3.SliderState(
            value = currentIndex.toFloat(),
            trackRange = 0f..(levels.size - 1).toFloat(),
            steps = levels.size - 2,
        )
    }
    // 拖动过程中跟随滑块预览档位（英雄图/文案即时变化），松手后才提交
    LaunchedEffect(currentIndex) { sliderState.value = currentIndex.toFloat() }
    LaunchedEffect(sliderState) {
        snapshotFlow { sliderState.value.roundToInt() }
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = activeLevel.first,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = activeLevel.second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ReasoningHero(index = selectedIndex)
            IconButton(
                onClick = onClose,
                contentDescription = stringResource(R.string.chat_close),
            ) {
                RuntimeIcon(RuntimeIconName.Close, Modifier.size(16.dp), MaterialTheme.colorScheme.outline)
            }
        }

        // 步进滑杆：粗短轨道 + 竖条拇指（对齐 rikkahub ReasoningPicker）
        Slider(
            state = sliderState,
            onValueChange = {
                sliderState.value = it
                selectedIndex = it.roundToInt().coerceIn(0, levels.size - 1)
            },
            onValueChangeFinished = {
                val snapped = sliderState.value.roundToInt().coerceIn(0, levels.size - 1)
                sliderState.value = snapped.toFloat()
                selectedIndex = snapped
                val (mode, effort) = levels[snapped].third
                onSelect(mode, effort)
            },
            modifier = Modifier.fillMaxWidth(),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = remember { MutableInteractionSource() },
                    isVertical = false,
                    thumbSize = DpSize(4.dp, 52.dp),
                )
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    trackCornerSize = 12.dp,
                    modifier = Modifier.height(40.dp),
                )
            },
        )

        // 💡 说明提示：必须模型支持才生效
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp),
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
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.chat_reasoning_model_support_notice),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 档位 → MaterialShapes 形状：档位越高形状越「激烈」，Morph 连续过渡（参考 rikkahub PickerHero）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
private fun reasoningLevelShape(index: Int): androidx.graphics.shapes.RoundedPolygon = when (index) {
    0 -> androidx.compose.material3.MaterialShapes.Circle
    1 -> androidx.compose.material3.MaterialShapes.Cookie4Sided
    2 -> androidx.compose.material3.MaterialShapes.Cookie6Sided
    3 -> androidx.compose.material3.MaterialShapes.Cookie9Sided
    else -> androidx.compose.material3.MaterialShapes.SoftBurst
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReasoningHero(index: Int, modifier: Modifier = Modifier) {
    val shapes = remember { (0..4).map { reasoningLevelShape(it) } }
    val morphs = remember(shapes) { shapes.zipWithNext { from, to -> Morph(from, to) } }
    val position by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            index == 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
            index <= 2 -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            index == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
            index <= 2 -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )

    Box(
        modifier = modifier
            .size(72.dp)
            .drawBehind {
                // 弹簧会过冲，position 可能略微越界
                val segment = position.toInt().coerceIn(0, morphs.lastIndex)
                val morphPath = Path()
                morphs[segment].toPath(
                    progress = (position - segment).coerceIn(0f, 1f),
                    path = morphPath,
                )
                withTransform({
                    rotate(position * 30f)
                    // MaterialShapes 归一化到 1x1，按实际尺寸放大
                    scale(size.width, size.height, pivot = Offset.Zero)
                }) {
                    drawPath(morphPath, containerColor)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val iconEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val iconSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        AnimatedContent(
            targetState = index == 0,
            transitionSpec = {
                (fadeIn(animationSpec = iconEffectsSpec) +
                    scaleIn(animationSpec = iconSpatialSpec, initialScale = 0.6f)) togetherWith
                    (fadeOut(animationSpec = iconEffectsSpec) +
                        scaleOut(animationSpec = iconSpatialSpec, targetScale = 0.6f))
            },
            label = "reasoning-hero-icon",
        ) { disabled ->
            RuntimeIcon(
                name = if (disabled) RuntimeIconName.Brain else RuntimeIconName.Sparkles,
                modifier = Modifier.size(28.dp),
                tint = contentColor,
            )
        }
    }
}

/**
 * 审批模式选择底部弹层（完全访问 / 询问 / 每次审批）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApprovalModeSheet(
    currentApprovalMode: ApprovalMode,
    onSelect: (ApprovalMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            listOf(
                Triple(ApprovalMode.REQUEST, stringResource(R.string.chat_approval_request_title), stringResource(R.string.chat_approval_request_description)),
                Triple(ApprovalMode.ASSISTED, stringResource(R.string.chat_approval_assisted_title), stringResource(R.string.chat_approval_assisted_description)),
                Triple(ApprovalMode.FULL_ACCESS, stringResource(R.string.chat_approval_full_access_title), stringResource(R.string.chat_approval_full_access_description)),
            ).forEach { (mode, title, desc) ->
                val isSelected = mode == currentApprovalMode
                Surface(
                    onClick = { onSelect(mode) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
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
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunModeSheet(
    currentRunMode: RunMode,
    onSelect: (RunMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            listOf(
                Triple(RunMode.BUILD, stringResource(R.string.chat_run_mode_build_title), stringResource(R.string.chat_run_mode_build_description)),
                Triple(RunMode.PLAN, stringResource(R.string.chat_run_mode_plan_title), stringResource(R.string.chat_run_mode_plan_description)),
            ).forEach { (mode, title, desc) ->
                val isSelected = mode == currentRunMode
                Surface(
                    onClick = { onSelect(mode) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
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
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

