package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.ui.components.RuntimeSearchEntry
import top.wkbin.taixu.ui.components.RuntimeUsageNoticeDialog
import top.wkbin.taixu.feature.components.R as ComponentsR
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.MainDestination
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
    onBack: () -> Unit = { onNavigate(MainDestination.Agent) },
) {
    var showUsageNotice by rememberSaveable { mutableStateOf(false) }
    if (showUsageNotice) RuntimeUsageNoticeDialog(onDismiss = { showUsageNotice = false })

    PersonalizationScaffold(stringResource(R.string.personal_settings_title), onBack) {
        item("search") {
            RuntimeSearchEntry(stringResource(R.string.personal_search), onOpenSearch)
        }
        item("appearance") {
            SettingsCategoryCard(
                icon = RuntimeIconName.Palette,
                title = stringResource(R.string.personal_appearance_title),
                subtitle = stringResource(R.string.settings_nav_appearance_summary),
                onClick = onOpenAppearance,
            )
        }
        item("environment") {
            PersonalizationSection(stringResource(R.string.personal_environment)) {
                SettingsCategoryCard(RuntimeIconName.Brain, stringResource(R.string.personal_agents_title),
                    stringResource(R.string.settings_nav_agents_summary),
                    first = true, last = false, onClick = onOpenAgentEco)
                SettingsCategoryCard(RuntimeIconName.Server, stringResource(R.string.personal_linux_title),
                    stringResource(R.string.settings_nav_linux_summary),
                    first = false, last = true, onClick = onOpenLinuxEnv)
            }
        }
        item("features-and-data") {
            PersonalizationSection(stringResource(R.string.settings_material_features_data)) {
                SettingsCategoryCard(RuntimeIconName.Extension, stringResource(R.string.plugin_center_title),
                    stringResource(R.string.plugin_center_summary),
                    first = true, last = false, onClick = onOpenPluginCenter)
                LocalBackupSettingsCard(first = false, last = true)
            }
        }
        item("application") {
            PersonalizationSection(stringResource(R.string.personal_application)) {
                SettingsCategoryCard(RuntimeIconName.Admin, stringResource(R.string.personal_diagnostics_title),
                    stringResource(R.string.settings_nav_diagnostics_summary),
                    first = true, last = false, onClick = onOpenSystemDev)
                SettingsCategoryCard(RuntimeIconName.Community, stringResource(R.string.settings_nav_about_title),
                    stringResource(R.string.settings_nav_about_summary),
                    first = false, last = false, onClick = onOpenAboutCommunity)
                SettingsCategoryCard(RuntimeIconName.Shield, stringResource(ComponentsR.string.runtime_notice_title),
                    stringResource(ComponentsR.string.runtime_notice_summary),
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
    first: Boolean = true,
    last: Boolean = true,
    onClick: () -> Unit,
) {
    PreferenceSurface(first = first, last = last) {
        SettingsRow(icon = icon, title = title, subtitle = subtitle, onClick = onClick, tonalIcon = true)
    }
}
