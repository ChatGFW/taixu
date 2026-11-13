package top.wkbin.taixu

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator
import kotlin.math.roundToInt
import org.koin.core.context.GlobalContext

/**
 * 分身任务画中画：退到桌面/切应用时小窗仍可见（系统 PiP，非悬浮窗权限）。
 */
internal object TwinPhonePip {
    private const val TAG = "TwinPhonePip"
    const val ACTION_PIP_CONTROL = "top.wkbin.taixu.action.PIP_CONTROL"
    const val EXTRA_CONTROL = "control"
    const val CONTROL_PAUSE = "pause"
    const val CONTROL_RESUME = "resume"
    const val CONTROL_STOP = "stop"

    private const val ASPECT_NUM = 516
    private const val ASPECT_DEN = 1000

    fun supports(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return activity.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,
        )
    }

    /** 有手机任务或小窗钉住时允许自动/手动进入 PiP。 */
    fun shouldOfferPip(): Boolean {
        if (TwinPhonePresence.miniPinned.value) return true
        return runCatching {
            GlobalContext.get().get<VirtualDisplayCoordinator>().phoneTasks.hasActive()
        }.getOrDefault(false)
    }

    @RequiresApi(26)
    fun buildParams(
        activity: Activity,
        aspect: Float = ASPECT_NUM / ASPECT_DEN.toFloat(),
        sourceRect: Rect? = null,
        autoEnter: Boolean = shouldOfferPip(),
    ): PictureInPictureParams {
        val safe = aspect.coerceIn(0.42f, 2.38f)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((safe * ASPECT_DEN).roundToInt().coerceAtLeast(1), ASPECT_DEN))
            .setActions(pipActions(activity))
        if (sourceRect != null && !sourceRect.isEmpty) {
            builder.setSourceRectHint(sourceRect)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    @RequiresApi(26)
    fun syncParams(activity: Activity, aspect: Float = TwinPhonePresence.contentAspect) {
        if (!supports(activity)) return
        try {
            val hint = Rect()
            val source = if (TwinPhonePresence.copySourceRectHint(hint)) hint else null
            val ratio = aspect.takeIf { it > 0.05f } ?: (ASPECT_NUM / ASPECT_DEN.toFloat())
            activity.setPictureInPictureParams(
                buildParams(activity, aspect = ratio, sourceRect = source),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "setPictureInPictureParams refused: ${t.message}")
        }
    }

    @RequiresApi(26)
    fun enter(activity: Activity, aspect: Float = TwinPhonePresence.contentAspect): Boolean {
        if (!supports(activity) || !shouldOfferPip()) return false
        return try {
            val hint = Rect()
            val source = if (TwinPhonePresence.copySourceRectHint(hint)) hint else null
            val ratio = aspect.takeIf { it > 0.05f } ?: (ASPECT_NUM / ASPECT_DEN.toFloat())
            activity.enterPictureInPictureMode(
                buildParams(activity, aspect = ratio, sourceRect = source, autoEnter = true),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "enterPictureInPictureMode refused: ${t.message}")
            false
        }
    }

    @RequiresApi(26)
    private fun pipActions(activity: Activity): List<RemoteAction> {
        fun action(control: String, iconRes: Int, label: String): RemoteAction {
            val intent = Intent(ACTION_PIP_CONTROL)
                .setPackage(activity.packageName)
                .putExtra(EXTRA_CONTROL, control)
            val pi = PendingIntent.getBroadcast(
                activity,
                control.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteAction(
                Icon.createWithResource(activity, iconRes),
                label,
                label,
                pi,
            )
        }
        // 复用应用图标作为占位；文案走硬编码短标签，避免再引 string 资源耦合。
        val icon = activity.applicationInfo.icon
        return listOf(
            action(CONTROL_PAUSE, icon, "暂停"),
            action(CONTROL_RESUME, icon, "继续"),
            action(CONTROL_STOP, icon, "停止"),
        )
    }
}

/** PiP 控制条广播：暂停/继续/停止当前手机任务。 */
internal class TwinPhonePipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TwinPhonePip.ACTION_PIP_CONTROL) return
        val control = intent.getStringExtra(TwinPhonePip.EXTRA_CONTROL) ?: return
        val coordinator = runCatching {
            GlobalContext.get().get<VirtualDisplayCoordinator>()
        }.getOrNull() ?: return
        val session = coordinator.phoneTasks.activeSession()
            ?: VirtualDisplayCoordinator.DEFAULT_SESSION_ID
        when (control) {
            TwinPhonePip.CONTROL_PAUSE -> coordinator.takeOverPhoneTask(session)
            TwinPhonePip.CONTROL_RESUME -> coordinator.resumePhoneTask(session)
            TwinPhonePip.CONTROL_STOP -> coordinator.cancelPhoneTask(session)
        }
    }

    companion object {
        fun register(context: Context): TwinPhonePipReceiver {
            val receiver = TwinPhonePipReceiver()
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(TwinPhonePip.ACTION_PIP_CONTROL),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            return receiver
        }
    }
}
