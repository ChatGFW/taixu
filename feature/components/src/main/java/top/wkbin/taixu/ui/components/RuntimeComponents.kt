package top.wkbin.taixu.ui.components

import top.wkbin.taixu.ui.miuix.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.components.R
import androidx.compose.ui.res.stringResource

/**
 * 太墟 (TaiXu) 四大核心中枢导航定义：
 * 太墟（开辟画布）· 智枢（AI 结对）· 工坊（工作区）· 乾坤（设置与模型）
 */
enum class MainDestination(val labelRes: Int, val subtitleRes: Int, val icon: RuntimeIconName) {
    Home(R.string.components_nav_home, R.string.components_nav_subtitle_home, RuntimeIconName.NavDashboard),
    Agent(R.string.components_nav_agent, R.string.components_nav_subtitle_agent, RuntimeIconName.NavMessage), Bots(R.string.components_nav_bots, R.string.components_nav_subtitle_bots, RuntimeIconName.Bot),
    Workspace(R.string.components_nav_workspace, R.string.components_nav_subtitle_workspace, RuntimeIconName.NavRepository),
    Settings(R.string.components_nav_settings, R.string.components_nav_subtitle_settings, RuntimeIconName.NavSettings),
}

/**
 * 太墟 · 核心底部中枢导航栏（Material 3 NavigationBar）。
 */
@Composable
fun RuntimeBottomBar(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    top.yukonga.miuix.kmp.basic.NavigationBar(modifier = modifier) {
        MainDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = {
                    if (destination != selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigate(destination)
                },
                icon = runtimeIconImageVector(destination.icon),
                label = stringResource(destination.labelRes),
            )
        }
    }
}

/**
 * 太墟品牌标志徽章
 */
@Composable
fun TaiXuBrandBadge(size: Dp = 38.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Color.White)
            .padding(size * 0.10f),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.components_taixu_logo),
            contentDescription = stringResource(R.string.components_taixu_logo),
            modifier = Modifier.size(size * 0.80f),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = RuntimeSpacing.cardContent),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            top.yukonga.miuix.kmp.basic.SmallTitle(text = title, insideMargin = PaddingValues(0.dp))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 太墟精制卡片组件：支持自适应表面色与细腻描边
 */
@Composable
fun RuntimeCard(
    modifier: Modifier = Modifier,
    containerColor: Color = RuntimeTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = Color.Transparent,
    shape: Shape = RuntimeTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(RuntimeSpacing.cardContent),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = borderColor.takeIf { it.alpha > 0f }?.let { BorderStroke(1.dp, it) }
    if (onClick == null) {
        Card(
            modifier = modifier, shape = shape.atMostCardRadius(),
            colors = colors,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier, shape = shape.atMostCardRadius(),
            colors = colors,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun RuntimeSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    thumbContent: (@Composable (() -> Unit))? = null,
) {
    top.wkbin.taixu.ui.miuix.Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled, colors = colors, thumbContent = thumbContent)
}

@Composable
fun RuntimeLinearProgressIndicator(
    progress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    color: Color = RuntimeTheme.colorScheme.primary,
    trackColor: Color = RuntimeTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    wavy: Boolean = false,
) {
    top.wkbin.taixu.ui.miuix.LinearProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor)
}

@Composable
fun RuntimeCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = top.yukonga.miuix.kmp.theme.LocalContentColor.current,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 4.5.dp,
) {
    top.wkbin.taixu.ui.miuix.CircularProgressIndicator(modifier = modifier, color = color, trackColor = trackColor, strokeWidth = strokeWidth)
}

@Composable
fun RuntimeCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
) {
    top.wkbin.taixu.ui.miuix.Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled, colors = colors)
}

@Composable
fun RuntimeRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
) {
    top.wkbin.taixu.ui.miuix.RadioButton(selected = selected, onClick = onClick, modifier = modifier, enabled = enabled, colors = colors)
}

@Composable
fun RuntimeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
) {
    top.wkbin.taixu.ui.miuix.Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
    )
}

@Composable
fun RuntimeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        icon = icon,
    )
}


/**
 * 状态标签：委托 miuix [top.yukonga.miuix.kmp.basic.Badge]。
 * [pulsing] 时对容器透明度做轻呼吸，避免再手搓 Surface 药丸。
 */
@Composable
fun StatusBadge(
    text: String,
    color: Color = RuntimeTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val pulseAlpha = if (pulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        ).value
    } else 1f
    top.yukonga.miuix.kmp.basic.Badge(
        modifier = modifier.alpha(pulseAlpha),
        containerColor = color.copy(alpha = 0.16f),
        contentColor = color,
    ) {
        Text(text = text, maxLines = 1, softWrap = false)
    }
}

@Composable
fun IconTile(
    icon: RuntimeIconName,
    modifier: Modifier = Modifier,
    color: Color = RuntimeTheme.colorScheme.primary,
    size: Dp = 42.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .squircleBackground(color.copy(alpha = 0.12f), size * 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        RuntimeIcon(icon, Modifier.size(size * 0.48f), tint = color)
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isCode: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = RuntimeTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default,
            ),
            color = RuntimeTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(0.62f, fill = false),
        )
    }
}

