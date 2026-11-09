package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import top.wkbin.taixu.ui.components.RuntimeSearchEntry
import top.wkbin.taixu.ui.components.RuntimeUsageNoticeDialog
import top.wkbin.taixu.feature.components.R as ComponentsR
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
    onOpenPluginCenter: () -> Unit = {},
    viewModel: SettingsViewModel? = previewOrNull { koinViewModel() }, onBack: () -> Unit = { onNavigate(MainDestination.Agent) },
) {
    var showUsageNotice by rememberSaveable { mutableStateOf(false) }
    if (showUsageNotice) RuntimeUsageNoticeDialog(onDismiss = { showUsageNotice = false })
    val models by viewModel?.models?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val developer by viewModel?.developerMode?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val themeMode by viewModel?.themeMode?.collectAsStateWithLifecycle() ?: previewStateOf("")
    val skills by viewModel?.allSkills?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val effectiveExecutionMode by viewModel?.effectiveExecutionMode?.collectAsStateWithLifecycle() ?: previewStateOf(top.wkbin.taixu.core.model.ExecutionMode.PROOT)
    val installedDistros by viewModel?.installedDistros?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val themeLabel = when (themeMode) {
        "light" -> "浅色"
        "dark" -> "曜石"
        else -> "跟随系统"
    }

    val appVersionName = rememberAppVersion()


    PersonalizationScaffold(stringResource(R.string.personal_settings_title), onBack) {
        item("search") {
            RuntimeSearchEntry(stringResource(R.string.personal_search), onOpenSearch)
        }
        item("appearance") {
            SettingsCategoryCard(
                icon = RuntimeIconName.Palette,
                title = stringResource(R.string.personal_appearance_title),
                subtitle = stringResource(R.string.settings_nav_appearance_summary),
                value = themeLabel,
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
                    first = false, last = true, onClick = onOpenLinuxEnv)
            }
        }
        item("features-and-data") {
            PersonalizationSection(stringResource(R.string.settings_material_features_data)) {
                SettingsCategoryCard(RuntimeIconName.Extension, stringResource(R.string.plugin_center_title),
                    stringResource(R.string.plugin_center_summary), value = null,
                    first = true, last = false, onClick = onOpenPluginCenter)
                LocalBackupSettingsCard(first = false, last = true)
            }
        }
        item("application") {
            PersonalizationSection(stringResource(R.string.personal_application)) {
                SettingsCategoryCard(RuntimeIconName.Admin, stringResource(R.string.personal_diagnostics_title),
                    stringResource(R.string.settings_nav_diagnostics_summary),
                    if (developer) "诊断模式已开启" else "运行平稳",
                    first = true, last = false, onClick = onOpenSystemDev)
                SettingsCategoryCard(RuntimeIconName.Community, stringResource(R.string.settings_nav_about_title),
                    stringResource(R.string.settings_nav_about_summary),
                    if (appVersionName == "unknown") "版本号未知 · 稳定版" else "v$appVersionName 稳定版",
                    first = false, last = false, onClick = onOpenAboutCommunity)
                SettingsCategoryCard(RuntimeIconName.Shield, stringResource(ComponentsR.string.runtime_notice_title),
                    stringResource(ComponentsR.string.runtime_notice_summary), value = null,
                    first = false, last = true, onClick = { showUsageNotice = true })
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    value: String?,
    first: Boolean = true,
    last: Boolean = true,
    onClick: () -> Unit,
) {
    PreferenceSurface(first = first, last = last) {
        SettingsRow(icon = icon, title = title, subtitle = subtitle, value = value, onClick = onClick, tonalIcon = true)
    }
}
