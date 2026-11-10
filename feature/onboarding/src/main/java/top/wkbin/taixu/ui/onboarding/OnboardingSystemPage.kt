package top.wkbin.taixu.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import top.wkbin.taixu.ui.components.previewStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeWindowDropdownPreference
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.feature.onboarding.R
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.DistributionCatalog
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.runtimeContentPadding
import androidx.compose.foundation.layout.heightIn
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeTextButton

private data class SetupOption(val id: String, val label: String)

private val distributionOptions = DistributionCatalog.supported.map { SetupOption(it.id, it.displayWithVersion) }

@Composable
internal fun SystemSetupPage(viewModel: OnboardingViewModel?, modifier: Modifier) {
    val distribution by viewModel?.distribution?.collectAsStateWithLifecycle() ?: previewStateOf("ubuntu")
    val mirror by viewModel?.mirror?.collectAsStateWithLifecycle() ?: previewStateOf("auto")
    val state by viewModel?.runtimeState?.collectAsStateWithLifecycle() ?: previewStateOf(RuntimeState.Ready)
    val importing by viewModel?.isImporting?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val importError by viewModel?.importError?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val installing = state is RuntimeState.Initializing || importing
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let({ arg0 -> viewModel?.importArchive(arg0) }) }
    val mirrorOptions = listOf(
        SetupOption("auto", stringResource(R.string.onboarding_mirror_auto)),
        SetupOption("official", stringResource(R.string.onboarding_mirror_official)),
        SetupOption("china", stringResource(R.string.onboarding_mirror_china)),
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = runtimeContentPadding(horizontal = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OnboardingPageHeading(
                title = stringResource(R.string.onboarding_title),
                description = stringResource(R.string.onboarding_description),
            )
        }
        item {
            RuntimeCard(contentPadding = PaddingValues(0.dp)) {
                SetupDropdown(stringResource(R.string.onboarding_distribution), distribution, distributionOptions,
                    enabled = !installing) { viewModel?.selectDistribution(it) }
                SetupDropdown(stringResource(R.string.onboarding_mirror), mirror, mirrorOptions,
                    enabled = !installing) { viewModel?.selectMirror(it) }
            }
        }
        if (state is RuntimeState.Initializing) {
            val progress = state as RuntimeState.Initializing
            item {
                RuntimeCard(
                    containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
                    shape = RuntimeTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = progress.step,
                                style = RuntimeTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${(progress.progress * 100).toInt()}%",
                                style = RuntimeTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = RuntimeTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        RuntimeLinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RuntimeTheme.shapes.small),
                            color = RuntimeTheme.colorScheme.primary,
                            wavy = true,
                        )
                        progress.detail?.let {
                            Text(
                                text = it,
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        (state as? RuntimeState.Error)?.let { error ->
            item {
                Text(
                    error.throwable.message ?: stringResource(R.string.onboarding_initialization_failed),
                    color = RuntimeTheme.colorScheme.error,
                    style = RuntimeTheme.typography.bodyMedium,
                )
            }
        }
        item {
            RuntimeButton(
                onClick = { viewModel?.install() },
                enabled = !installing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    stringResource(
                        if (state is RuntimeState.Error) R.string.onboarding_retry
                        else if (installing) R.string.onboarding_preparing
                        else R.string.onboarding_initialize,
                    ),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        importError?.let { error ->
            item {
                Text(
                    error,
                    color = RuntimeTheme.colorScheme.error,
                    style = RuntimeTheme.typography.bodyMedium,
                )
            }
        }
        item {
            RuntimeTextButton(
                onClick = { importLauncher.launch(arrayOf("application/gzip", "application/x-gzip", "application/x-tar", "application/x-xz", "application/octet-stream")) },
                enabled = !installing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_import))
            }
        }
        item {
            Text(
                stringResource(R.string.onboarding_import_hint),
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state is RuntimeState.Error) {
            item {
                RuntimeTextButton(
                    onClick = { viewModel?.retryReady() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_recheck))
                }
            }
        }
    }
}

@Composable
private fun SetupDropdown(
    label: String,
    selectedId: String,
    options: List<SetupOption>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    top.wkbin.taixu.ui.components.RuntimeWindowDropdownPreference(
        items = options.map { it.label },
        selectedIndex = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
        title = label,
        enabled = enabled,
        onSelectedIndexChange = { onSelected(options[it].id) },
    )
}
