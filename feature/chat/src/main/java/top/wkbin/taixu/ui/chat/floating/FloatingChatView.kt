package top.wkbin.taixu.ui.chat.floating

import androidx.compose.runtime.*


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import top.wkbin.taixu.ui.chat.safeScrollToLastItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.ModelSwitchEvent
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName

internal const val EDGE_HANDLE_TOUCH_WIDTH_DP = 32
internal const val EDGE_HANDLE_TOUCH_HEIGHT_DP = 40

/**
 * 智枢桌面悬浮小窗视图（支持胶囊态与面板态即时切换，去除多余遮罩与阴影瑕疵）。
 */
@Composable
fun FloatingChatView(
    sessionTitle: String?,
    messages: List<HarnessMessage>,
    running: Boolean,
    thinkingLive: Boolean,
    isExpanded: Boolean,
    isEdgeHidden: Boolean,
    dockedOnLeft: Boolean,
    onToggleExpanded: () -> Unit,
    onReveal: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onStopAgent: () -> Unit,
    onRestoreApp: () -> Unit,
    onClose: () -> Unit,
) {
    if (isExpanded) {
        FloatingChatPanel(
            sessionTitle = sessionTitle,
            messages = messages,
            running = running,
            thinkingLive = thinkingLive,
            onCollapse = onToggleExpanded,
            onDragBy = onDragBy,
            onDragEnd = onDragEnd,
            onSendPrompt = onSendPrompt,
            onStopAgent = onStopAgent,
            onRestoreApp = onRestoreApp,
            onClose = onClose,
        )
    } else if (isEdgeHidden) {
        FloatingChatEdgeHandle(
            running = running,
            dockedOnLeft = dockedOnLeft,
            onReveal = onReveal,
            onDragBy = onDragBy,
            onDragEnd = onDragEnd,
        )
    } else {
        FloatingChatCapsule(
            sessionTitle = sessionTitle,
            running = running,
            thinkingLive = thinkingLive,
            onExpand = onToggleExpanded,
            onDragBy = onDragBy,
            onDragEnd = onDragEnd,
        )
    }
}

/** 贴边后仅保留可触摸的小入口，不占用胶囊原有的整块屏幕区域。 */
@Composable
private fun FloatingChatEdgeHandle(
    running: Boolean,
    dockedOnLeft: Boolean,
    onReveal: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val shape = if (dockedOnLeft) {
        RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
    } else {
        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    }
    val revealLabel = stringResource(R.string.chat_floating_reveal)
    Box(
        modifier = Modifier
            .size(width = EDGE_HANDLE_TOUCH_WIDTH_DP.dp, height = EDGE_HANDLE_TOUCH_HEIGHT_DP.dp)
            .semantics {
                contentDescription = revealLabel
                onClick(label = revealLabel) {
                    onReveal()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val change = awaitTouchSlopOrCancellation(down.id) { c, overSlop ->
                        c.consume()
                        onDragBy(overSlop.x, overSlop.y)
                    }
                    if (change != null) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerChange = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointerChange.pressed) {
                                pointerChange.consume()
                                break
                            }
                            val dragAmount = pointerChange.positionChange()
                            pointerChange.consume()
                            onDragBy(dragAmount.x, dragAmount.y)
                        }
                        onDragEnd()
                    } else {
                        onReveal()
                    }
                }
            },
        contentAlignment = if (dockedOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier.size(width = 20.dp, height = 32.dp),
            color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (running) RuntimeTheme.colorScheme.primary.copy(alpha = 0.6f)
                else RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (running) {
                    RuntimeCircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.6.dp,
                        color = RuntimeTheme.colorScheme.primary,
                    )
                } else {
                    RuntimeIcon(
                        RuntimeIconName.Brain,
                        modifier = Modifier.size(12.dp),
                        tint = RuntimeTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * 胶囊态 (Mini Capsule Mode)
 * 支持滑动拖拽移动、松手自动边缘吸附与轻触点击展开，无多余阴影遮罩
 */
@Composable
private fun FloatingChatCapsule(
    sessionTitle: String?,
    running: Boolean,
    thinkingLive: Boolean,
    onExpand: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RuntimeTheme.shapes.extraLargeIncreased)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val change = awaitTouchSlopOrCancellation(down.id) { c, overSlop ->
                        c.consume()
                        onDragBy(overSlop.x, overSlop.y)
                    }
                    if (change != null) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerChange = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointerChange.pressed) {
                                pointerChange.consume()
                                break
                            }
                            val dragAmount = pointerChange.positionChange()
                            pointerChange.consume()
                            onDragBy(dragAmount.x, dragAmount.y)
                        }
                        onDragEnd()
                    } else {
                        onExpand()
                    }
                }
            },
        color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
        shape = RuntimeTheme.shapes.extraLargeIncreased,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (running) RuntimeTheme.colorScheme.primary.copy(alpha = 0.6f)
            else RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (running) RuntimeTheme.colorScheme.primaryContainer
                        else RuntimeTheme.colorScheme.surfaceContainerHigh,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (running) {
                    RuntimeCircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = RuntimeTheme.colorScheme.primary,
                    )
                } else {
                    RuntimeIcon(
                        RuntimeIconName.Brain,
                        modifier = Modifier.size(14.dp),
                        tint = RuntimeTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = when {
                    thinkingLive -> stringResource(R.string.chat_floating_thinking)
                    running -> stringResource(R.string.chat_floating_running)
                    else -> sessionTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_title)
                },
                style = RuntimeTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = RuntimeTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp),
            )
        }
    }
}

