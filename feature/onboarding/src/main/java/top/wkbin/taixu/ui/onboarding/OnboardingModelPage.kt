package top.wkbin.taixu.ui.onboarding

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import top.wkbin.taixu.ui.miuix.*
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.tools.AgentProviderDefinition
import top.wkbin.taixu.feature.onboarding.R
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.components.RuntimeDropdownField

@Composable
internal fun ModelSetupPage(viewModel: OnboardingViewModel?, modifier: Modifier) {
    val provider by viewModel?.modelProvider?.collectAsStateWithLifecycle() ?: previewStateOf("OpenAI 兼容接口")
    val model by viewModel?.modelId?.collectAsStateWithLifecycle() ?: previewStateOf("")
    val baseUrl by viewModel?.baseUrl?.collectAsStateWithLifecycle() ?: previewStateOf("")
    val apiKey by viewModel?.apiKey?.collectAsStateWithLifecycle() ?: previewStateOf("")
    val discovered by viewModel?.discoveredModels?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val discovering by viewModel?.discoveringModels?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val discoveryError by viewModel?.modelDiscoveryError?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val providers = viewModel?.providerCatalog ?: listOf(AgentProviderDefinition("custom", provider, "", group = top.wkbin.taixu.core.tools.ProviderGroup.CUSTOM))
    val selectedProvider = providers.firstOrNull { it.name == provider } ?: providers.first()

    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var keyRevealed by remember { mutableStateOf(false) }

    val candidateModels = remember(discovered, selectedProvider) {
        (discovered + selectedProvider.recommendedModels).distinct().filter { it.isNotBlank() }
    }

    LazyColumn(
        modifier = modifier.imePadding(),
        contentPadding = runtimeContentPadding(horizontal = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.onboarding_connect_model), Modifier.weight(1f),
                        style = RuntimeTheme.typography.headlineMedium)
                    RuntimeFilledTonalButton(onClick = { showImportDialog = true }) {
                        Text(stringResource(R.string.model_setup_import))
                    }
                }
                Text(stringResource(R.string.onboarding_connect_model_description), style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            RuntimeDropdownField(
                title = "模型服务商",
                items = providers.map { it.name },
                selectedIndex = providers.indexOfFirst { it.id == selectedProvider.id }.coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    providers.getOrNull(index)?.let { option -> viewModel?.selectProvider(option.id) }
                },
                summary = stringResource(
                    if (selectedProvider.protocol == top.wkbin.taixu.core.tools.ProviderProtocol.ANTHROPIC) {
                        R.string.model_setup_protocol_anthropic
                    } else {
                        R.string.model_setup_protocol_openai
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            RuntimeSectionTitle(stringResource(R.string.model_setup_connection))
            RuntimeCard(contentPadding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RuntimeFilledTextField(
                        value = baseUrl, onValueChange = { viewModel?.setBaseUrl(it) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        labelText = stringResource(R.string.model_setup_url),
                        placeholder = { Text("https://api.openai.com/v1") },
                        trailingIcon = {
                            RuntimeIconButton(onClick = { viewModel?.discoverModels() }, enabled = !discovering && baseUrl.isNotBlank(),
                                contentDescription = stringResource(R.string.model_setup_discover)) {
                                if (discovering) RuntimeCircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(20.dp))
                            }
                        },
                    )
                    RuntimeFilledTextField(
                        value = apiKey, onValueChange = { viewModel?.setApiKey(it) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        labelText = stringResource(R.string.model_setup_key), placeholder = { Text("sk-…") },
                        visualTransformation = if (keyRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            if (apiKey.isNotEmpty()) RuntimeIconButton(onClick = { keyRevealed = !keyRevealed },
                                contentDescription = stringResource(if (keyRevealed) R.string.model_setup_hide_key else R.string.model_setup_show_key)) {
                                RuntimeIcon(if (keyRevealed) RuntimeIconName.VisibilityOff else RuntimeIconName.Visibility, Modifier.size(20.dp))
                            }
                        },
                    )
                }
            }
        }
        item {
            RuntimeSectionTitle(stringResource(R.string.model_setup_model))
            RuntimeCard(contentPadding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RuntimeFilledTextField(
                        value = model, onValueChange = { viewModel?.setModelId(it) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        labelText = "Model ID", placeholder = { Text("gpt-4o / deepseek-chat") },
                    )
                    OnboardingModelCandidates(candidateModels, model) { viewModel?.setModelId(it) }
                }
            }
        }
        discoveryError?.let { err -> item { Text(err, color = RuntimeTheme.colorScheme.error, style = RuntimeTheme.typography.bodySmall) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeButton(onClick = { viewModel?.saveModelAndFinish() }, enabled = model.isNotBlank() && baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.onboarding_enter))
                    Spacer(Modifier.width(8.dp))
                    RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(20.dp))
                }
                RuntimeTextButton(onClick = { viewModel?.skipModel() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_later))
                }
            }
        }
    }

    // JSON 导入对话框
    if (showImportDialog) {
        OnboardingModelImportDialog(
            onDismiss = { showImportDialog = false },
            onImportJson = { jsonStr ->
                val result = viewModel?.importProfileFromJson(jsonStr)
                result?.fold(
                    onSuccess = { profile ->
                        Toast.makeText(context, "成功载入模型配置「${profile.name.ifBlank { profile.model }}」", Toast.LENGTH_SHORT).show()
                        showImportDialog = false
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "解析失败: ${err.message}", Toast.LENGTH_LONG).show()
                    },
                )
            },
        )
    }
}
