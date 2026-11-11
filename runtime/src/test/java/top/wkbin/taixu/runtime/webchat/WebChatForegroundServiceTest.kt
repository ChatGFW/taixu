package top.wkbin.taixu.runtime.webchat

import android.app.Application
import android.app.Service
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class WebChatForegroundServiceTest {
    @Test
    fun `bridge notification starts and stops a real foreground service`() {
        val controller = Robolectric.buildService(WebChatForegroundService::class.java).create()
        val service = controller.get()
        val notification = WebBridgeNotification(service)
        try {
            notification.show("http://192.168.1.2:8899", "123456")
            val intent = shadowOf(service).nextStartedService
            assertEquals(WebChatForegroundService::class.java.name, intent.component?.className)
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(intent, 0, 1))
            assertEquals(WebBridgeNotification.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
            assertNotNull(shadowOf(service).lastForegroundNotification)
            notification.hide()
            assertEquals(WebChatForegroundService::class.java.name,
                shadowOf(service).nextStoppedService.component?.className)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `service does not resurrect a stopped bridge`() {
        val controller = Robolectric.buildService(WebChatForegroundService::class.java).create()
        try {
            val service = controller.get()
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent(), 0, 1))
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }
}
