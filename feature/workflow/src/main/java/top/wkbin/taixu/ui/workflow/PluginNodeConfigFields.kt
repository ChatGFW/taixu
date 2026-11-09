package top.wkbin.taixu.ui.workflow

import androidx.compose.foundation.layout.fillMaxWidth
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.workflow.R
import top.wkbin.taixu.ui.components.RuntimeFilledTextField

@Composable
internal fun PluginNodeConfigFields(config: MutableMap<String, String>) {
    listOf("plugin" to R.string.plugin_node_id, "extension" to R.string.plugin_node_extension, "arguments" to R.string.plugin_node_arguments).forEach { (key, label) ->
        RuntimeFilledTextField(value = config[key].orEmpty(), onValueChange = { config[key] = it },
            labelText = stringResource(label), minLines = if (key == "arguments") 3 else 1, maxLines = 8, modifier = Modifier.fillMaxWidth())
    }
    Text(stringResource(R.string.plugin_node_scope), style = RuntimeTheme.typography.bodySmall)
}
