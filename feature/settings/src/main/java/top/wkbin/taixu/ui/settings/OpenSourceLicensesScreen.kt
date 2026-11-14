package top.wkbin.taixu.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSettingsScaffold
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.settings.LocalizedText as Text

private data class BundledLicense(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val assetPath: String,
)

private val bundledLicenses = listOf(
    BundledLicense(
        id = "taixu",
        titleRes = R.string.settings_oss_taixu_title,
        subtitleRes = R.string.settings_oss_taixu_subtitle,
        assetPath = "licenses/taixu-LICENSE",
    ),
    BundledLicense(
        id = "miuix",
        titleRes = R.string.settings_oss_miuix_title,
        subtitleRes = R.string.settings_oss_apache_subtitle,
        assetPath = "licenses/miuix-LICENSE",
    ),
    BundledLicense(
        id = "material_symbols",
        titleRes = R.string.settings_oss_material_symbols_title,
        subtitleRes = R.string.settings_oss_apache_subtitle,
        assetPath = "licenses/material-symbols-LICENSE",
    ),
    BundledLicense(
        id = "a2ui",
        titleRes = R.string.settings_oss_a2ui_title,
        subtitleRes = R.string.settings_oss_apache_subtitle,
        assetPath = "licenses/a2ui-LICENSE",
    ),
    BundledLicense(
        id = "rtk",
        titleRes = R.string.settings_oss_rtk_title,
        subtitleRes = R.string.settings_oss_apache_subtitle,
        assetPath = "licenses/rtk-LICENSE",
    ),
    BundledLicense(
        id = "agency_agents",
        titleRes = R.string.settings_oss_agency_agents_title,
        subtitleRes = R.string.settings_oss_mit_subtitle,
        assetPath = "agency_agents/LICENSE",
    ),
)

/**
 * 开源许可：展示本应用 GPL-3.0 与随 APK 携带的第三方许可证全文。
 */
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = bundledLicenses.firstOrNull { it.id == selectedId }

    if (selected != null) {
        OpenSourceLicenseDetailScreen(
            entry = selected,
            onBack = { selectedId = null },
        )
    } else {
        OpenSourceLicenseListScreen(
            onBack = onBack,
            onOpenLicense = { selectedId = it },
        )
    }
}

@Composable
private fun OpenSourceLicenseListScreen(
    onBack: () -> Unit,
    onOpenLicense: (String) -> Unit,
) {
    RuntimeSettingsScaffold(
        title = stringResource(R.string.settings_oss_title),
        onBack = onBack,
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_oss_section_app),
                style = RuntimeTheme.typography.labelLarge,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            SettingsGroup {
                val app = bundledLicenses.first()
                SettingsRow(
                    icon = RuntimeIconName.Shield,
                    title = stringResource(app.titleRes),
                    subtitle = stringResource(app.subtitleRes),
                    onClick = { onOpenLicense(app.id) },
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.settings_oss_section_third_party),
                style = RuntimeTheme.typography.labelLarge,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            SettingsGroup {
                bundledLicenses.drop(1).forEach { entry ->
                    SettingsRow(
                        icon = RuntimeIconName.Document,
                        title = stringResource(entry.titleRes),
                        subtitle = stringResource(entry.subtitleRes),
                        onClick = { onOpenLicense(entry.id) },
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.settings_oss_footer),
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun OpenSourceLicenseDetailScreen(
    entry: BundledLicense,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val body = remember(entry.id) { readLicenseAsset(context, entry.assetPath) }

    RuntimeSettingsScaffold(
        title = stringResource(entry.titleRes),
        onBack = onBack,
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(entry.subtitleRes),
                    style = RuntimeTheme.typography.labelMedium,
                    color = RuntimeTheme.colorScheme.primary,
                )
                SelectionContainer {
                    Text(
                        text = body ?: stringResource(R.string.settings_oss_load_failed),
                        style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = RuntimeTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun readLicenseAsset(context: Context, assetPath: String): String? =
    runCatching {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }.getOrNull()
