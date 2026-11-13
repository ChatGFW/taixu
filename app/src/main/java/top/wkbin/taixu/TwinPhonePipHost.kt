package top.wkbin.taixu

import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator

/** 把分身 PiP 生命周期从 [MainActivity] 拆出，压文件行数。 */
internal class TwinPhonePipHost(
    private val activity: AppCompatActivity,
) : DefaultLifecycleObserver {
    private var receiver: TwinPhonePipReceiver? = null

    override fun onCreate(owner: LifecycleOwner) {
        if (Build.VERSION.SDK_INT >= 26 && TwinPhonePip.supports(activity)) {
            receiver = TwinPhonePipReceiver.register(activity)
            TwinPhonePip.syncParams(activity)
            activity.lifecycleScope.launch {
                owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val coordinator = runCatching {
                        GlobalContext.get().get<VirtualDisplayCoordinator>()
                    }.getOrNull()
                    if (coordinator == null) return@repeatOnLifecycle
                    combine(
                        TwinPhonePresence.miniPinned,
                        coordinator.phoneTasks.runningSession,
                        coordinator.phoneTasks.waiting,
                    ) { pinned, running, waiting ->
                        pinned || running != null || waiting != null
                    }.collect {
                        TwinPhonePip.syncParams(activity)
                    }
                }
            }
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
        if (Build.VERSION.SDK_INT < 26 || !TwinPhonePip.shouldOfferPip()) return
        // API 31+ 依赖 autoEnter；任务开始后若不刷新 params，Home 仍沿用旧的 false。
        TwinPhonePip.syncParams(activity)
        TwinPhonePip.enter(activity)
    }

    fun onPictureInPictureModeChanged(isInPip: Boolean) {
        TwinPhonePresence.setInPip(isInPip)
        if (Build.VERSION.SDK_INT >= 26) TwinPhonePip.syncParams(activity)
    }

    fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        onPictureInPictureModeChanged(isInPip)
    }
}
