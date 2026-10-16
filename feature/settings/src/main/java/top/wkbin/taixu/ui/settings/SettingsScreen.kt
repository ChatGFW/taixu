package top.wkbin.taixu.ui.settings
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.settings.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.LocalPluginSettingsContent

/**
 * 太墟 · 乾坤配置 (TaiXu Settings & Models)
 */
@Composable
fun SettingsScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenAgentEco: () -> Unit,
    onOpenLinuxEnv: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSystemDev: () -> Unit,
    onOpenAboutCommunity: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenFeatureHub: () -> Unit = {},
    viewModel: SettingsViewModel? = previewOrNull { koinViewModel() }, onBack: () -> Unit = { onNavigate(MainDestination.Agent) },
) {
    val pluginSettings = LocalPluginSettingsContent.current
    val models by viewModel?.models?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val developer by viewModel?.developerMode?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val themeMode by viewModel?.themeMode?.collectAsStateWithLifecycle() ?: previewStateOf("")
    val skills by viewModel?.allSkills?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val effectiveExecutionMode by viewModel?.effectiveExecutionMode?.collectAsStateWithLifecycle() ?: previewStateOf(top.wkbin.taixu.core.model.ExecutionMode.PROOT)
    val installedDistros by viewModel?.installedDistros?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val terminalFontSize by viewModel?.terminalFontSize?.collectAsStateWithLifecycle() ?: previewStateOf(14)

    val themeLabel = when (themeMode) {
        "light" -> "浅色"
        "dark" -> "曜石"
        else -> "跟随系统"
    }

    val appVersionName = rememberAppVersion()


    PersonalizationScaffold(stringResource(R.string.personal_settings_title), onBack) {
        item("search") {
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSearch,
                shape = RoundedCornerShape(50),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentPadding = PaddingValues(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RuntimeIcon(RuntimeIconName.Search, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.personal_search), style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item("appearance") {
            SettingsCategoryCard(
                icon = RuntimeIconName.Palette,
                title = stringResource(R.string.personal_appearance_title),
                subtitle = stringResource(R.string.settings_nav_appearance_summary),
                value = "$themeLabel · ${terminalFontSize}sp",
                onClick = onOpenAppearance,
            )
        }
        item("environment") {
            PersonalizationSection(stringResource(R.string.personal_environment)) {
                SettingsCategoryCard(RuntimeIconName.Brain, stringResource(R.string.personal_agents_title),
                    stringResource(R.string.settings_nav_agents_summary),
                    if (models.isEmpty()) "未配置模型" else "${models.size} 个模型 · ${skills.count { it.isEnabled }} 技能",
                    first = true, last = false, onClick = onOpenAgentEco)
                SettingsCategoryCard(RuntimeIconName.Server, stringResource(R.string.personal_linux_title),
                    stringResource(R.string.settings_nav_linux_summary),
                    "${installedDistros.size} 套系统 · ${effectiveExecutionMode.shortLabel}",
                    first = false, last = false, onClick = onOpenLinuxEnv)
                SettingsCategoryCard(RuntimeIconName.Sparkles, "特色功能",
                    stringResource(R.string.settings_nav_features_summary),
                    "5 项功能", first = false, last = true, onClick = onOpenFeatureHub)
            }
        }
        if (pluginSettings != null) item("plugins") { pluginSettings() }
        item("application") {
            PersonalizationSection(stringResource(R.string.personal_application)) {
                SettingsCategoryCard(RuntimeIconName.Admin, stringResource(R.string.personal_diagnostics_title),
                    stringResource(R.string.settings_nav_diagnostics_summary),
                    if (developer) "诊断模式已开启" else "运行平稳",
                    first = true, last = false, onClick = onOpenSystemDev)
                SettingsCategoryCard(RuntimeIconName.Community, stringResource(R.string.settings_nav_about_title),
                    stringResource(R.string.settings_nav_about_summary),
                    if (appVersionName == "unknown") "版本号未知 · 稳定版" else "v$appVersionName 稳定版",
                    first = false, last = true, onClick = onOpenAboutCommunity)
            }
        }
        item("backup") { LocalBackupSettingsCard() }
    }
}

@Composable
private fun SettingsCategoryCard(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    value: String,
    first: Boolean = true,
    last: Boolean = true,
    onClick: () -> Unit,
) {
    PreferenceSurface(first = first, last = last) {
        SettingsRow(icon = icon, title = title, subtitle = subtitle, value = value, onClick = onClick)
    }
}
