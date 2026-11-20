package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RuntimeWindowDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) = DropdownPreference(
    items = items, selectedIndex = selectedIndex, title = title,
    onSelectedIndexChange = onSelectedIndexChange, modifier = modifier, summary = summary,
    enabled = enabled, showValue = showValue, startAction = startAction,
    insideMargin = insideMargin, overlay = false,
)

@Composable
fun RuntimeOverlayDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) = DropdownPreference(
    items = items, selectedIndex = selectedIndex, title = title,
    onSelectedIndexChange = onSelectedIndexChange, modifier = modifier, summary = summary,
    enabled = enabled, showValue = showValue, startAction = startAction,
    insideMargin = insideMargin, overlay = true,
)

@Composable
private fun DropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier,
    summary: String?,
    enabled: Boolean,
    showValue: Boolean,
    startAction: @Composable (() -> Unit)?,
    insideMargin: PaddingValues,
    overlay: Boolean,
) {
    val backdrop = runtimeOverlayBackdrop()
    if (backdrop != null) {
        GlassDropdownPreference(
            onSelectedIndexChange = onSelectedIndexChange,
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            backdrop = backdrop,
            modifier = modifier,
            summary = summary,
            enabled = enabled,
            showValue = showValue,
            startAction = startAction,
            insideMargin = insideMargin,
            renderInRootScaffold = false,
        )
        return
    }
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    var expanded by remember { mutableStateOf(false) }
    var holdDown by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val actualEnabled = enabled && items.isNotEmpty()
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    val entry = remember(items, selectedIndex) {
        DropdownEntry(items = items.mapIndexed { index, text ->
            DropdownItem(
                text = text,
                selected = index == selectedIndex,
                onClick = { currentOnSelectedIndexChange(index) },
            )
        })
    }
    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        startAction = startAction,
        endActions = {
            if (showValue && items.isNotEmpty()) {
                val text = items.getOrNull(selectedIndex)
                if (!text.isNullOrEmpty()) {
                    Text(
                        text,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .align(Alignment.CenterVertically)
                            .weight(1f, fill = false),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = actionColor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DropdownArrowEndAction(actionColor = actionColor)
            if (items.isNotEmpty()) {
                if (overlay) {
                    OverlayDropdownPopup(
                        entry = entry,
                        show = expanded,
                        onDismiss = { expanded = false },
                        onDismissFinished = { holdDown = false },
                        maxHeight = null,
                        dropdownColors = DropdownDefaults.dropdownColors(),
                        renderInRootScaffold = true,
                    )
                } else {
                    WindowDropdownPopup(
                        entry = entry,
                        show = expanded,
                        onDismiss = { expanded = false },
                        onDismissFinished = { holdDown = false },
                        maxHeight = null,
                        dropdownColors = DropdownDefaults.dropdownColors(),
                    )
                }
            }
        },
        onClick = {
            if (actualEnabled) {
                expanded = !expanded
                if (expanded) {
                    holdDown = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        },
        role = Role.DropdownList,
        holdDownState = holdDown,
        enabled = actualEnabled,
    ) {
        PreferenceTexts(title, summary, actualEnabled)
    }
}