/**
 * 展开面板态 (Expanded Floating Chat Panel)
 */
@Composable
private fun FloatingChatPanel(
    sessionTitle: String?,
    messages: List<HarnessMessage>,
    running: Boolean,
    thinkingLive: Boolean,
    onCollapse: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onStopAgent: () -> Unit,
    onRestoreApp: () -> Unit,
    onClose: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val displayMessages = remember(messages) {
        messages.filter { it !is ToolResult }.takeLast(20)
    }

    LaunchedEffect(displayMessages.size, running) {
        if (displayMessages.isNotEmpty()) {
            listState.safeScrollToLastItem(animated = true)
        }
    }

    Surface(
        modifier = Modifier
            .width(310.dp)
            .height(410.dp)
            .clip(RuntimeTheme.shapes.medium),
        color = RuntimeTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        shape = RuntimeTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 顶栏（左侧标题区域支持按住拖拽移动，右侧操作按钮独立响应点击）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragBy(dragAmount.x, dragAmount.y)
                                },
                            )
                        },
                ) {
                    RuntimeIcon(
                        RuntimeIconName.Brain,
                        modifier = Modifier.size(16.dp),
                        tint = RuntimeTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = sessionTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_title),
                        style = RuntimeTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = RuntimeTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (running) {
                        RuntimeIconButton(
                            onClick = onStopAgent,
                            modifier = Modifier.size(24.dp),
                            contentDescription = stringResource(R.string.chat_floating_stop),
                        ) {
                            RuntimeIcon(
                                RuntimeIconName.Stop,
                                modifier = Modifier.size(14.dp),
                                tint = RuntimeTheme.colorScheme.error,
                            )
                        }
                    }
                    RuntimeIconButton(
                        onClick = onRestoreApp,
                        modifier = Modifier.size(24.dp),
                        contentDescription = stringResource(R.string.chat_floating_restore_app),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.OpenInNew,
                            modifier = Modifier.size(14.dp),
                            tint = RuntimeTheme.colorScheme.onSurface,
                        )
                    }
                    RuntimeIconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(24.dp),
                        contentDescription = "收起为胶囊",
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.ChevronDown,
                            modifier = Modifier.size(14.dp),
                            tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RuntimeIconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp),
                        contentDescription = stringResource(R.string.chat_floating_close),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Close,
                            modifier = Modifier.size(14.dp),
                            tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 消息流列表
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = RuntimeTheme.colorScheme.surfaceContainer,
                shape = RuntimeTheme.shapes.small,
            ) {
                if (displayMessages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_floating_idle),
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(displayMessages, key = { it.id }) { msg ->
                            when (msg) {
                                is UserMessage -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        Surface(
                                            color = RuntimeTheme.colorScheme.primaryContainer,
                                            shape = RuntimeTheme.shapes.small,
                                            modifier = Modifier.widthIn(max = 240.dp),
                                        ) {
                                            Text(
                                                text = msg.text,
                                                style = RuntimeTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = RuntimeTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            )
                                        }
                                    }
                                }
                                is AssistantText -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (!msg.reasoning.isNullOrBlank()) {
                                            Surface(
                                                color = RuntimeTheme.colorScheme.surfaceContainerLow,
                                                shape = RuntimeTheme.shapes.extraSmall,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    RuntimeIcon(
                                                        RuntimeIconName.Brain,
                                                        Modifier.size(11.dp),
                                                        RuntimeTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Text(
                                                        text = msg.reasoning.orEmpty(),
                                                        style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                        if (msg.text.isNotBlank()) {
                                            Surface(
                                                color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                                                shape = RuntimeTheme.shapes.small,
                                                modifier = Modifier.widthIn(max = 260.dp),
                                            ) {
                                                Text(
                                                    text = msg.text,
                                                    style = RuntimeTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                    color = RuntimeTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                is ToolCall -> {
                                    Surface(
                                        color = RuntimeTheme.colorScheme.surfaceContainerLow,
                                        shape = RuntimeTheme.shapes.extraSmall,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RuntimeIcon(
                                                RuntimeIconName.Code,
                                                Modifier.size(11.dp),
                                                RuntimeTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "${msg.rawToolName ?: msg.tool.name} ${msg.args}",
                                                style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = RuntimeTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                is ModelSwitchEvent -> {
                                    Text(
                                        text = stringResource(
                                            R.string.chat_model_switch_to_fmt,
                                            msg.toLabel,
                                        ),
                                        style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }

            // 底部快速输入栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RuntimeTheme.shapes.medium)
                    .background(RuntimeTheme.colorScheme.surfaceContainer)
                    .border(
                        0.8.dp,
                        RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RuntimeTheme.shapes.medium,
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_floating_input_hint),
                            style = RuntimeTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = RuntimeTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = RuntimeTheme.colorScheme.onSurface,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(RuntimeTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    val prompt = inputText.trim()
                                    inputText = ""
                                    onSendPrompt(prompt)
                                }
                            },
                        ),
                    )
                }

                // 迷你实心圆钮，与主 composer 发送按钮同族
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape)
                        .background(if (inputText.isNotBlank()) RuntimeTheme.colorScheme.primary
                            else RuntimeTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = inputText.isNotBlank()) {
                            onSendPrompt(inputText.trim()); inputText = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(RuntimeIconName.ArrowUp, Modifier.size(14.dp),
                        tint = if (inputText.isNotBlank()) RuntimeTheme.colorScheme.onPrimary
                        else RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}
