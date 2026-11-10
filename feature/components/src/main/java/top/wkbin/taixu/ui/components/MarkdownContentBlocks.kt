package top.wkbin.taixu.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.components.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.markdown.*

@Composable
internal fun CodeBlock(block: MdCodeBlock) {
    val context = LocalContext.current
    val isDark = RuntimeTheme.colorScheme.surface.luminance() < 0.5f

    val copyCode = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.components_md_code_clipboard_label), block.code))
        Toast.makeText(context, context.getString(R.string.components_md_code_copied), Toast.LENGTH_SHORT).show()
    }

    val highlightedText = remember(block.code, block.language, isDark) {
        SyntaxHighlighter.highlight(block.code, block.language, isDark = isDark)
    }

    val containerBg = if (isDark) Color(0xFF0D1117) else Color(0xFFF6F8FA)
    val headerBg = if (isDark) Color(0xFF161B22) else Color(0xFFEAEEF2)
    val borderColor = if (isDark) Color(0xFF30363D) else Color(0xFFD0D7DE)
    val headerTextColor = if (isDark) Color(0xFF8B949E) else Color(0xFF57606A)

    Surface(
        color = containerBg,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // 代码块顶部信息栏：语言徽章 + 复制按钮 (紧凑设计)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 10.dp, vertical = 3.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (block.language.isNotBlank()) block.language.uppercase() else stringResource(R.string.components_md_code_badge),
                    style = RuntimeTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = headerTextColor,
                )
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable(onClick = copyCode),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Copy,
                            Modifier.size(11.dp),
                            tint = headerTextColor,
                        )
                        Text(
                            text = stringResource(R.string.components_md_copy),
                            style = RuntimeTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = headerTextColor,
                        )
                    }
                }
            }

            // 代码高亮显示区 (紧凑字号与间距)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = highlightedText,
                    style = RuntimeTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun TableBlock(table: MdTable) {
    val colCount = (listOf(table.headers) + table.rows).maxOf { it.size }

    // 列宽按「该列所有单元格中最宽者」估算，用固定 dp 宽度绘制。
    //
    // 为什么不用 IntrinsicSize / weight：
    //   旧写法 Box{ Column(horizontalScroll + width(IntrinsicSize.Max)){ Row(fillMaxWidth){ 列 weight(1f) } } }
    //   三者语义冲突——horizontalScroll 下子项获得无限宽约束，IntrinsicSize 依赖固有测量，
    //   而 weight 需要确定的可分配宽度；结果每列被压成 0 宽，界面只剩最左一列
    //   （用户实际观测：表格内容丢失、只剩第一列）。
    //   改为按字符数估算固定宽度：纯算术、确定性 100%，不依赖 Compose 测量机制。
    val colWidths = (0 until colCount).map { c ->
        val widest = (listOf(table.headers) + table.rows)
            .mapNotNull { it.getOrNull(c) }
            .maxOfOrNull { displayWidthOf(it) } ?: 1
        // 每字符约 7.2dp（bodySmall ~12sp 的粗略折算），上下限夹逼避免极窄/极宽列。
        (widest * 7.2f).dp.coerceIn(48.dp, 280.dp)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(RuntimeTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
    ) {
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row {
                (0 until colCount).forEach { c ->
                    InlineText(
                        table.headers.getOrNull(c) ?: "",
                        RuntimeTheme.typography.bodySmall,
                        bold = true,
                        modifier = Modifier
                            .width(colWidths[c])
                            .padding(end = 12.dp),
                    )
                }
            }
            HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant)
            table.rows.forEach { row ->
                Row {
                    (0 until colCount).forEach { c ->
                        InlineText(
                            row.getOrNull(c) ?: "",
                            RuntimeTheme.typography.bodySmall,
                            modifier = Modifier
                                .width(colWidths[c])
                                .padding(end = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 估算字符串的显示宽度（以半角字符为单位）。
 *
 * Markdown 强调标记（`**`、`*`、`` ` ``）不占实际显示宽度，需先剥离；
 * CJK / 全角标点按 2 个单位计，其余按 1。用于表格列宽估算，无需精确字形度量。
 */
private fun displayWidthOf(raw: String): Int {
    val text = raw
        .replace("**", "")
        .replace("`", "")
        .replace("*", "")
    var width = 0
    text.forEach { ch ->
        val code = ch.code
        width += when {
            code in 0x1100..0x115F -> 2                 // 韩文字母
            code in 0x2E80..0xA4CF -> 2                 // CJK 部首/汉字/日文假名
            code in 0xAC00..0xD7A3 -> 2                 // 韩文音节
            code in 0xF900..0xFAFF -> 2                 // CJK 兼容表意
            code in 0xFE30..0xFE6F -> 2                 // CJK 兼容形式
            code in 0xFF00..0xFF60 -> 2                 // 全角 ASCII
            code in 0xFFE0..0xFFE6 -> 2                 // 全角符号
            else -> 1
        }
    }
    return width.coerceAtLeast(1)
}

@Composable
internal fun ListBlock(block: MdList) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (block.ordered) "${index + 1}." else "\u2022",
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                InlineText(item, RuntimeTheme.typography.bodyMedium, modifier = Modifier.padding(end = 0.dp))
            }
        }
    }
}

@Composable
internal fun QuoteBlock(block: MdQuote) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RuntimeTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        block.lines.forEach { line ->
            InlineText(line, RuntimeTheme.typography.bodyMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
        }
    }
}
