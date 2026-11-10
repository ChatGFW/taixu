package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginCenterActions(
    onDownload: () -> Unit, onImport: () -> Unit, onBuiltinFeatures: () -> Unit, onWebPlugins: () -> Unit,
    enabled: Boolean = true,
) {
    RuntimeCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.plugin_center_intro), style = RuntimeTheme.typography.bodyMedium)
            RuntimeActionRow() {
                RuntimeButton(onClick = onDownload, enabled = enabled) {
                    RuntimeIcon(RuntimeIconName.Download, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.plugin_center_download), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RuntimeFilledTonalButton(onClick = onImport, enabled = enabled) {
                    RuntimeIcon(RuntimeIconName.FolderOpen, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.plugin_center_import), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RuntimeActionRow() {
                RuntimeTextButton(onClick = onBuiltinFeatures) { Text(stringResource(R.string.plugin_center_builtin)) }
                RuntimeTextButton(onClick = onWebPlugins, enabled = enabled) { Text(stringResource(R.string.plugin_center_web)) }
            }
        }
    }
}
