package top.wkbin.taixu.ui.miuix

import androidx.compose.animation.core.FiniteAnimationSpec
import top.yukonga.miuix.kmp.anim.folmeSpring
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/** miuix has one container radius (16dp, cards, fields, buttons); the M3 large steps collapse onto it. */
@Immutable
data class Shapes(
    val hairline: CornerBasedShape = RoundedCornerShape(2.dp),
    val tiny: CornerBasedShape = RoundedCornerShape(4.dp),
    val extraSmall: CornerBasedShape = RoundedCornerShape(6.dp),
    val chip: CornerBasedShape = RoundedCornerShape(8.dp),
    val small: CornerBasedShape = RoundedCornerShape(12.dp),
    val medium: CornerBasedShape = RoundedCornerShape(16.dp),
    val large: CornerBasedShape = RoundedCornerShape(16.dp),
    val extraLarge: CornerBasedShape = RoundedCornerShape(16.dp),
    val largeIncreased: CornerBasedShape = RoundedCornerShape(16.dp),
    val extraLargeIncreased: CornerBasedShape = RoundedCornerShape(20.dp),
    val extraExtraLarge: CornerBasedShape = RoundedCornerShape(28.dp),
)

/** Native Miuix typography shared by preferences, top bars and application text roles. */
internal val RuntimeTextStyles = defaultTextStyles()

/** M3 role names mapped onto [RuntimeTextStyles]; card content reads like a `BasicComponent`. */
@Immutable
class Typography {
    private val native = RuntimeTextStyles
    val displayLarge = native.title1
    val displayMedium = native.title2
    val displaySmall = native.title3
    val headlineLarge = native.title2.copy(fontWeight = FontWeight.Medium)
    val headlineMedium = native.title3.copy(fontWeight = FontWeight.Medium)
    val headlineSmall = native.title4.copy(fontWeight = FontWeight.Medium)
    val titleLarge = native.title4.copy(fontWeight = FontWeight.Medium)
    val titleMedium = native.headline1.copy(fontWeight = FontWeight.Medium)
    val titleSmall = native.headline2.copy(fontWeight = FontWeight.Medium)
    val bodyLarge = native.main
    val bodyMedium = native.body1
    val bodySmall = native.body2
    val labelLarge = native.body2.copy(fontWeight = FontWeight.Medium)
    val labelMedium = native.footnote1.copy(fontWeight = FontWeight.Medium)
    val labelSmall = native.footnote2.copy(fontWeight = FontWeight.Medium)
}

object RuntimeMotion {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 0.9f, response = 0.35f)
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 0.9f, response = 0.5f)
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 0.9f, response = 0.2f)
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 1f, response = 0.25f)
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 1f, response = 0.18f)
}

private val LocalRuntimeColors = staticCompositionLocalOf { lightColorScheme() }
private val LocalRuntimeShapes = staticCompositionLocalOf { Shapes() }
private val LocalRuntimeTypography = staticCompositionLocalOf { Typography() }
val LocalTextStyle = staticCompositionLocalOf { RuntimeTextStyles.main }

object RuntimeTheme {
    val colorScheme: ColorScheme @Composable get() = LocalRuntimeColors.current
    val shapes: Shapes @Composable get() = LocalRuntimeShapes.current
    val typography: Typography @Composable get() = LocalRuntimeTypography.current
    val motionScheme get() = RuntimeMotion
}

@Composable
fun RuntimeDesignTheme(
    colorScheme: ColorScheme,
    dark: Boolean,
    typography: Typography = Typography(),
    shapes: Shapes = Shapes(),
    content: @Composable () -> Unit,
) {
    val semanticColors = remember(colorScheme, dark) { colorScheme.withMiuixSurfaces(dark) }
    val nativeColors = remember(semanticColors, dark) { semanticColors.toMiuixColors(dark) }
    MiuixTheme(colors = nativeColors, textStyles = RuntimeTextStyles) {
        CompositionLocalProvider(
            LocalRuntimeColors provides semanticColors,
            LocalRuntimeShapes provides shapes,
            LocalRuntimeTypography provides typography,
            LocalTextStyle provides MiuixTheme.textStyles.main,
            content = content,
        )
    }
}
