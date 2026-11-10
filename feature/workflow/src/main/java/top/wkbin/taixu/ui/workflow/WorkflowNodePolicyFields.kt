package top.wkbin.taixu.ui.workflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.workflow.FailurePolicy
import top.wkbin.taixu.core.model.workflow.WorkflowNodeType

@Composable
internal fun WorkflowNodePolicyFields(type: WorkflowNodeType, timeout: String, onTimeout: (String) -> Unit,
    policy: FailurePolicy, onPolicy: (FailurePolicy) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (type != WorkflowNodeType.HUMAN_APPROVAL) RuntimeFilledTextField(
            value = timeout, onValueChange = { onTimeout(it.filter(Char::isDigit).take(4)) },
            labelText = "超时（秒）", supportingText = { Text("1–3600 秒") },
            isError = timeout.toIntOrNull()?.let { it in 1..3600 } != true,
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        RuntimeDropdownField(
            title = "失败策略",
            items = FailurePolicy.entries.map { it.label() },
            selectedIndex = FailurePolicy.entries.indexOf(policy),
            onSelectedIndexChange = { index -> FailurePolicy.entries.getOrNull(index)?.let(onPolicy) },
            modifier = Modifier.weight(1.2f),
        )
    }
}

private fun FailurePolicy.label() = when (this) {
    FailurePolicy.ABORT -> "终止流程"
    FailurePolicy.CONTINUE -> "继续执行"
    FailurePolicy.RETRY_ONCE -> "重试一次"
    FailurePolicy.ASK_USER -> "询问用户"
}
