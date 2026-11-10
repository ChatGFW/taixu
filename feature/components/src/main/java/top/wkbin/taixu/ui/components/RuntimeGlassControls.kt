package top.wkbin.taixu.ui.components

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import top.wkbin.taixu.ui.miuix.Badge
import top.wkbin.taixu.ui.miuix.DropdownMenu
import top.wkbin.taixu.ui.miuix.DropdownMenuItem
import top.wkbin.taixu.ui.miuix.LocalTextStyle
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.MenuDefaults
import top.wkbin.taixu.ui.miuix.MenuItemColors
import top.wkbin.taixu.ui.miuix.BottomSheetDefaults
import top.wkbin.taixu.ui.miuix.ModalBottomSheet
import top.wkbin.taixu.ui.miuix.OutlinedTextField
import top.wkbin.taixu.ui.miuix.OutlinedTextFieldDefaults
import top.wkbin.taixu.ui.miuix.SheetState
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.miuix.TextFieldColors
import top.wkbin.taixu.ui.miuix.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.shape.CircleShape
/** 全圆角药丸形状 (Capsule Shape) */
val CapsuleShape: Shape = CircleShape


// ═══════════════════════════════════════════════════════════════════════════
// 1. RuntimeSegmentedControl（药丸分段选择器）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 太墟 · 药丸分段选择器 (Segmented Control)：Material 3 表面 + 弹簧滑块指示。
 */
@Composable
fun <T> RuntimeSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            top.yukonga.miuix.kmp.basic.Button(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                colors = if (selected) top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
                    else top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(),
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) { itemContent(item, selected) }
        }
    }
}

/**
 * 字符型选项分段选择器重载（最常见的文本分段）。
 */
@Composable
fun RuntimeSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    top.yukonga.miuix.kmp.basic.TabRow(
        tabs = options,
        selectedTabIndex = selectedIndex,
        onTabSelected = { if (enabled) onSelect(it) },
        modifier = modifier,
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 2. RuntimeOutlinedTextField & RuntimeTextField（文本输入框）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 太墟 · 描边文本输入框，委托 Miuix TextField。
 */
@Composable
fun RuntimeOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
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
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        labelText = labelText,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = actualInteractionSource,
        shape = shape,
        colors = colors,
    )
}

/**
 * 填充型文本输入框别名，与 RuntimeOutlinedTextField 共享同一实现。
 */
@Composable
fun RuntimeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
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
    RuntimeOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        labelText = labelText,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors,
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 3. RuntimeModalBottomSheet（模态底部抽屉）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 太墟 · 模态底部抽屉，委托 Miuix WindowBottomSheet。
 */
@Composable
fun RuntimeModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    containerColor: Color = Color.Unspecified,
    contentColor: Color = RuntimeTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = if (containerColor != Color.Unspecified) containerColor else RuntimeTheme.colorScheme.surfaceContainerLow,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle ?: { BottomSheetDefaults.DragHandle() },
        content = content,
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 4. RuntimeDropdownMenu & RuntimeDropdownMenuItem（上下文菜单）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 太墟 · 上下文菜单，委托 Miuix WindowListPopup。
 */
@Composable
fun RuntimeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RuntimeTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        properties = properties,
        shape = shape,
        content = content,
    )
}

/** 太墟 · 上下文菜单项：沿用 miuix 弹出列表的整行按压反馈。 */
@Composable
fun RuntimeDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 5. RuntimeSearchBar（胶囊搜索栏）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 太墟 · 胶囊搜索栏：直接委托 miuix [top.yukonga.miuix.kmp.basic.InputField]
 *（自带 Search / SearchCleanup 图标与清空动效）。
 */
@Composable
fun RuntimeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索...",
    leadingIcon: (@Composable () -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    // InputField 内建清空；若调用方另需 onClear 副作用，在查询被清空时补一次回调。
    var previousQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (previousQuery.isNotEmpty() && query.isEmpty()) onClear?.invoke()
        previousQuery = query
    }
    top.yukonga.miuix.kmp.basic.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch?.invoke(it) },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier,
        label = placeholder,
        enabled = enabled,
        leadingIcon = leadingIcon,
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 6. RuntimeBadge（状态/计数角标）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 太墟 · 状态/计数角标，委托 Miuix Badge。
 */
@Composable
fun RuntimeBadge(
    modifier: Modifier = Modifier,
    containerColor: Color = RuntimeTheme.colorScheme.error,
    contentColor: Color = RuntimeTheme.colorScheme.onError,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    Badge(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content,
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 7. RuntimeSheetHeader（底部抽屉标准顶栏）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 太墟 · 底部抽屉顶栏：miuix 面板布局，标题居中，两侧为圆形图标动作（关闭 / 确认）。
 * 文案参数作为无障碍描述保留。
 */
@Composable
fun RuntimeSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirm: (() -> Unit)? = null,
    confirmText: String = "完成",
    dismissText: String = "取消",
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        SheetHeaderAction(RuntimeIconName.Close, dismissText, onDismiss, Modifier.align(Alignment.CenterStart))
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 56.dp),
            style = RuntimeTheme.typography.titleLarge,
            color = RuntimeTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (onConfirm != null) {
            SheetHeaderAction(RuntimeIconName.Check, confirmText, onConfirm, Modifier.align(Alignment.CenterEnd), primary = true)
        }
    }
}

@Composable
private fun SheetHeaderAction(
    icon: RuntimeIconName,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    primary: Boolean = false,
) {
    val colors = RuntimeTheme.colorScheme
    top.yukonga.miuix.kmp.basic.IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp).semantics { contentDescription = label },
        backgroundColor = if (primary) colors.primary else colors.surfaceContainerHigh,
    ) {
        RuntimeIcon(icon, Modifier.size(20.dp), if (primary) colors.onPrimary else colors.onSurface)
    }
}
