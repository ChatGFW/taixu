package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.graphics.SurfaceTexture
import androidx.test.core.app.ApplicationProvider
import com.ai.assistance.showerclient.ShowerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwinPhoneEmbedViewTest {
    @Test fun resizingPreviewRestoresDisplayBufferEvenWhenDisplaySizeIsUnchanged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = TwinPhoneEmbedView(context)
        val controller = ShowerController()
        setField(controller, "videoWidth", 1088)
        setField(controller, "videoHeight", 2400)
        setField(view, "boundController", controller)
        // No Binder calls or server startup: only exercise the synchronous surface callbacks.
        setField(view, "scope", CoroutineScope(Job().apply { cancel() }))
        val texture = RecordingSurfaceTexture()
        try {
            view.layout(0, 0, 432, 953)
            view.onSurfaceTextureAvailable(texture, 432, 953)
            assertEquals(1088 to 2400, texture.bufferSize)

            for ((width, height) in listOf(928 to 2047, 432 to 953, 640 to 1412)) {
                view.layout(0, 0, width, height)
                // Framework writes the new View size before delivering this callback.
                texture.setDefaultBufferSize(width, height)
                view.onSurfaceTextureSizeChanged(texture, width, height)
                assertEquals(1088 to 2400, texture.bufferSize)
            }
        } finally {
            view.onSurfaceTextureDestroyed(texture)
            texture.release()
        }
    }

    private class RecordingSurfaceTexture : SurfaceTexture(0) {
        var bufferSize = 0 to 0
        override fun setDefaultBufferSize(width: Int, height: Int) {
            super.setDefaultBufferSize(width, height)
            bufferSize = width to height
        }
    }

    private fun setField(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
}
