package top.wkbin.taixu.runtime.webchat

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 用户开启协作台期间保持 HTTP 接收线程可调度，避免后台进程冻结。 */
class WebChatForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        val pin = intent?.getStringExtra("pin")
        if (url == null || pin == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(WebBridgeNotification.NOTIFICATION_ID,
            WebBridgeNotification(this).createNotification(url, pin))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
