package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.wkbin.taixu.core.tools.ProviderProtocol
import top.wkbin.taixu.core.tools.AgentConnectionThreeWayProbe
import top.wkbin.taixu.core.tools.ConnectionThreeWayResult
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton

/**
 * 模型档案「三项连接测试」面板（借鉴多厂商聚合客户端）：在既有单项探活之外，
 * 独立验证「非流式补全 / 流式补全 / 工具调用」——中转站常见的半残形态
 * （能聊天不能流、不支持 tools）只有分项探测才暴露。状态自持，不经 ViewModel。
 */
@Composable
internal fun ModelConnectionThreeWayPanel(
    baseUrl: String,
    model: String,
    apiKey: String,
    useResponsesApi: Boolean,
    protocol: ProviderProtocol,
    providerName: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val probe = koinInject<AgentConnectionThreeWayProbe>()
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ConnectionThreeWayResult?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RuntimeOutlinedButton(
            onClick = {
                running = true
                result = null
                scope.launch {
                    result = probe.test(baseUrl, model, apiKey.takeIf { it.isNotBlank() }, useResponsesApi, protocol, providerName)
                    running = false
                }
            },
            enabled = enabled && !running && baseUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            if (running) {
                RuntimeCircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("三项测试中…")
            } else {
                RuntimeIcon(RuntimeIconName.Checklist, Modifier.size(16.dp), RuntimeTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("三项连接测试（非流式 / 流式 / 工具调用）")
            }
        }

        result?.let { r ->
            Surface(
                shape = RuntimeTheme.shapes.medium,
                color = RuntimeTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProbeRow("非流式补全", r.plain.success, r.plain.detail)
                    ProbeRow("流式补全", r.stream.success, r.stream.detail)
                    ProbeRow("工具调用", r.tool.success, r.tool.detail)
                }
            }
        }
    }
}


/**
 * 单项探活、发现错误、三项分项探针与结果条。
 * 自 [ModelEditorContent] 抽出，避免顶格文件越过尺寸棘轮。
 */
@Composable
internal fun ModelConnectionTestBlock(
    url: String,
    model: String,
    apiKey: String,
    useResponsesApi: Boolean,
    protocol: ProviderProtocol,
    providerName: String,
    providerId: String,
    testing: Boolean,
    discoveryError: String?,
    testResult: String?,
    onTest: (String, String, String, Boolean, String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RuntimeOutlinedButton(
            onClick = { onTest(url, model, apiKey, useResponsesApi, providerId) },
            enabled = !testing && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (testing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuntimeCircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在测试…")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuntimeIcon(RuntimeIconName.Play, Modifier.size(16.dp), RuntimeTheme.colorScheme.primary)
                    Text("测试连接")
                }
            }
        }

        discoveryError?.let {
            Surface(
                shape = RuntimeTheme.shapes.medium,
                color = RuntimeTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Alert, Modifier.size(16.dp), tint = RuntimeTheme.colorScheme.error)
                    Text(
                        text = it,
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        ModelConnectionThreeWayPanel(
            baseUrl = url,
            model = model,
            apiKey = apiKey,
            useResponsesApi = useResponsesApi,
            protocol = protocol,
            providerName = providerName,
            enabled = true,
        )
        testResult?.let { resultMsg ->
            val isSuccess = resultMsg == "连接成功"
            Surface(
                shape = RuntimeTheme.shapes.medium,
                color = if (isSuccess) successStatusColor().copy(alpha = 0.15f) else RuntimeTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RuntimeIcon(
                        name = if (isSuccess) RuntimeIconName.Check else RuntimeIconName.Alert,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSuccess) successStatusColor() else RuntimeTheme.colorScheme.error,
                    )
                    Text(
                        text = resultMsg,
                        style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isSuccess) successStatusColor() else RuntimeTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbeRow(label: String, success: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RuntimeIcon(
            name = if (success) RuntimeIconName.Check else RuntimeIconName.Alert,
            modifier = Modifier.size(14.dp),
            tint = if (success) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.error,
        )
        Text(label, style = RuntimeTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        Text(
            detail,
            style = RuntimeTheme.typography.bodySmall,
            color = if (success) RuntimeTheme.colorScheme.onSurfaceVariant else RuntimeTheme.colorScheme.error,
            maxLines = 2,
        )
    }
}
