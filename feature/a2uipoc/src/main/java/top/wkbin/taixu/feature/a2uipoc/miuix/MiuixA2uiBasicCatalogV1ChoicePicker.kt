package top.wkbin.taixu.feature.a2uipoc.miuix

import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.ChoicePicker.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.miuix.ButtonDefaults
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog

internal object MiuixA2uiBasicCatalogV1ChoicePicker : A2uiBasicCatalogV1.ChoicePicker {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        label: String?,
        options: List<Option>,
        value: List<String>,
        variant: Variant,
        displayStyle: DisplayStyle,
        filterable: Boolean,
        onValueChange: (List<String>) -> Unit,
        enabled: Boolean,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        val visible = remember(options, query) { options.filter { it.label.contains(query, ignoreCase = true) } }
        val errorMessage = checks.firstOrNull { !it.condition }?.message
        val toggle: (String) -> Unit = { option ->
            onValueChange(when (variant) {
                Variant.MutuallyExclusive -> listOf(option)
                Variant.MultipleSelection -> if (option in value) value.filter { it != option } else value + option
            })
        }
        Column(modifier.a2uiAccessibility(accessibility).semantics { errorMessage?.let { error(it) } },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            label?.let { Text(it, style = RuntimeTheme.typography.titleSmall) }
            if (displayStyle == DisplayStyle.Chips) {
                if (filterable) RuntimeFilledTextField(query, { query = it }, enabled = enabled, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visible.forEach { option ->
                        RuntimeButton(onClick = { toggle(option.value) }, enabled = enabled,
                            colors = if (option.value in value) ButtonDefaults.buttonColors(
                                containerColor = RuntimeTheme.colorScheme.primaryContainer,
                                contentColor = RuntimeTheme.colorScheme.onPrimaryContainer,
                            ) else ButtonDefaults.buttonColors()) {
                            Text(option.label)
                        }
                    }
                }
            } else {
                RuntimeButton(onClick = { expanded = true }, enabled = enabled) {
                    Text(options.filter { it.value in value }.joinToString { it.label }.ifEmpty { label.orEmpty() })
                }
            }
            errorMessage?.let { Text(it, color = RuntimeTheme.colorScheme.error) }
        }
        WindowDialog(show = expanded, title = label, onDismissRequest = { expanded = false }) {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (filterable) RuntimeFilledTextField(query, { query = it }, enabled = enabled, singleLine = true)
                visible.forEach { option ->
                    if (variant == Variant.MutuallyExclusive) {
                        top.wkbin.taixu.ui.components.RuntimeRadioPreference(selected = option.value in value,
                            onClick = { toggle(option.value); expanded = false }, title = option.label, enabled = enabled)
                    } else {
                        top.wkbin.taixu.ui.components.RuntimeCheckboxPreference(checked = option.value in value, onCheckedChange = { toggle(option.value) },
                            title = option.label, enabled = enabled)
                    }
                }
            }
        }
    }
}
