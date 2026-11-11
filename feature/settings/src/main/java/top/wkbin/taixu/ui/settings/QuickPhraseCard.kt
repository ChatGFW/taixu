package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.QuickPhrase
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun QuickPhraseCard(
    phrase: QuickPhrase,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val typeLabel = when (phrase.targetProjectType) {
        "ANDROID" -> "Android"
        "FLUTTER" -> "Flutter"
        "REVERSE" -> "逆向分析"
        else -> "通用"
    }
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RuntimeTheme.shapes.medium,
        containerColor = RuntimeTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(0.dp),
    ) {
        BasicComponent(
            startAction = {
                RuntimeIcon(
                    parseIconName(phrase.iconName),
                    Modifier.padding(end = RuntimeSpacing.rowIconEnd).size(RuntimeSpacing.rowIconSize),
                    MiuixTheme.colorScheme.onSurface,
                )
            },
            endActions = {
                RuntimeSwitch(checked = phrase.isEnabled, onCheckedChange = onToggle)
            },
            bottomAction = {
                RuntimeActionRow {
                    RuntimeFilledTonalButton(onClick = onEdit) { Text("编辑", maxLines = 1) }
                    RuntimeTextButton(onClick = onDelete) {
                        Text("删除", color = RuntimeTheme.colorScheme.error, maxLines = 1)
                    }
                }
            },
            onClick = onEdit,
        ) {
            Text(
                phrase.title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                typeLabel + if (phrase.isEnabled) {
                    " · " + stringResource(R.string.personal_phrase_enabled)
                } else {
                    " · " + stringResource(R.string.personal_phrase_disabled)
                },
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (phrase.description.isNotBlank()) {
                Text(
                    phrase.description,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                phrase.content,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
