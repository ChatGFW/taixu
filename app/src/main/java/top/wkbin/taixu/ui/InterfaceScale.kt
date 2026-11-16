package top.wkbin.taixu.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

/**
 * 把设置里的画面缩放写进 Activity 的 [DisplayMetrics] 和 [Configuration.densityDpi]。
 *
 * 主界面在组合里覆盖 LocalDensity；Dialog、Popup、BottomSheet 各自新建 ComposeView，会用
 * Context 的显示密度重新提供 LocalDensity，把那层覆盖盖掉。密度必须提前写进 Resources，
 * 这些窗口创建时才能和主界面用同一套缩放。
 *
 * 系统基线取自 [Resources.getSystem]，而不是 Activity 的 Resources：后者一旦被本类改过，
 * Activity 因 locale 等未登记配置重建时会把缩放结果误认成系统 dpi，导致重复乘算。
 * 所有写入只能在组合外（流收集 / 生命周期回调）调用，避免组合期副作用。
 */
internal class InterfaceScale(private val resources: Resources) {
    private var systemDensityDpi: Int = systemResourcesDpi() ?: appDensityDpi()
    private var lastKnownSystemDpi: Int = systemDensityDpi
    private var lastAppliedDpi: Int = targetDpi(1f)

    /** 有效缩放；只由 [apply] 更新，保证 Resources 与 Compose Density 使用同一份值。 */
    var scale: Float = 1f
        private set

    fun densityFor(fontScale: Float, scaleOverride: Float = scale): Density = Density(
        density = dpiToDensity(targetDpi(scaleOverride)),
        fontScale = fontScale,
    )

    /**
     * 写入 Resources。必须在组合外调用。返回值表示 Resources 的显示密度是否真的变化，
     * 调用方可据此决定是否要让 Compose 重新计算 Density。
     */
    fun apply(requested: Float): Boolean {
        val previousScale = scale
        scale = requested.coerceIn(MIN_SCALE, MAX_SCALE)
        val metrics = resources.displayMetrics
        val config = resources.configuration
        val dpi = targetDpi(scale)
        lastAppliedDpi = dpi
        // DENSITY_DEFAULT_SCALE 是 @hide，release 编不过；等价于 1/DENSITY_DEFAULT。
        val density = dpiToDensity(dpi)
        if (metrics.densityDpi == dpi && config.densityDpi == dpi && metrics.density == density) {
            return previousScale != scale
        }
        metrics.density = density
        metrics.densityDpi = dpi
        @Suppress("DEPRECATION")
        metrics.scaledDensity = density * (config.fontScale.takeIf { it > 0f } ?: 1f)
        config.densityDpi = dpi
        return true
    }

    /**
     * 系统配置回调。系统资源里的 dpi 是权威基线；测试或兼容模式下系统资源不变时再退回回调值。
     * 缩放后的 dpi 回声不能写回基线，否则下一次 apply 会叠乘。
     */
    fun acceptSystemConfiguration(config: Configuration): Boolean {
        val systemDpi = systemResourcesDpi()
        if (systemDpi != null && systemDpi != lastKnownSystemDpi) {
            systemDensityDpi = systemDpi
            lastKnownSystemDpi = systemDpi
        } else {
            val incoming = config.densityDpi
            if (incoming != Configuration.DENSITY_DPI_UNDEFINED && incoming != lastAppliedDpi) {
                systemDensityDpi = incoming
            }
        }
        return apply(scale)
    }

    private fun systemResourcesDpi(): Int? =
        Resources.getSystem().configuration.densityDpi
            .takeIf { it != Configuration.DENSITY_DPI_UNDEFINED && it > 0 }

    private fun appDensityDpi(): Int =
        resources.displayMetrics.densityDpi.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT

    private fun targetDpi(requested: Float): Int =
        (systemDensityDpi * requested.coerceIn(MIN_SCALE, MAX_SCALE)).roundToInt().coerceAtLeast(1)

    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.3f

        fun dpiToDensity(dpi: Int): Float = dpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    }
}
