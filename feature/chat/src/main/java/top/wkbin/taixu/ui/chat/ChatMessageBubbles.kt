package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeCard
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import androidx.compose.material3.ButtonDefaults
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.CapabilityEvent
import top.wkbin.taixu.harness.SkillSuggestion
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.taixu.harness.ModelSwitchEvent
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.checkpoint.RewindScope
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.GlobalContext
import top.wkbin.taixu.core.common.navigation.AppNavigationTarget
import top.wkbin.taixu.core.common.navigation.GlobalNavigationBus
import top.wkbin.taixu.core.common.translation.TranslationManager
import top.wkbin.taixu.core.common.translation.TranslationModelStatus

/** 消息气泡：用户/助手气泡、思考块、任务计划卡、能力事件卡。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserBubble(
    message: UserMessage,
    knownMentionNames: List<String> = emptyList(),
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCreateBranch: () -> Unit,
    onRewind: (RewindScope) -> Unit = {},
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    // 删除消息为破坏性操作，先经确认对话框
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    // 撤回到此轮：先选择撤回范围（代码/对话/两者）
    var showRewindScopeDialog by rememberSaveable { mutableStateOf(false) }

    val copyText = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_user_request_clipboard), message.text))
        Toast.makeText(context, context.getString(R.string.chat_request_copied), Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 🌟 1. 多模态图片预览卡片
            if (message.imageUrls.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    itemsIndexed(
                        items = message.imageUrls,
                        key = { index, _ -> "${message.id}:$index" },
                    ) { index, imageUrl ->
                        ImageThumbnail(
                            imageUrl = imageUrl,
                            cacheKey = "chat-image-${message.id}-$index",
                            onClick = { showMenu = true },
                        )
                    }
                }
            }

            // 🌟 2. 用户文字气泡（clipBubbleShape 只裁绘制：四角异径的 Surface 会在图层轮廓里
            // 生成 Path，超长消息下半部分触摸判定会因 float 精度失效）
            if (message.text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .clipBubbleShape(RoundedCornerShape(24.dp, 8.dp, 24.dp, 24.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { showMenu = true },
                ) {
                    val mentionColor = MaterialTheme.colorScheme.primary
                    val mentionBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    val annotatedText = remember(message.text, knownMentionNames, mentionColor, mentionBg) {
                        formatMentionText(message.text, knownMentionNames, mentionColor, mentionBg)
                    }
                    SelectionContainer {
                        Text(
                            text = annotatedText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else if (message.imageUrls.isNotEmpty()) {
                // 纯图片消息：显示小菜单按钮
                Surface(
                    onClick = { showMenu = true },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RuntimeIcon(
                            RuntimeIconName.More,
                            Modifier.size(15.dp),
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_copy)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp)) },
                onClick = {
                    showMenu = false
                    copyText()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_edit_resend)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Edit, Modifier.size(16.dp)) },
                onClick = {
                    showMenu = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_create_branch)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.GitBranch, Modifier.size(16.dp)) },
                onClick = {
                    showMenu = false
                    onCreateBranch()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_rewind)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Reverse, Modifier.size(16.dp)) },
                onClick = {
                    showMenu = false
                    showRewindScopeDialog = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    RuntimeIcon(
                        RuntimeIconName.Trash,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    showMenu = false
                    showDeleteConfirm = true
                },
            )
        }
    }

    // 删除消息二次确认
    if (showDeleteConfirm) {
        RuntimeAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.chat_delete_message_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.chat_delete_message_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.chat_confirm_delete), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.chat_cancel)) }
            },
        )
    }

    // 撤回范围选择：仅代码 / 仅对话 / 两者
    if (showRewindScopeDialog) {
        RuntimeAlertDialog(
            onDismissRequest = { showRewindScopeDialog = false },
            title = { Text(stringResource(R.string.chat_rewind_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.chat_rewind_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RewindScopeOption(
                        label = stringResource(R.string.chat_rewind_code),
                        description = stringResource(R.string.chat_rewind_code_desc),
                        icon = RuntimeIconName.Code,
                        onClick = { showRewindScopeDialog = false; onRewind(RewindScope.CODE) },
                    )
                    RewindScopeOption(
                        label = stringResource(R.string.chat_rewind_conversation),
                        description = stringResource(R.string.chat_rewind_conversation_desc),
                        icon = RuntimeIconName.Chat,
                        onClick = { showRewindScopeDialog = false; onRewind(RewindScope.CONVERSATION) },
                    )
                    RewindScopeOption(
                        label = stringResource(R.string.chat_rewind_both),
                        description = stringResource(R.string.chat_rewind_both_desc),
                        icon = RuntimeIconName.Reverse,
                        onClick = { showRewindScopeDialog = false; onRewind(RewindScope.BOTH) },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRewindScopeDialog = false }) { Text(stringResource(R.string.chat_cancel)) }
            },
        )
    }
}

/** 撤回范围选择行：图标 + 标题 + 说明，整行可点。 */
@Composable
private fun RewindScopeOption(
    label: String,
    description: String,
    icon: RuntimeIconName,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuntimeIcon(icon, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 只裁剪绘制内容，不像 [clip] 那样把形状设成图层轮廓。
 *
 * 四个角半径不同时图层轮廓是 Path，Compose 判断触摸点是否落在轮廓内时用 Path 求交，
 * 坐标超过 8192px 后 float 精度不够，求交结果为空——超长用户消息的下半部分就收不到触摸了。
 */
private fun Modifier.clipBubbleShape(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
}

/**
 * 🌟 多模态图片优雅缩略图卡片（支持 Base64 Data URL 与本地文件路径）。
 *
 * Base64 解码不能放在 remember 里：remember 的计算发生在 Compose 主线程，历史大图会
 * 直接阻塞切换智枢的首帧。交给 Coil 后，DataUriFetcher/图片解码在后台执行，并按缩略图
 * 的实际尺寸采样；稳定的 cacheKey 也让 Navigation3 重组页面时直接命中内存缓存。
 */
@Composable
internal fun ImageThumbnail(
    imageUrl: String,
    cacheKey: String,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val thumbnailPx = with(LocalDensity.current) { 130.dp.roundToPx() }
    val request = remember(imageUrl, cacheKey, thumbnailPx) {
        val data: Any = when {
            imageUrl.startsWith("file://") -> File(imageUrl.removePrefix("file://"))
            imageUrl.startsWith("/") -> File(imageUrl)
            else -> imageUrl
        }
        ImageRequest.Builder(context)
            .data(data)
            .size(thumbnailPx, thumbnailPx)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = stringResource(R.string.chat_user_image),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 130.dp, height = 130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp),
            )
            // 与文字气泡一致的点击语义：弹出操作菜单
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
internal fun AssistantBubble(
    message: AssistantText,
    defaultExpanded: Boolean,
    autoTranslate: Boolean = false,
    live: Boolean = false,
    showRegenerate: Boolean = false,
    prominentActions: Boolean = true,
    onRegenerate: () -> Unit = {},
    onCreateBranch: () -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null,
) {
    val reasoning = message.reasoning
    val context = LocalContext.current
    // 生成图判定：live（流式进行中）直接跳过扫描 —— 此时 base64 尚未写完，扫描必然 miss，
    // 却会在每个流式分片对整段（可能数 MB）文本跑一次 contains。改为仅在流结束后的稳定文本上判定，
    // 缓存键也随之从「含 text.length」收敛为「按 message.id」，避免键随每帧变化导致缓存永久失效。
    val generatedImagePayload = remember(message.id, live) {
        if (live) {
            false
        } else {
            val cacheKey = message.id
            generatedImageFlagCache.get(cacheKey)
                ?: message.text.contains("data:image/", ignoreCase = true).also { found ->
                    generatedImageFlagCache.put(cacheKey, found)
                }
        }
    }

    val copyAll = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_ai_response_clipboard), message.text))
        Toast.makeText(context, context.getString(R.string.chat_response_copied), Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!reasoning.isNullOrBlank()) {
            ThinkingBlock(
                id = message.id,
                reasoning = reasoning,
                defaultExpanded = defaultExpanded,
                live = live,
                durationMs = message.reasoningMs,
                autoTranslate = autoTranslate,
                onNavigateToSettings = onNavigateToSettings,
            )
        }

        if (!live && !generatedImagePayload) {
            val planSteps = remember(message.text) { extractTaskPlanSteps(message.text) }
            if (planSteps.size >= 2) {
                TaskPlanCard(steps = planSteps)
            }
        }

        if (message.text.isNotBlank()) ChatReadableMessage {
            if (live) {
                val liveImageDataStart = remember(message.text) {
                    message.text.indexOf("data:image/", ignoreCase = true)
                }
                if (liveImageDataStart >= 0) {
                    val humanPrefix = remember(message.text, liveImageDataStart) {
                        val markdownStart = message.text.lastIndexOf("![", liveImageDataStart)
                            .takeIf { it >= 0 } ?: liveImageDataStart
                        message.text.substring(0, markdownStart).trim().take(8_000)
                    }
                    if (humanPrefix.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = humanPrefix,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(width = 260.dp, height = 180.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text(
                                text = stringResource(R.string.chat_receiving_image),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = message.text.take(MAX_LIVE_TEXT_CHARS),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                MarkdownText(
                    markdown = message.text,
                    modifier = Modifier.fillMaxWidth(),
                    // 缓存键只用 message.id：流式期间每帧 text.length 都会变，把长度编进键会让
                    // 缓存形同失效。大文本 LRU 在 MarkdownText 内用内容指纹校验，同一 id 换正文不会串味。
                    contentCacheKey = "assistant:${message.id}",
                )
            }
        }

        if (!live && message.text.isNotBlank()) {
            AssistantMessageFooter(message, prominentActions, showRegenerate, onRegenerate, onCreateBranch, copyAll)
        }
    }
}

private const val MAX_LIVE_TEXT_CHARS = 64_000
/** Avoid rescanning a multi-megabyte Base64 response whenever its lazy-list item re-enters composition. */
private val generatedImageFlagCache = LruCache<String, Boolean>(64)

@Composable
internal fun ThinkingBlock(
    id: String,
    reasoning: String,
    defaultExpanded: Boolean,
    live: Boolean = false,
    durationMs: Long? = null,
    autoTranslate: Boolean = false,
    onNavigateToSettings: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable(id) { mutableStateOf(defaultExpanded) }
    var showTranslation by rememberSaveable(id) { mutableStateOf(false) }
    var translatedText by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var showMissingModelDialog by rememberSaveable(id) { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val translationManager = remember {
        GlobalContext.getOrNull()
            ?.getOrNull<TranslationManager>()
    }
    val globalNavigationBus = remember {
        GlobalContext.getOrNull()
            ?.getOrNull<GlobalNavigationBus>()
    }
    val fallbackStatusFlow = remember {
        MutableStateFlow<TranslationModelStatus>(
            TranslationModelStatus.Checking
        )
    }
    val translationModelStatus by (translationManager?.status ?: fallbackStatusFlow)
        .collectAsStateWithLifecycle()
    // 流式思考限高视口：长思考固定在 160dp 视口内滚动并跟随尾部；用户拖动即暂停跟随，
    // 可点「跟随」恢复。手势判定与群聊 GroupReasoningPanel 同一模式。
    val liveScroll = rememberScrollState()
    var liveFollowing by remember(id) { mutableStateOf(true) }
    LaunchedEffect(liveScroll) {
        liveScroll.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> liveFollowing = false
                is DragInteraction.Stop, is DragInteraction.Cancel ->
                    liveFollowing = liveScroll.value >= liveScroll.maxValue - 2
                else -> Unit
            }
        }
    }
    LaunchedEffect(liveScroll, expanded) {
        // 监听测量结果而非 token 到达：文本排版可能晚一帧完成。
        snapshotFlow { Triple(liveScroll.maxValue, liveFollowing, liveScroll.isScrollInProgress) }.collect { (maximum, follow, moving) ->
            if (!moving && !follow && liveScroll.value >= maximum - 2) liveFollowing = true
            if (expanded && follow && maximum != Int.MAX_VALUE && !moving) liveScroll.scrollTo(maximum)
        }
    }
    LaunchedEffect(expanded, autoTranslate) {
        if (expanded && autoTranslate && !live && !showTranslation && translatedText == null) {
            if (translationManager?.isReady() == true) {
                isTranslating = true
                val result = translationManager.translate(reasoning)
                if (result.isSuccess) {
                    translatedText = result.getOrNull()
                    showTranslation = true
                }
                isTranslating = false
            }
        }
    }
    val copyToClipboard = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = if (showTranslation && translatedText != null) translatedText else reasoning
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_reasoning_clipboard), textToCopy))
        Toast.makeText(context, context.getString(R.string.chat_reasoning_copied), Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxWidth().then(chatGreetingModifier())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth().heightIn(min = 28.dp).clip(MaterialTheme.shapes.small)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); expanded = !expanded }
                .padding(horizontal = 2.dp, vertical = 4.dp),
        ) {
            ChatProcessIcon(RuntimeIconName.Brain, running = live)

            ChatReasoningLabel(id, live, durationMs, Modifier.weight(1f))

            if (expanded) {
                if (!live) {
                    IconButton(
                        onClick = {
                            val isReady = translationManager?.isReady() == true
                            if (!isReady) {
                                showMissingModelDialog = true
                            } else {
                                if (showTranslation) {
                                    showTranslation = false
                                } else if (translatedText != null) {
                                    showTranslation = true
                                } else {
                                    scope.launch {
                                        isTranslating = true
                                        val result = translationManager.translate(reasoning)
                                        if (result.isSuccess) {
                                            translatedText = result.getOrNull()
                                            showTranslation = true
                                        } else {
                                            Toast.makeText(
                                                context,
                                                result.exceptionOrNull()?.message ?: "离线翻译失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        isTranslating = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(18.dp),
                        contentDescription = if (showTranslation) "查看英文原文" else "离线翻译思考",
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            RuntimeIcon(
                                RuntimeIconName.Globe,
                                Modifier.size(18.dp),
                                tint = if (showTranslation) MaterialTheme.colorScheme.primary
                                       else chatProcessColor(),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = copyToClipboard,
                    modifier = Modifier.size(18.dp),
                    contentDescription = stringResource(R.string.chat_copy_reasoning),
                ) {
                    RuntimeIcon(
                        RuntimeIconName.Copy,
                        Modifier.size(18.dp),
                        chatProcessColor(),
                    )
                }
            }

            RuntimeIcon(
                if (expanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
                Modifier.size(16.dp),
                chatProcessColor(),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ChatProcessContent {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expanded = false
                        }
                        .padding(top = 12.dp, bottom = 4.dp),
                ) {
                    if (showTranslation && translatedText != null) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(bottom = 2.dp),
                            ) {
                                RuntimeIcon(
                                    RuntimeIconName.Globe,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "中文译文 (ML Kit 离线生成)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            MarkdownText(
                                translatedText.orEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (isTranslating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "正在离线翻译思考内容...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = chatProcessColor(),
                                ),
                            )
                        }
                    } else {
                        if (live) {
                            Box(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                                SelectionContainer {
                                    Text(
                                        text = reasoning,
                                        modifier = Modifier.fillMaxWidth().verticalScroll(liveScroll),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = chatProcessColor(),
                                        ),
                                    )
                                }
                                if (!liveFollowing && liveScroll.maxValue > 0) {
                                    TextButton(
                                        onClick = { liveFollowing = true },
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.chat_follow_reasoning),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        } else {
                            MarkdownText(
                                reasoning,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMissingModelDialog) {
        val isDownloading = translationModelStatus is TranslationModelStatus.Downloading
        val downloadingStatus = translationModelStatus as? TranslationModelStatus.Downloading
        RuntimeAlertDialog(
            onDismissRequest = { showMissingModelDialog = false },
            title = {
                Text(
                    text = if (isDownloading) "离线翻译模型下载中" else "未下载离线翻译语种包",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = if (isDownloading && downloadingStatus != null) {
                        "离线翻译语种模型正在后台下载中（${downloadingStatus.stepName}，${downloadingStatus.detailText}）。\n\n下载完成后将自动就绪支持离线翻译，您可前往「智能体设置」查看详细进度。"
                    } else {
                        "本地英语-中文翻译模型（约 60MB）尚未下载。请前往「Agent 设置」完成语种模型下载后方可离线翻译思考流内容。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMissingModelDialog = false
                        if (onNavigateToSettings != null) {
                            onNavigateToSettings()
                        } else {
                            globalNavigationBus?.navigateTo(AppNavigationTarget.AgentSettings)
                        }
                    }
                ) {
                    Text(if (isDownloading) "查看下载进度" else "前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMissingModelDialog = false }) {
                    Text(if (isDownloading) "后台继续" else "取消")
                }
            },
        )
    }
}

/**
 * 技能进化建议卡片（对话后技能沉淀/进化）：
 * 创建新技能 / 更新既有技能 / 忽略，动作完成后由上层隐藏卡片。
 */
@Composable
internal fun SkillSuggestionCard(
    suggestion: SkillSuggestion,
    onCreate: () -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isUpdate = suggestion.action == "update"
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RuntimeIcon(
                    RuntimeIconName.Sparkles,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (isUpdate) "技能进化建议 · 修复既有技能" else "技能进化建议 · 沉淀为新技能",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                suggestion.skillName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suggestion.description.isNotBlank()) {
                Text(
                    suggestion.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (suggestion.reason.isNotBlank()) {
                Text(
                    "依据：" + suggestion.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isUpdate) {
                    OutlinedButton(
                        onClick = onUpdate,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text("更新技能", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    }
                }
                Button(
                    onClick = onCreate,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(if (isUpdate) "另存为新技能" else "创建技能", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("忽略", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
internal fun CapabilityEventCard(event: CapabilityEvent) {
    val isSkill = event.kind == CapabilityEvent.Kind.SKILL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            RuntimeIcon(
                if (isSkill) RuntimeIconName.BookOpen else RuntimeIconName.Extension,
                Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        }
        Text(
            stringResource(
                if (isSkill) R.string.chat_capability_skill_fmt else R.string.chat_capability_mcp_fmt,
                event.name,
            ),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        if (event.details.isNotBlank()) {
            Text(
                "· ${event.details}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun ModelSwitchCard(event: ModelSwitchEvent) {
    val toWindow = formatContextWindow(event.toContextTokens)
    val title = if (event.fromLabel.isBlank()) {
        stringResource(R.string.chat_model_switch_to_fmt, event.toLabel)
    } else {
        stringResource(R.string.chat_model_switch_from_to_fmt, event.fromLabel, event.toLabel)
    }
    val subtitle = when {
        event.compacted && event.foldedMessageCount > 0 ->
            stringResource(R.string.chat_model_switch_compacted_fmt, toWindow, event.foldedMessageCount)
        event.compactionPending ->
            stringResource(R.string.chat_model_switch_pending_fmt, toWindow)
        else -> stringResource(R.string.chat_model_switch_window_fmt, toWindow)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.widthIn(max = 260.dp),
            ) {
                RuntimeIcon(
                    RuntimeIconName.Bot,
                    Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
