package top.wkbin.taixu.ui.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.components.RuntimeCheckbox as Checkbox
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.template.ProjectTemplateInputType
import top.wkbin.taixu.template.ProjectTemplateVariable
import top.wkbin.taixu.ui.components.RuntimeDropdownField

@Composable
private fun VariableLabel(label: String, required: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        if (required) {
            Text(" *", color = RuntimeTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun TemplateVariableFields(
    variables: List<ProjectTemplateVariable>,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
) {
    variables.filter { it.prompt }.forEach { variable ->
        val value = values[variable.name] ?: variable.defaultValue
        val error = templateVariableError(variable, value)
        when (variable.inputType) {
            ProjectTemplateInputType.BOOLEAN -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onValueChange(variable.name, (!value.equals("true", ignoreCase = true)).toString()) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = value.equals("true", ignoreCase = true),
                    onCheckedChange = { onValueChange(variable.name, it.toString()) },
                )
                Column(Modifier.padding(start = 8.dp)) {
                    VariableLabel(variable.label, variable.required)
                    if (variable.description.isNotBlank()) {
                        Text(variable.description, style = RuntimeTheme.typography.bodySmall)
                    }
                }
            }

            ProjectTemplateInputType.SELECT -> {
                RuntimeDropdownField(
                    title = if (variable.required) "${variable.label} *" else variable.label,
                    items = variable.options.map { it.label }.ifEmpty { listOf(value) },
                    selectedIndex = variable.options.indexOfFirst { it.value == value }.coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        variable.options.getOrNull(index)?.let { option ->
                            onValueChange(variable.name, option.value)
                        }
                    },
                    summary = variable.description.takeIf(String::isNotBlank),
                    enabled = variable.options.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> RuntimeFilledTextField(
                value = value,
                onValueChange = { onValueChange(variable.name, it) },
                label = { VariableLabel(variable.label, variable.required) },
                placeholder = variable.placeholder.takeIf(String::isNotBlank)?.let { { Text(it) } },
                supportingText = {
                    Text(error ?: variable.description)
                },
                isError = error != null,
                singleLine = variable.inputType != ProjectTemplateInputType.MULTILINE,
                minLines = if (variable.inputType == ProjectTemplateInputType.MULTILINE) 3 else 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (variable.inputType == ProjectTemplateInputType.NUMBER) {
                        KeyboardType.Number
                    } else {
                        KeyboardType.Text
                    },
                ),
                visualTransformation = if (variable.inputType == ProjectTemplateInputType.SECRET) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 校验失败类型：REQUIRED = 必填为空；INVALID_FORMAT = 不满足 validationRegex。 */
internal enum class TemplateVariableErrorKind { REQUIRED, INVALID_FORMAT }

internal fun templateVariableErrorKind(variable: ProjectTemplateVariable, value: String): TemplateVariableErrorKind? {
    if (variable.required && value.isBlank()) return TemplateVariableErrorKind.REQUIRED
    if (value.isNotBlank() && variable.validationRegex.isNotBlank() &&
        !runCatching { Regex(variable.validationRegex).matches(value) }.getOrDefault(false)
    ) {
        return TemplateVariableErrorKind.INVALID_FORMAT
    }
    return null
}

/** 校验失败的本地化文案（必须在 Composable 上下文中调用）。 */
@Composable
internal fun templateVariableError(variable: ProjectTemplateVariable, value: String): String? =
    when (templateVariableErrorKind(variable, value)) {
        null -> null
        TemplateVariableErrorKind.REQUIRED -> stringResource(R.string.template_variable_required, variable.label)
        TemplateVariableErrorKind.INVALID_FORMAT -> stringResource(R.string.template_variable_invalid_format, variable.label)
    }
