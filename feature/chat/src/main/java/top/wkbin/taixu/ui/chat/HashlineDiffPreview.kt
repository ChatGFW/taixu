package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.harness.text.HashlineEditArgs

private val OpsPreviewText = Color(0xFF7EE787)
private val OpsPreviewBg = Color(0xFF070B12)

/** 工具 diff 共用的滚动行容器，避免每处再写一遍底色与高度。 */
@Composable
internal fun DiffScrollColumn(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06090F), RuntimeTheme.shapes.extraSmall)
            .heightIn(max = 320.dp)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
internal fun HashlineOpsLabel(args: JsonObject) {
    val label = remember(args) { HashlineEditArgs.opsLabel(args) } ?: return
    Text(
        text = label,
        style = RuntimeTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        color = RuntimeTheme.colorScheme.primary,
    )
}

@Composable
internal fun HashlineOpsColumn(lines: List<String>) {
    DiffScrollColumn {
        itemsIndexed(lines, key = { index, _ -> "op-$index" }) { _, line ->
            DiffLine(text = line, background = OpsPreviewBg, foreground = OpsPreviewText)
        }
    }
}

/** Hashline ops 粗预览（无 unified diff / legacy 文本时用）；每行一条，截断 content。 */
internal fun hashlineOpsPreviewLines(args: JsonObject, maxLines: Int = 32): List<String> {
    val ops = args["ops"] as? JsonArray ?: return emptyList()
    return ops.take(maxLines).mapIndexed { index, element ->
        val obj = element as? JsonObject ?: return@mapIndexed "ops[$index] (invalid)"
        val op = obj["op"]?.jsonPrimitive?.content.orEmpty().ifBlank { "?" }
        val start = obj["start"]?.jsonPrimitive?.content
        val end = obj["end"]?.jsonPrimitive?.content
        val after = obj["after"]?.jsonPrimitive?.content
        val content = obj["content"]?.jsonPrimitive?.content
        buildString {
            append("· $op")
            when {
                after != null -> append(" after=$after")
                start != null && end != null && end != start -> append(" $start..$end")
                start != null -> append(" $start")
            }
            if (!content.isNullOrEmpty()) {
                val oneLine = content.lineSequence().first().take(80)
                append(" → ").append(oneLine)
                if (content.contains('\n') || content.length > 80) append("…")
            }
        }
    }
}
