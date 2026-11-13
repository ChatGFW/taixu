package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 虚拟屏会话存活期间启停 [TwinDisplayForegroundService]（幂等）。
 */
internal object TwinDisplayKeepAlive {
    private val running = AtomicBoolean(false)

    fun start(context: Context) {
        val app = context.applicationContext
        running.set(true)
        runCatching {
            ContextCompat.startForegroundService(
                app,
                Intent(app, TwinDisplayForegroundService::class.java),
            )
        }.onFailure { running.set(false) }
    }

    fun stop(context: Context) {
        if (!running.getAndSet(false)) return
        val app = context.applicationContext
        runCatching {
            app.startService(
                Intent(app, TwinDisplayForegroundService::class.java)
                    .setAction(TwinDisplayForegroundService.ACTION_STOP),
            )
        }
    }
}
