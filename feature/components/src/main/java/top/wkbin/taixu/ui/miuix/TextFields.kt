package top.wkbin.taixu.ui.miuix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

/** Presentation tokens shared by the app's labeled input adapters. */
data class TextFieldColors(
    val focusedContainerColor: Color,
    val unfocusedContainerColor: Color,
    val focusedBorderColor: Color,
    val unfocusedBorderColor: Color,
)

object TextFieldDefaults {
    @Composable
    fun colors(
        focusedContainerColor: Color = MiuixTheme.colorScheme.secondaryContainer,
        unfocusedContainerColor: Color = MiuixTheme.colorScheme.secondaryContainer,
        focusedIndicatorColor: Color = MiuixTheme.colorScheme.primary,
        unfocusedIndicatorColor: Color = Color.Transparent,
    ) = TextFieldColors(focusedContainerColor, unfocusedContainerColor, focusedIndicatorColor, unfocusedIndicatorColor)
}

/** Kept for call-site compatibility; miuix fields have no resting outline, so the unfocused border is ignored. */
object OutlinedTextFieldDefaults {
    @Composable
    fun colors(
        focusedContainerColor: Color = MiuixTheme.colorScheme.secondaryContainer,
        unfocusedContainerColor: Color = MiuixTheme.colorScheme.secondaryContainer,
        focusedBorderColor: Color = MiuixTheme.colorScheme.primary,
        unfocusedBorderColor: Color = Color.Transparent,
    ) = TextFieldColors(focusedContainerColor, unfocusedContainerColor, focusedBorderColor, unfocusedBorderColor)
}

private val IconOuterPadding = 16.dp
private val IconInnerPadding = 8.dp

/** Shared chrome for both value types: external label, icon insets, placeholder and supporting text. */
@Composable
private fun FieldFrame(
    modifier: Modifier,
    label: (@Composable () -> Unit)?,
    labelText: String?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    prefix: (@Composable () -> Unit)?,
    suffix: (@Composable () -> Unit)?,
    placeholder: (@Composable () -> Unit)?,
    showPlaceholder: Boolean,
    supportingText: (@Composable () -> Unit)?,
    field: @Composable (leading: (@Composable () -> Unit)?, trailing: (@Composable () -> Unit)?) -> Unit,
) {
    var leadingWidth by remember { mutableIntStateOf(0) }
    val start = leadingIcon != null || prefix != null
    val end = trailingIcon != null || suffix != null
    val leading: (@Composable () -> Unit)? = if (!start) null else {
        {
            Row(
                Modifier.onSizeChanged { leadingWidth = it.width }
                    .padding(start = IconOuterPadding, end = IconInnerPadding),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) { leadingIcon?.invoke(); prefix?.invoke() }
        }
    }
    val trailing: (@Composable () -> Unit)? = if (!end) null else {
        {
            Row(
                Modifier.padding(start = IconInnerPadding, end = IconOuterPadding),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) { suffix?.invoke(); trailingIcon?.invoke() }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (labelText == null) label?.invoke()
        Box {
            field(leading, trailing)
            if (showPlaceholder && placeholder != null && labelText.isNullOrEmpty()) {
                val startPadding: Dp = if (start) with(LocalDensity.current) { leadingWidth.toDp() } else IconOuterPadding
                Box(Modifier.padding(start = startPadding, top = 16.dp, end = IconOuterPadding)) {
                    CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurfaceVariantSummary, content = placeholder)
                }
            }
        }
        supportingText?.invoke()
    }
}

@Composable
private fun fieldColors(colors: TextFieldColors, focused: Boolean, isError: Boolean) =
    top.yukonga.miuix.kmp.basic.TextFieldDefaults.textFieldColors(
        backgroundColor = if (focused) colors.focusedContainerColor else colors.unfocusedContainerColor,
        borderColor = if (isError) RuntimeTheme.colorScheme.error else colors.focusedBorderColor,
    )

@Composable
private fun Modifier.errorOutline(isError: Boolean, focused: Boolean, radius: Dp): Modifier =
    if (isError && !focused) squircleBorder(1.dp, RuntimeTheme.colorScheme.error, radius) else this

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = RuntimeTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RuntimeTheme.shapes.medium,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    labelText: String? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused = source.collectIsFocusedAsState().value
    val radius = shape.uniformRadius() ?: top.yukonga.miuix.kmp.basic.TextFieldDefaults.CornerRadius
    FieldFrame(modifier, label, labelText, leadingIcon, trailingIcon, prefix, suffix, placeholder,
        value.isEmpty(), supportingText) { leading, trailing ->
        MiuixTextField(
            value = value, onValueChange = onValueChange, label = labelText.orEmpty(),
            modifier = Modifier.fillMaxWidth().errorOutline(isError, focused, radius),
            cornerRadius = radius,
            enabled = enabled, readOnly = readOnly, textStyle = textStyle,
            colors = fieldColors(colors, focused, isError),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            leadingIcon = leading, trailingIcon = trailing,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            visualTransformation = visualTransformation, interactionSource = source,
        )
    }
}

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = RuntimeTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RuntimeTheme.shapes.medium,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    labelText: String? = null,
) = OutlinedTextField(value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder,
    leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation,
    keyboardOptions, keyboardActions, singleLine, maxLines, minLines, interactionSource, shape, colors, labelText)

@Composable
fun OutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = RuntimeTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RuntimeTheme.shapes.medium,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    labelText: String? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused = source.collectIsFocusedAsState().value
    val radius = shape.uniformRadius() ?: top.yukonga.miuix.kmp.basic.TextFieldDefaults.CornerRadius
    FieldFrame(modifier, label, labelText, leadingIcon, trailingIcon, prefix, suffix, placeholder,
        value.text.isEmpty(), supportingText) { leading, trailing ->
        MiuixTextField(
            value = value, onValueChange = onValueChange, label = labelText.orEmpty(),
            modifier = Modifier.fillMaxWidth().errorOutline(isError, focused, radius),
            cornerRadius = radius,
            enabled = enabled, readOnly = readOnly, textStyle = textStyle,
            colors = fieldColors(colors, focused, isError),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            leadingIcon = leading, trailingIcon = trailing,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            visualTransformation = visualTransformation, interactionSource = source,
        )
    }
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = RuntimeTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RuntimeTheme.shapes.medium,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    labelText: String? = null,
) = OutlinedTextField(value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder,
    leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation,
    keyboardOptions, keyboardActions, singleLine, maxLines, minLines, interactionSource, shape, colors, labelText)
