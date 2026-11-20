package top.wkbin.taixu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.glassSurface
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu

/**
 * 表单/设置里的单选下拉：走单行标题/副标题的 miuix 下拉行。
 */
@Composable
fun RuntimeDropdownField(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
) {
    RuntimeWindowDropdownPreference(
        items = items,
        selectedIndex = if (items.isEmpty()) 0 else selectedIndex.coerceIn(0, items.lastIndex),
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled && items.isNotEmpty(),
        showValue = showValue,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

/**
 * 由标题列表构造 [DropdownEntry]，供 [RuntimeIconMenu] / [WindowIconDropdownMenu] 使用。
 */
fun runtimeMenuEntry(
    vararg items: Pair<String, () -> Unit>,
): DropdownEntry = DropdownEntry(
    items = items.map { (text, onClick) ->
        DropdownItem(text = text, onClick = onClick)
    },
)

fun runtimeMenuEntry(
    items: List<DropdownItem>,
): DropdownEntry = DropdownEntry(items = items)

/**
 * 顶栏和列表的更多菜单。页面有独立背景层时，在 Scaffold 的弹层中绘制玻璃材质。
 */
@Composable
fun RuntimeIconMenu(
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = IconButtonDefaults.CornerRadius,
    content: @Composable () -> Unit,
) {
    val backdrop = runtimeOverlayBackdrop()
    if (backdrop != null) {
        OverlayIconCascadingDropdownMenu(
            entries = entries,
            surface = glassSurface(backdrop, GlassPopupDefaults.visuals()),
            modifier = modifier,
            enabled = enabled,
            renderInRootScaffold = false,
            backgroundColor = backgroundColor,
            cornerRadius = cornerRadius,
            content = content,
        )
        return
    }
    WindowIconDropdownMenu(
        entries = entries,
        modifier = modifier,
        enabled = enabled,
        backgroundColor = backgroundColor,
        cornerRadius = cornerRadius,
        content = content,
    )
}
