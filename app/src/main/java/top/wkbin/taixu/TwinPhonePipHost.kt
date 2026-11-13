package top.wkbin.taixu

import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence

/** 把分身 PiP 生命周期从 [MainActivity] 拆出，压文件行数。 */
internal class TwinPhonePipHost(
    private val activity: AppCompatActivity,
) : DefaultLifecycleObserver {
    private var receiver: TwinPhonePipReceiver? = null

    override fun onCreate(owner: LifecycleOwner) {
        if (Build.VERSION.SDK_INT >= 26 && TwinPhonePip.supports(activity)) {
            receiver = TwinPhonePipReceiver.register(activity)
            TwinPhonePip.syncParams(activity)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (Build.VERSION.SDK_INT >= 26) TwinPhonePip.syncParams(activity)
        TwinPhonePresence.previewVisible = true
    }

    override fun onDestroy(owner: LifecycleOwner) {
        receiver?.let { runCatching { activity.unregisterReceiver(it) } }
        receiver = null
        TwinPhonePresence.setInPip(false)
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT in 26..30 && TwinPhonePip.shouldOfferPip()) {
            TwinPhonePip.enter(activity)
        }
    }

    fun onPictureInPictureModeChanged(isInPip: Boolean) {
        TwinPhonePresence.setInPip(isInPip)
        if (Build.VERSION.SDK_INT >= 26) TwinPhonePip.syncParams(activity)
    }

    fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        onPictureInPictureModeChanged(isInPip)
    }
}
