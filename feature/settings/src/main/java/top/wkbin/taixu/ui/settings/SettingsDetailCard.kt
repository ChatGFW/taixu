package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/** Consistent detail-page surface without increasing the default content density. */
@Composable
internal fun SettingsDetailCard(
    modifier: Modifier = Modifier,
    containerColor: Color = RuntimeTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = Color.Transparent,
    shape: Shape = RuntimeTheme.shapes.extraLarge,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(SettingsDensity.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) = RuntimeCard(modifier, containerColor, borderColor, shape, onClick, contentPadding, content)

@Composable
internal fun SettingsDialogForm(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** Give the editable port a complete row, including on narrow windows and large fonts. */
@Composable
internal fun SettingsPortEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supportingText: String,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RuntimeFilledTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            labelText = label,
            singleLine = true,
            isError = isError,
            supportingText = {
                Text(
                    supportingText,
                    style = settingsSupportingStyle(),
                    color = if (isError) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        RuntimeActionRow(content = actions)
    }
}
