package top.wkbin.taixu.ui.chat.artifact

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import top.wkbin.taixu.ui.miuix.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.theme.semanticColors
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import top.wkbin.taixu.ui.chat.MarkdownText
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.SyntaxHighlighter
import top.wkbin.taixu.ui.components.RuntimeActionRow
enum class ArtifactViewMode {
    PREVIEW,
    CODE,
    EDIT,
}

/**
 * 太墟产物交付与全功能预览浮层 (Artifact Preview Sheet)
 *
 * 1. 支持 Markdown 富文本解析与渲染；
 * 2. 支持全语种代码语法高亮与行号显示；
 * 3. 支持无缝切换即时就地编辑（Edit Mode）；
 * 4. 提供一键复制、Android 原生系统分享与导出通道。
 */
@Composable
fun ArtifactPreviewSheet(
    title: String,
    content: String,
    relativePath: String = "",
    workspace: String = "",
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onSaveContent: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val extension = remember(relativePath, title) {
        val target = if (relativePath.isNotEmpty()) relativePath else title
        target.substringAfterLast('.', "").lowercase()
    }

    val isDark = isSystemInDarkTheme()
    var zoomScale by remember { mutableFloatStateOf(1f) }

    val defaultFileName = remember(relativePath, title, extension) {
        val raw = if (relativePath.isNotEmpty()) relativePath.substringAfterLast('/') else title
        if (raw.isNotBlank()) raw else "artifact.${if (extension.isNotBlank()) extension else "txt"}"
    }

    val mimeType = remember(extension) {
        when (extension) {
            "md", "markdown" -> "text/markdown"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "txt", "kt", "java", "py", "c", "cpp", "h", "rs", "go", "js", "ts", "sh", "yaml", "yml", "gradle" -> "text/plain"
            else -> "text/plain"
        }
    }

    val isMarkdown = extension == "md" || extension == "markdown"
    var viewMode by remember {
        mutableStateOf(if (isMarkdown) ArtifactViewMode.PREVIEW else ArtifactViewMode.CODE)
    }

    var editableContent by remember(content) { mutableStateOf(content) }
    val isDirty = editableContent != content

    // SAF 外部目录保存 / 导出文档选择器
    val exportDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument(mimeType),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(editableContent.toByteArray(Charsets.UTF_8))
                    output.flush()
                } ?: error("无法打开目标文件流")
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.chat_artifact_export_success), Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, context.getString(R.string.chat_artifact_export_failed, err.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val contentBg = when {
        viewMode == ArtifactViewMode.PREVIEW -> RuntimeTheme.colorScheme.surface
        isDark -> Color(0xFF090D14)
        else -> RuntimeTheme.colorScheme.surfaceContainerLowest
    }
    val codeTextColor = if (isDark) Color(0xFFDCE6F5) else RuntimeTheme.colorScheme.onSurface
    val lineNumColor = if (isDark) Color.White.copy(alpha = 0.35f) else RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RuntimeTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 680.dp),
        ) {
            // 1. 顶栏标题与模式切换
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = RuntimeTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // 左侧图标与文件名
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RuntimeTheme.shapes.chip,
                            color = RuntimeTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RuntimeIcon(
                                    name = when {
                                        isMarkdown -> RuntimeIconName.Document
                                        extension in setOf("kt", "java", "py", "c", "cpp", "rs", "go", "js", "ts") -> RuntimeIconName.Code
                                        else -> RuntimeIconName.File
                                    },
                                    modifier = Modifier.size(18.dp),
                                    tint = RuntimeTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title.ifEmpty { relativePath.substringAfterLast('/') },
                                    style = RuntimeTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isDirty) {
                                    Text(
                                        text = " *",
                                        color = semanticColors().warning,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp,
                                    )
                                }
                            }
                            if (relativePath.isNotEmpty()) {
                                Text(
                                    text = if (workspace.isNotEmpty()) "$workspace/$relativePath" else relativePath,
                                    style = RuntimeTheme.typography.bodySmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // 右侧模式切换胶囊
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isMarkdown) {
                            ModeChip(
                                label = stringResource(R.string.chat_artifact_mode_preview),
                                selected = viewMode == ArtifactViewMode.PREVIEW,
                                onClick = { viewMode = ArtifactViewMode.PREVIEW },
                            )
                        }
                        ModeChip(
                            label = stringResource(R.string.chat_artifact_mode_code),
                            selected = viewMode == ArtifactViewMode.CODE,
                            onClick = { viewMode = ArtifactViewMode.CODE },
                        )
                        if (onSaveContent != null) {
                            ModeChip(
                                label = stringResource(R.string.chat_artifact_mode_edit),
                                selected = viewMode == ArtifactViewMode.EDIT,
                                onClick = { viewMode = ArtifactViewMode.EDIT },
                            )
                        }
                        RuntimeIconButton(
                            onClick = onDismiss,
                            contentDescription = stringResource(R.string.chat_close),
                        ) {
                            RuntimeIcon(RuntimeIconName.Close, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. 主体内容区（支持双指捏合手势 Pinch-to-zoom 缩放）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(contentBg)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.7f, 2.5f)
                        }
                    },
            ) {
                when (viewMode) {
                    ArtifactViewMode.PREVIEW -> {
                        // Markdown 渲染模式
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    transformOrigin = TransformOrigin(0f, 0f)
                                },
                        ) {
                            MarkdownText(
                                markdown = editableContent,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    ArtifactViewMode.CODE -> {
                        // 语法高亮代码模式（支持浅色/暗黑主题自适应 + 手势缩放）
                        val highlighted = remember(editableContent, extension, isDark) {
                            SyntaxHighlighter.highlight(editableContent, extension, isDark = isDark)
                        }
                        val lines = remember(editableContent) { editableContent.lines() }
                        val codeFontSize = (12 * zoomScale).sp
                        val codeLineHeight = (18 * zoomScale).sp

                        SelectionContainer {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp),
                            ) {
                                // 行号栏
                                Column(
                                    modifier = Modifier.padding(end = 12.dp),
                                    horizontalAlignment = Alignment.End,
                                ) {
                                    lines.indices.forEach { idx ->
                                        Text(
                                            text = "${idx + 1}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = codeFontSize,
                                            lineHeight = codeLineHeight,
                                            color = lineNumColor,
                                        )
                                    }
                                }

                                // 语法高亮正文
                                Text(
                                    text = highlighted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = codeFontSize,
                                    lineHeight = codeLineHeight,
                                    color = codeTextColor,
                                )
                            }
                        }
                    }

                    ArtifactViewMode.EDIT -> {
                        // 就地编辑模式
                        val editFontSize = (13 * zoomScale).sp
                        val editLineHeight = (19 * zoomScale).sp

                        BasicTextField(
                            value = editableContent,
                            onValueChange = { editableContent = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = editFontSize,
                                lineHeight = editLineHeight,
                                color = codeTextColor,
                            ),
                            cursorBrush = SolidColor(RuntimeTheme.colorScheme.primary),
                        )
                    }
                }
            }

            HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3. 底栏操作通道与缩放微调
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RuntimeActionRow {
                    // 复制全文
                    RuntimeOutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText(title, editableContent))
                            Toast.makeText(context, context.getString(R.string.chat_artifact_copied), Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        RuntimeIcon(RuntimeIconName.Copy, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.chat_artifact_copy))
                    }

                    // 系统分享
                    RuntimeOutlinedButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, editableContent)
                                putExtra(Intent.EXTRA_TITLE, title)
                                type = "text/plain"
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, context.getString(R.string.chat_artifact_share_chooser, title)),
                            )
                        },
                    ) {
                        RuntimeIcon(RuntimeIconName.OpenInNew, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.chat_artifact_share))
                    }

                    // 导出到外部目录 (SAF)
                    RuntimeOutlinedButton(
                        onClick = {
                            exportDocumentLauncher.launch(defaultFileName)
                        },
                    ) {
                        RuntimeIcon(RuntimeIconName.Download, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.chat_artifact_export))
                    }

                    // 保存/保存修改
                    if (viewMode == ArtifactViewMode.EDIT && onSaveContent != null) {
                        RuntimeButton(
                            onClick = {
                                onSaveContent(editableContent)
                                Toast.makeText(context, context.getString(R.string.chat_artifact_saved), Toast.LENGTH_SHORT).show()
                                viewMode = if (isMarkdown) ArtifactViewMode.PREVIEW else ArtifactViewMode.CODE
                            },
                            enabled = isDirty,
                        ) {
                            RuntimeIcon(RuntimeIconName.Check, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.chat_artifact_save))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 手动缩放微调控制器
                    Surface(
                        shape = RuntimeTheme.shapes.chip,
                        color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clip(RuntimeTheme.shapes.chip),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RuntimeTheme.shapes.tiny)
                                    .clickable { zoomScale = (zoomScale - 0.15f).coerceIn(0.7f, 2.5f) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("-", style = RuntimeTheme.typography.titleMedium)
                            }

                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                style = RuntimeTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                ),
                                color = if (zoomScale != 1f) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RuntimeTheme.shapes.tiny)
                                    .clickable { zoomScale = 1f }
                                    .heightIn(min = 48.dp)
                                    .wrapContentHeight()
                                    .padding(horizontal = 12.dp),
                            )

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RuntimeTheme.shapes.tiny)
                                    .clickable { zoomScale = (zoomScale + 0.15f).coerceIn(0.7f, 2.5f) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+", style = RuntimeTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RuntimeTheme.shapes.small,
        color = if (selected) RuntimeTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, RuntimeTheme.colorScheme.primary) else null,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RuntimeTheme.shapes.small)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = RuntimeTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
            color = if (selected) RuntimeTheme.colorScheme.onPrimaryContainer else RuntimeTheme.colorScheme.onSurfaceVariant,
        )
    }
}
