package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeSettingsScaffold
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun WorkshopEnvironmentSettingsScreen(
    onBack: () -> Unit,
    viewModel: WorkshopSettingsViewModel? = previewOrNull { koinViewModel() },
    sdkViewModel: WorkshopSdkViewModel? = previewOrNull { koinViewModel() },
) {
    val draft by viewModel?.draft?.collectAsStateWithLifecycle() ?: previewStateOf(WorkshopEnvironmentDraft())
    val detected by viewModel?.detectedToolchains?.collectAsStateWithLifecycle() ?: previewStateOf(DetectedToolchains())

    val sdkState by sdkViewModel?.state?.collectAsStateWithLifecycle() ?: previewStateOf(WorkshopSdkState())
    RuntimeSettingsScaffold(stringResource(R.string.workshop_env_title), onBack) {
            workshopSdkItems(sdkState, draft.androidSdkPath, draft.aapt2Path, sdkViewModel) { sdk ->
                viewModel?.let { it.update(it.draft.value.copy(androidSdkPath = sdk.path, aapt2Path = sdk.aapt2Path)) }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionHeader(stringResource(R.string.workshop_env_paths_title), stringResource(R.string.workshop_env_paths_desc))
                    }
                    RuntimeOutlinedButton(
                        onClick = { viewModel?.rescanToolchains() },
                        enabled = !detected.isScanning,
                    ) {
                        Text(if (detected.isScanning) stringResource(R.string.workshop_env_rescanning) else stringResource(R.string.workshop_env_rescan))
                    }
                }
            }
            item {
                RuntimeCard(shape = RuntimeTheme.shapes.extraLarge) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToolchainPathDropdownField("Gradle", draft.gradlePath, detected.gradleOptions) { viewModel?.update(draft.copy(gradlePath = it)) }
                        ToolchainPathDropdownField("Java / JDK", draft.javaPath, detected.javaOptions) { viewModel?.update(draft.copy(javaPath = it)) }
                        ToolchainPathDropdownField("Android NDK", draft.ndkPath, detected.ndkOptions) { viewModel?.update(draft.copy(ndkPath = it)) }
                        ToolchainPathDropdownField("AAPT2", draft.aapt2Path, detected.aapt2Options) { viewModel?.update(draft.copy(aapt2Path = it)) }
                        PathField("Android SDK", draft.androidSdkPath) { viewModel?.update(draft.copy(androidSdkPath = it)) }
                        PathField("Flutter SDK", draft.flutterSdkPath) { viewModel?.update(draft.copy(flutterSdkPath = it)) }
                        PathField("CMake", draft.cmakePath) { viewModel?.update(draft.copy(cmakePath = it)) }
                        PathField("Ninja", draft.ninjaPath) { viewModel?.update(draft.copy(ninjaPath = it)) }
                        PathField("Gradle 缓存", draft.gradleUserHome) { viewModel?.update(draft.copy(gradleUserHome = it)) }
                        PathField("Flutter Pub 缓存", draft.pubCache) { viewModel?.update(draft.copy(pubCache = it)) }
                        PathField("Android 工具目录 / ADB", draft.toolDir) { viewModel?.update(draft.copy(toolDir = it)) }
                    }
                }
            }
            item {
                RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                    RuntimeOutlinedButton(onClick = { viewModel?.resetEnvironment() }, modifier = Modifier) { Text(stringResource(R.string.workshop_action_reset)) }
                    RuntimeButton(onClick = { viewModel?.saveEnvironment() }, modifier = Modifier) { Text(stringResource(R.string.workshop_action_save)) }
                }
            }
    }
}

@Composable
private fun ToolchainPathDropdownField(
    label: String,
    value: String,
    options: List<ToolchainOption>,
    onValueChange: (String) -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.path == value }.takeIf { it >= 0 } ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (options.isEmpty()) {
            Text(label, style = RuntimeTheme.typography.labelMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
        } else {
            RuntimeDropdownField(
                title = label,
                items = options.map { option ->
                    option.label + if (option.isDetected) " · ${stringResource(R.string.workshop_env_detected)}" else ""
                },
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index -> onValueChange(options[index].path) },
                summary = value,
            )
        }

        RuntimeFilledTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            textStyle = RuntimeTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun PathField(label: String, value: String, onValueChange: (String) -> Unit) {
    RuntimeFilledTextField(
        value = value, onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        labelText = label, singleLine = true,
        textStyle = RuntimeTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
}
