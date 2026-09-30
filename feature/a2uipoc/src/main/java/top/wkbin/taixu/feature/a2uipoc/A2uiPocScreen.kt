package top.wkbin.taixu.feature.a2uipoc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import top.wkbin.taixu.harness.A2uiSurfaceBus
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeTextButton

/**
 * A2UI PoC 的两个复用入口：
 * - [A2uiPocSurfaceCard]：聊天流内嵌卡片（render_surface 工具结果 → 原生界面）；
 * - [A2uiPocScreen]：独立演示屏（注入示例 / 清空 / 浏览最近载荷），供入口挂载。
 *
 * 协议消息只在内容变化时处理一次（LaunchedEffect 按 messagesJson 键控）；
 * 渲染失败或 surface 尚无组件数据时回退展示原始 JSON，保证 PoC 始终可观测。
 */

@Composable
fun A2uiPocSurfaceCard(call: ToolCall, modifier: Modifier = Modifier) {
    val surfaceId = call.args["surfaceId"]?.toString()?.trim('"', ' ')?.trim().orEmpty()
    val messagesJson = call.args["messages"]?.toString()?.trim('"', ' ')?.trim().orEmpty()
    A2uiSurfaceHost(surfaceId = surfaceId, messagesJson = messagesJson, modifier = modifier)
}

@Composable
fun A2uiSurfaceHost(
    surfaceId: String,
    messagesJson: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    var renderError by remember(messagesJson) { mutableStateOf<String?>(null) }
    LaunchedEffect(messagesJson) {
        renderError = if (messagesJson.isBlank()) "载荷为空" else TaiXuA2uiRenderer.processMessages(messagesJson)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val rendered = TaiXuA2uiRenderer.SurfaceView(surfaceId = surfaceId, modifier = Modifier.fillMaxWidth())
        if (!rendered) {
            A2uiFallbackCard(renderError = renderError, messagesJson = messagesJson)
        }
    }
}

@Composable
private fun A2uiFallbackCard(renderError: String?, messagesJson: String, modifier: Modifier = Modifier) {
    RuntimeCard(
        containerColor = if (renderError != null) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (renderError != null) {
                Text(
                    renderError,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                messagesJson.take(1200).ifBlank { "（空载荷）" },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 10,
            )
        }
    }
}

@Composable
fun A2uiPocScreen(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val surfaces by A2uiSurfaceBus.surfaces.collectAsStateWithLifecycle()
    var lastInjectedAt by rememberSaveable { mutableStateOf(0L) }
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                RuntimeTextButton(onClick = onBack) { Text(stringResource(R.string.fa2ui_back)) }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                stringResource(R.string.fa2ui_poc_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            RuntimeTextButton(onClick = {
                lastInjectedAt = System.currentTimeMillis()
                A2uiSurfaceBus.publishFromTool(sampleToolArgs(lastInjectedAt))
            }) { Text(stringResource(R.string.fa2ui_inject_sample)) }
            Spacer(Modifier.width(8.dp))
            RuntimeTextButton(onClick = { A2uiSurfaceBus.clear() }) {
                Text(stringResource(R.string.fa2ui_clear))
            }
        }
        if (surfaces.isEmpty()) {
            RuntimeCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.fa2ui_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(surfaces, key = { it.surfaceId }) { payload ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                payload.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            A2uiSurfaceHost(
                                surfaceId = payload.surfaceId,
                                messagesJson = payload.messagesJson,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/** 演示屏注入示例时模拟一次 render_surface 工具调用（与真实工具共用同一校验/发布链路）。 */
private fun sampleToolArgs(timestamp: Long) = kotlinx.serialization.json.buildJsonObject {
    put("surfaceId", kotlinx.serialization.json.JsonPrimitive("demo-$timestamp"))
    put("title", kotlinx.serialization.json.JsonPrimitive("内置示例"))
    put("messages", kotlinx.serialization.json.JsonPrimitive(A2uiSurfaceBus.sampleMessagesJson()))
}
