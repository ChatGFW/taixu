package top.wkbin.taixu.runtime.virtualdisplay

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 分身任务通知：通道 twin_task_progress|result|need，通知 id 71/72/73。
 * need/pause 可带 Action（拒绝/接受）。
 */
@SuppressLint("MissingPermission")
internal class PhoneTaskNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        ensureChannels()
    }

    fun showProgress(title: String, step: String, stepIndex: Int = 0) {
        // 正文「短标题 · 第 N 步」，标题为任务名。
        val body = buildString {
            val label = step.trim()
            if (label.isNotEmpty()) append(label.take(40))
            if (stepIndex > 0) {
                if (isNotEmpty()) append(" · ")
                append("第").append(stepIndex).append("步")
            }
            if (isEmpty()) append("正在操作")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(smallIcon())
            .setContentTitle(title.ifBlank { "分身任务进行中" })
            .setContentText(body.take(80))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(launchIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(CHANNEL_PROGRESS)
        runCatching {
            manager.cancel(ID_NEED)
            manager.notify(ID_PROGRESS, builder.build())
        }
    }

    fun showProgress(step: String, stepIndex: Int = 0) =
        showProgress("分身任务进行中", step, stepIndex)

    /** 人工触摸暂停：对齐 need Action，继续=accepted、停止=rejected。 */
    fun showPaused(sessionId: String, step: String) {
        val body = step.ifBlank { "人工操作中 · 任务已暂停" }
        val builder = NotificationCompat.Builder(context, CHANNEL_NEED)
            .setSmallIcon(smallIcon())
            .setContentTitle("分身任务已暂停")
            .setContentText(body.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(200)))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(launchIntent())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(CHANNEL_NEED)
            .addAction(0, "停止任务", answerIntent(sessionId, PhoneTaskActionReceiver.KIND_PAUSE, accepted = false))
            .addAction(0, "继续任务", answerIntent(sessionId, PhoneTaskActionReceiver.KIND_PAUSE, accepted = true))
        runCatching {
            manager.cancel(ID_PROGRESS)
            manager.notify(ID_NEED, builder.build())
        }
    }

    fun showNeed(sessionId: String, step: String) {
        val body = step.ifBlank { "登录、验证码或确认" }
        val builder = NotificationCompat.Builder(context, CHANNEL_NEED)
            .setSmallIcon(smallIcon())
            .setContentTitle("分身需要你接手")
            .setContentText(body.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(200)))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(CHANNEL_NEED)
            // 任务已结束时的提示：知道了=关掉通知，打开分身=进 Tab。
            .addAction(0, "知道了", answerIntent(sessionId, PhoneTaskActionReceiver.KIND_NEED, accepted = false))
            .addAction(0, "打开分身", answerIntent(sessionId, PhoneTaskActionReceiver.KIND_NEED, accepted = true))
        runCatching {
            manager.cancel(ID_PROGRESS)
            manager.notify(ID_NEED, builder.build())
        }
    }

    fun showNeed(step: String) = showNeed(VirtualDisplayCoordinator.DEFAULT_SESSION_ID, step)

    /** 进行中的 handoff：接受=继续跑模型，拒绝=停止任务。 */
    fun showHandoff(sessionId: String, step: String) {
        showWaitNeed(
            sessionId = sessionId,
            title = "分身需要你接手",
            body = step.ifBlank { "登录、验证码或确认" },
            detail = "在分身页做完后点「我做完了」，任务会继续。",
            reject = "停止任务",
            accept = "我做完了",
            kind = PhoneTaskActionReceiver.KIND_HANDOFF,
        )
    }

    /** confirm：允许/拒绝。 */
    fun showConfirm(sessionId: String, step: String) {
        showWaitNeed(
            sessionId = sessionId,
            title = "分身需要你确认",
            body = step.ifBlank { "请确认是否继续" },
            detail = "允许后任务继续；拒绝则停止本次手机操作。",
            reject = "拒绝",
            accept = "允许",
            kind = PhoneTaskActionReceiver.KIND_CONFIRM,
        )
    }

    private fun showWaitNeed(
        sessionId: String,
        title: String,
        body: String,
        detail: String,
        reject: String,
        accept: String,
        kind: String,
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_NEED)
            .setSmallIcon(smallIcon())
            .setContentTitle(title)
            .setContentText(body.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText((body.take(160) + "\n" + detail).take(220)))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(launchIntent())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(CHANNEL_NEED)
            .addAction(0, reject, answerIntent(sessionId, kind, accepted = false))
            .addAction(0, accept, answerIntent(sessionId, kind, accepted = true))
        runCatching {
            manager.cancel(ID_PROGRESS)
            manager.notify(ID_NEED, builder.build())
        }
    }

    fun showResult(step: String, success: Boolean, cancelled: Boolean = false) {
        val title = when {
            cancelled -> "分身任务已取消"
            success -> "分身任务完成"
            else -> "分身任务未完成"
        }
        val body = step.ifBlank { title }
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(smallIcon())
            .setContentTitle(title)
            .setContentText(body.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(200)))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(CHANNEL_RESULT)
        runCatching {
            manager.cancel(ID_PROGRESS)
            manager.cancel(ID_NEED)
            manager.notify(ID_RESULT, builder.build())
        }
    }

    fun cancelNeed() {
        runCatching { manager.cancel(ID_NEED) }
    }

    fun cancel() {
        runCatching {
            manager.cancel(ID_PROGRESS)
            manager.cancel(ID_NEED)
            manager.cancel(ID_RESULT)
        }
    }

    private fun smallIcon(): Int =
        context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_notify_sync

    private fun launchIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra(EXTRA_OPEN_TWIN, true)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_LAUNCH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun answerIntent(sessionId: String, kind: String, accepted: Boolean): PendingIntent {
        val intent = Intent(context, PhoneTaskActionReceiver::class.java)
            .setAction(PhoneTaskActionReceiver.ACTION_ANSWER_AGENT_NEED)
            .putExtra(PhoneTaskActionReceiver.EXTRA_ACCEPTED, accepted)
            .putExtra(PhoneTaskActionReceiver.EXTRA_SESSION, sessionId)
            .putExtra(PhoneTaskActionReceiver.EXTRA_KIND, kind)
        val request = REQUEST_CODE_ANSWER + (if (accepted) 1 else 0) + kind.hashCode()
        return PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "分身任务进度", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "手机操作进行中的步骤"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "分身任务结果", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "完成或失败摘要"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NEED, "分身需要接手", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "登录、验证码、确认或人工暂停"
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "twin_task_progress"
        const val CHANNEL_RESULT = "twin_task_result"
        const val CHANNEL_NEED = "twin_task_need"
        const val ID_PROGRESS = 71
        const val ID_RESULT = 72
        const val ID_NEED = 73
        const val EXTRA_OPEN_TWIN = "top.wkbin.taixu.OPEN_TWIN_PHONE"
        private const val REQUEST_CODE_LAUNCH = 0x5048
        private const val REQUEST_CODE_ANSWER = 0x5049
    }
}
