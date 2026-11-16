package top.wkbin.taixu.ui

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import top.wkbin.taixu.testing.Aarch64AwareRobolectricTestRunner

@RunWith(Aarch64AwareRobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class InterfaceScaleTest {
    @Test
    fun dialogDensityFollowsPageScaleAndDoesNotCompound() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val scale = InterfaceScale(activity.resources)
        val systemDpi = activity.resources.configuration.densityDpi

        scale.apply(1.3f)

        val scaled = activity.resources.displayMetrics.density
        val dialog = Dialog(activity)
        assertEquals(scaled, dialog.context.resources.displayMetrics.density, 0.001f)
        assertEquals(scaled, Density(dialog.context).density, 0.001f)
        assertEquals(scale.densityFor(activity.resources.configuration.fontScale).density, scaled, 0.001f)
        assertEquals((systemDpi * 1.3f).roundToInt(), activity.resources.configuration.densityDpi)

        scale.apply(1.3f)
        assertEquals(scaled, activity.resources.displayMetrics.density, 0.001f)

        scale.apply(1f)
        assertEquals(systemDpi, activity.resources.configuration.densityDpi)
        assertEquals(InterfaceScale.dpiToDensity(systemDpi), activity.resources.displayMetrics.density, 0.001f)
    }

    @Test
    fun newInstanceDoesNotTreatScaledResourcesAsSystemBaseline() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val systemDpi = Resources.getSystem().configuration.densityDpi

        val first = InterfaceScale(activity.resources)
        first.apply(1.2f)
        val scaledDpi = activity.resources.configuration.densityDpi

        // 模拟 Activity 因未登记的配置变化重建：Resources 仍是上一次缩放后的对象。
        val second = InterfaceScale(activity.resources)
        second.apply(1.2f)
        assertEquals(scaledDpi, activity.resources.configuration.densityDpi)
        assertEquals((systemDpi * 1.2f).roundToInt(), activity.resources.configuration.densityDpi)
        second.apply(1f)
    }

    @Test
    fun systemConfigurationEchoDoesNotStackScale() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val originalDpi = context.resources.configuration.densityDpi
        val scale = InterfaceScale(context.resources)
        try {
            scale.apply(1.2f)
            val scaledDpi = context.resources.configuration.densityDpi
            val echo = Configuration(context.resources.configuration)
            scale.acceptSystemConfiguration(echo)
            assertEquals(scaledDpi, context.resources.configuration.densityDpi)

            val system = Configuration()
            system.densityDpi = 440
            scale.acceptSystemConfiguration(system)
            assertEquals((440 * 1.2f).roundToInt(), context.resources.configuration.densityDpi)
        } finally {
            val restore = Configuration()
            restore.densityDpi = originalDpi
            scale.acceptSystemConfiguration(restore)
            scale.apply(1f)
        }
    }
}
