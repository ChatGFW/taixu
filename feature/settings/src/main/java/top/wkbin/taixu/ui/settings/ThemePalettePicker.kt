package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import top.wkbin.taixu.ui.miuix.*
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.ThemePalette
import top.wkbin.taixu.core.model.ThemePaletteStyle
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.theme.ThemePaletteColors
import top.wkbin.taixu.ui.theme.themePaletteColorScheme
import top.wkbin.taixu.ui.theme.themePaletteSeed

/** Manual palettes stay available while Monet is enabled; picking one switches to manual color. */
@Composable
internal fun ThemePalettePicker(
    palette: ThemePalette,
    dynamicColor: Boolean,
    themeMode: String,
    onColor: (String) -> Unit,
    onStyle: (ThemePaletteStyle) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    val colorNames = stringArrayResource(R.array.appearance_palette_names)
    val styleNames = stringArrayResource(R.array.appearance_palette_styles)
    val isDark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val pager = rememberPagerState(
        initialPage = (ThemePaletteColors.indexOfFirst { it.id == palette.colorId }.coerceAtLeast(0) / 4),
        pageCount = { (ThemePaletteColors.size + 3) / 4 },
    )
    LaunchedEffect(palette.colorId) {
        pager.scrollToPage(ThemePaletteColors.indexOfFirst { it.id == palette.colorId }.coerceAtLeast(0) / 4)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HorizontalPager(state = pager, pageSpacing = 16.dp) { page ->
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePaletteColors.drop(page * 4).take(4).forEachIndexed { index, color ->
                    val scheme = remember(color, isDark, palette.style) { themePaletteColorScheme(ThemePalette(color.id, palette.style), isDark) }
                    val selected = !dynamicColor && color.id == palette.colorId
                    val label = colorNames[page * 4 + index]
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .border(2.dp, if (selected) scheme.primary else Color.Transparent, CircleShape)
                            .padding(5.dp).clip(CircleShape)
                            .selectable(selected, role = Role.RadioButton, onClick = { onColor(color.id) })
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                            drawArc(scheme.primary, 180f, 180f, useCenter = true)
                            drawArc(scheme.secondaryContainer, 90f, 90f, useCenter = true)
                            drawArc(scheme.tertiary, 0f, 90f, useCenter = true)
                        }
                        if (selected) RuntimeIcon(RuntimeIconName.Check, Modifier.size(22.dp), scheme.onPrimary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            repeat(pager.pageCount) { index ->
                val current = index == pager.currentPage
                Box(Modifier.size(width = if (current) 18.dp else 6.dp, height = 6.dp).clip(CircleShape)
                    .background(if (current) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.outline))
            }
        }
        Text(stringResource(if (dynamicColor) R.string.appearance_palette_wallpaper_hint else R.string.appearance_palette_manual_hint),
            style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp))
        // miuix ColorPalette / ColorPicker：选中色映射到最近预设色板 ID。
        val seed = remember(palette.colorId) { themePaletteSeed(palette.colorId) }
        var draft by remember(seed) { mutableStateOf(seed) }
        fun applyNearest(color: Color) {
            draft = color
            onDynamicColor(false)
            onColor(nearestThemePaletteId(color))
        }
        Text("精细调色", style = RuntimeTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp))
        RuntimeColorPalette(
            color = draft,
            onColorChanged = ::applyNearest,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        RuntimeColorPicker(
            color = draft,
            onColorChanged = ::applyNearest,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        top.yukonga.miuix.kmp.basic.Card {
            PersonalizationToggle(
                icon = RuntimeIconName.Image,
                title = stringResource(R.string.personal_wallpaper_color),
                subtitle = stringResource(R.string.personal_wallpaper_color_description),
                checked = dynamicColor,
                onCheckedChange = onDynamicColor,
            )
            top.wkbin.taixu.ui.components.RuntimeWindowDropdownPreference(
                items = styleNames.toList(),
                selectedIndex = palette.style.ordinal,
                title = stringResource(R.string.appearance_palette_style),
                startAction = { SettingsRowIcon(RuntimeIconName.Palette) },
                onSelectedIndexChange = { onStyle(ThemePaletteStyle.entries[it]) },
            )
        }
    }
}

private fun nearestThemePaletteId(color: Color): String =
    ThemePaletteColors.minBy { swatch ->
        val dr = color.red - swatch.seed.red
        val dg = color.green - swatch.seed.green
        val db = color.blue - swatch.seed.blue
        dr * dr + dg * dg + db * db
    }.id
