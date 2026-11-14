package top.wkbin.taixu.runtime.virtualdisplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * 虚拟屏存活期间以 SPECIAL_USE 前台服务保活，通知 id=737、低重要度通道。
 * 任务步骤仍走 [PhoneTaskNotifier]（71/72/73），本服务通知只表示后台显示在跑。
 */
class TwinDisplayForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "分身后台显示", NotificationManager.IMPORTANCE_LOW).apply {
                description = "任务期间保持分身虚拟屏在后台存活"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        runCatching { promoteForeground() }
            .onFailure {
                // 无通知权限等情况下仍尽量降级，避免 5s 内未 startForeground 被系统杀。
                runCatching {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
        return START_STICKY
    }

    override fun onTimeout(startId: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTimeout(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(PhoneTaskNotifier.EXTRA_OPEN_TWIN, true)
        }
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                this,
                REQUEST_CODE,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val icon = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_notify_sync
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("分身后台显示")
            .setContentText("虚拟屏运行中 · 点按打开分身")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP = "top.wkbin.taixu.action.STOP_TWIN_DISPLAY"
        /** 避免与任务进度通知 71–73 撞车。 */
        const val NOTIFICATION_ID = 737
        const val CHANNEL_ID = "twin_display"
        private const val REQUEST_CODE = 0x7370
    }
}
