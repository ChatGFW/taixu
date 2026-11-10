package top.wkbin.taixu.ui.settings.search

import top.wkbin.taixu.ui.miuix.*
import androidx.compose.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.RuntimeExpandableSearchBar
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollViewport
/**
 * 太墟 · 全局功能与设置选项搜索页面
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSearchScreen(
    onBack: () -> Unit,
    onNavigateToTarget: (SettingsSearchTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    val searchResults = remember(searchQuery) {
        SettingsSearchRegistry.search(searchQuery)
    }

    LaunchedEffect(Unit) {
        // 进入页面后自动聚焦搜索框
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RuntimeTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "搜索功能与设置",
                statusText = "全局核心能力一键直达",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(runtimeScrollViewport(padding))
                // 搜索框固定在顶栏下方；结果列表不再叠进顶栏（避免与展开 SearchBar 抢位）。
                .padding(top = padding.calculateTopPadding())
                .imePadding(),
        ) {
            // miuix SearchBar：展开后结果区落在下方 content。
            RuntimeExpandableSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = "搜索功能名称、设置项、拼音或关键词...",
                outsideEndAction = {
                    RuntimeTextButton(
                        onClick = { searchExpanded = false; searchQuery = "" },
                    ) { Text("取消") }
                },
            ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = runtimeScrollContentPadding(
                    PaddingValues(bottom = padding.calculateBottomPadding()),
                    runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searchQuery.isBlank()) {
                    // 空输入状态：展示热门搜索推荐标签
                    item {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "热门快捷检索",
                                style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SettingsSearchRegistry.popularTags.forEach { tag ->
                                    Surface(
                                        color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        shape = RuntimeTheme.shapes.chip,
                                        modifier = Modifier.clickable {
                                            searchQuery = tag
                                        },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            RuntimeIcon(
                                                name = RuntimeIconName.Search,
                                                modifier = Modifier.size(12.dp),
                                                tint = RuntimeTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = tag,
                                                style = RuntimeTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                                color = RuntimeTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "全局所有功能选项 (${SettingsSearchRegistry.allItems.size})",
                            style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
                        )
                    }

                    // 展示全部功能条目
                    items(
                        items = SettingsSearchRegistry.allItems,
                        key = { it.id },
                    ) { item ->
                        SearchResultCard(
                            item = item,
                            onClick = { onNavigateToTarget(item.target) },
                            highlightQuery = "",
                        )
                    }
                } else if (searchResults.isEmpty()) {
                    // 无匹配结果状态
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RuntimeTheme.shapes.medium)
                                        .background(RuntimeTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RuntimeIcon(
                                        name = RuntimeIconName.Search,
                                        modifier = Modifier.size(26.dp),
                                        tint = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                Text(
                                    text = "未找到包含「$searchQuery」的功能选项",
                                    style = RuntimeTheme.typography.bodyMedium,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                )
                                RuntimeOutlinedButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Text("清空关键词")
                                }
                            }
                        }
                    }
                } else {
                    // 结果统计
                    item {
                        Text(
                            text = "找到 ${searchResults.size} 个相关功能选项",
                            style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
                        )
                    }

                    // 检索结果列表（关键词高亮）
                    items(
                        items = searchResults,
                        key = { it.id },
                    ) { item ->
                        SearchResultCard(
                            item = item,
                            onClick = { onNavigateToTarget(item.target) },
                            highlightQuery = searchQuery,
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * 搜索结果卡片，包含关键词高亮、彩色图标与面包屑路径
 */
@Composable
private fun SearchResultCard(
    item: SettingsSearchItem,
    onClick: () -> Unit,
    highlightQuery: String = "",
    modifier: Modifier = Modifier,
) {
    val categoryColor = item.category.themeColor
    val iconBg = categoryColor.copy(alpha = 0.12f)
    val primaryColor = RuntimeTheme.colorScheme.primary
    val titleTextColor = RuntimeTheme.colorScheme.onSurface
    val subtitleTextColor = RuntimeTheme.colorScheme.onSurfaceVariant
    val breadcrumbTextColor = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)

    val titleAnnotated = remember(item.title, highlightQuery, primaryColor, titleTextColor) {
        buildHighlightedText(
            text = item.title,
            query = highlightQuery,
            highlightColor = primaryColor,
            baseColor = titleTextColor,
        )
    }

    val subtitleAnnotated = remember(item.subtitle, highlightQuery, primaryColor, subtitleTextColor) {
        buildHighlightedText(
            text = item.subtitle,
            query = highlightQuery,
            highlightColor = primaryColor,
            baseColor = subtitleTextColor,
        )
    }

    val breadcrumbText = item.breadcrumb ?: item.category.label
    val breadcrumbAnnotated = remember(breadcrumbText, highlightQuery, primaryColor, breadcrumbTextColor) {
        buildHighlightedText(
            text = breadcrumbText,
            query = highlightQuery,
            highlightColor = primaryColor,
            baseColor = breadcrumbTextColor,
        )
    }

    RuntimeCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        borderColor = Color.Transparent,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 图标容器
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RuntimeTheme.shapes.small)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(item.icon, Modifier.size(18.dp), tint = categoryColor)
            }

            // 信息区域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = titleAnnotated,
                        style = RuntimeTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    item.badge?.let { badgeText ->
                        Surface(
                            color = categoryColor.copy(alpha = 0.15f),
                            shape = RuntimeTheme.shapes.tiny,
                        ) {
                            Text(
                                text = badgeText,
                                style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = categoryColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Text(
                    text = subtitleAnnotated,
                    style = RuntimeTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // 路径面包屑（展示深层嵌套路径）

                Surface(
                    color = RuntimeTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    shape = RuntimeTheme.shapes.tiny,
                ) {
                    Text(
                        text = breadcrumbAnnotated,
                        style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            // 跳转指示箭头
            RuntimeIcon(
                name = RuntimeIconName.ChevronRight,
                modifier = Modifier.size(16.dp),
                tint = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * 为匹配到的搜索关键词生成高亮 AnnotatedString
 * 支持多词分词匹配，采用高亮强调色 + 轻微半透明背景高光，保证在深浅色模式下均清晰醒目
 */
private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color,
    baseColor: Color,
): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) {
                append(text)
            }
        }
    }

    val tokens = trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) {
                append(text)
            }
        }
    }

    val highlightMask = BooleanArray(text.length)
    tokens.forEach { token ->
        runCatching {
            val regex = Regex(Regex.escape(token), RegexOption.IGNORE_CASE)
            regex.findAll(text).forEach { match ->
                val start = match.range.first.coerceIn(0, text.length)
                val end = (match.range.last + 1).coerceIn(0, text.length)
                for (i in start until end) {
                    highlightMask[i] = true
                }
            }
        }
    }

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val isHighlight = highlightMask[i]
            val start = i
            while (i < text.length && highlightMask[i] == isHighlight) {
                i++
            }
            val chunk = text.substring(start, i)
            if (isHighlight) {
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Medium,
                        background = highlightColor.copy(alpha = 0.16f),
                    )
                ) {
                    append(chunk)
                }
            } else {
                withStyle(SpanStyle(color = baseColor)) {
                    append(chunk)
                }
            }
        }
    }
}