@Composable
fun CodeBlockRow(
    label: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = RuntimeTheme.typography.labelSmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
        val shape = RoundedCornerShape(8.dp)
        val codeModifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
        Box(modifier = codeModifier) {
            Text(
                text = code,
                style = RuntimeTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                color = RuntimeTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun EmptyPanel(
    icon: RuntimeIconName,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    RuntimeCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        borderColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(icon, color = RuntimeTheme.colorScheme.primary, size = 48.dp)
            Text(
                title,
                style = RuntimeTheme.typography.titleMedium,
                color = RuntimeTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val color = if (isError) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.primary
    val bannerModifier = modifier
        .fillMaxWidth()
        .squircleBackground(color.copy(alpha = if (isError) 0.10f else 0.08f), 16.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp)
    Row(
        modifier = bannerModifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuntimeIcon(if (isError) RuntimeIconName.Alert else RuntimeIconName.Check, Modifier.size(17.dp), color)
        Text(
            text = text,
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            val dismissLabel = stringResource(R.string.components_close)
            RuntimeIconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
                contentDescription = dismissLabel,
            ) {
                RuntimeIcon(RuntimeIconName.Close, Modifier.size(14.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 🌟 滚动列表平滑渐隐遮罩 (Scroll Fading Edge Mask)
 * 为列表顶部和底部提供平滑的 Alpha 渐隐过渡，消除与 TopBar / BottomBar 之间的生硬切边。
 *
 * ⚠️ 性能提示（2026-09-14）：本实现依赖 `CompositingStrategy.Offscreen` ——
 * `BlendMode.DstIn` 要求先渲染到独立离屏缓冲才能做遮罩。挂在 LazyColumn 上时，
 * **滚动期间每帧都要全量离屏合成**，屏幕越大代价越高，表现为「静止不卡、快速滑动明显掉帧」。
 * 消息列表已改用 [ScrollFadeOverlay]（列表外叠加渐变，代价近乎为零）。
 * 仅在「非滚动、或面积很小」的场景继续使用本修饰符。
 */
fun Modifier.scrollFadingEdge(
    top: Dp = 14.dp,
    bottom: Dp = 14.dp,
): Modifier = this.graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val topPx = top.toPx()
    val bottomPx = bottom.toPx()
    val h = size.height

    if (topPx > 0f && h > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = topPx.coerceAtMost(h),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
    if (bottomPx > 0f && h > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = (h - bottomPx).coerceAtLeast(0f),
                endY = h,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/**
 * 🌟 滚动渐隐遮罩（轻量版）：绘制为**叠加在滚动内容之上**的两段渐变，不使用离屏合成。
 *
 * 与 [scrollFadingEdge] 的取舍：
 *  - scrollFadingEdge 用 DstIn 真正「擦除」内容 alpha（效果更干净），
 *    但要求 Offscreen 合成 → 滚动每帧全量离屏渲染，是滑动卡顿的头号来源。
 *  - 本实现改为「在内容上方叠一层从背景色到透明的渐变」，视觉近似（顶/底自然淡出），
 *    但**零离屏合成开销**，适合挂在长列表这类滚动频繁、面积大的场景。
 *
 * 用法：把滚动内容与本 Overlay 放进同一个 Box，Overlay 位于内容之上：
 * ```
 * Box {
 *     LazyColumn(...)
 *     ScrollFadeOverlay(top = 4.dp, bottom = 20.dp)
 * }
 * ```
 */
@Composable
fun ScrollFadeOverlay(
    modifier: Modifier = Modifier,
    top: Dp = 14.dp,
    bottom: Dp = 14.dp,
    color: Color = RuntimeTheme.colorScheme.surface,
) {
    Box(modifier.fillMaxSize()) {
        if (top > 0.dp) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(top)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(listOf(color, color.copy(alpha = 0f))),
                    ),
            )
        }
        if (bottom > 0.dp) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bottom)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(color.copy(alpha = 0f), color)),
                    ),
            )
        }
    }
}

/**
 * 实心圆形操作钮：对齐 miuix [IconButton] 的 40dp，不再使用库默认 60dp FAB。
 * 新增入口优先放顶栏 actions；仅在确实需要浮层动作时调用本组件。
 */
@Composable
fun RuntimeFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = RuntimeTheme.colorScheme.primary,
    contentColor: Color = RuntimeTheme.colorScheme.onPrimary,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
    ),
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        content = content,
    )
}

/** @see RuntimeFloatingActionButton */
@Composable
fun RuntimeSmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = RuntimeTheme.colorScheme.primary,
    contentColor: Color = RuntimeTheme.colorScheme.onPrimary,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
    ),
    content: @Composable () -> Unit,
) {
    RuntimeFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        content = content,
    )
}

/**
 * 主题自适应过滤标签 (Miuix selection chip)：
 * - 遵循 M3 规范：高度 32dp，形状 RoundedCornerShape(8.dp)，文字 labelMedium；
 * - 包含 minimumInteractiveComponentSize() 满足 48dp 触控标准；
 */
@Composable
fun RuntimeFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RuntimeTheme.shapes.small,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = FilterChipDefaults.filterChipColors(),
    )
}

