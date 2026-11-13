package top.wkbin.taixu.runtime.virtualdisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 通知栏 Action：继续/停止暂停中的手机任务，或打开分身。
 * 通过 [PhoneTaskActionBridge] 回调到 [VirtualDisplayCoordinator]，避免 BroadcastReceiver 直接依赖 Koin。
 */
class PhoneTaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ANSWER_AGENT_NEED) return
        val accepted = intent.getBooleanExtra(EXTRA_ACCEPTED, false)
        val session = intent.getStringExtra(EXTRA_SESSION)
            ?.takeIf { it.isNotBlank() }
            ?: VirtualDisplayCoordinator.DEFAULT_SESSION_ID
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_NEED
        PhoneTaskActionBridge.dispatch(context.applicationContext, session, kind, accepted)
    }

    companion object {
        const val ACTION_ANSWER_AGENT_NEED = "top.wkbin.taixu.action.ANSWER_AGENT_NEED"
        const val EXTRA_ACCEPTED = "accepted"
        const val EXTRA_SESSION = "session"
        const val EXTRA_KIND = "kind"
        const val KIND_PAUSE = "pause"
        /** confirm / handoff 与 pause 同语义：accepted=resume，拒绝=cancel。 */
        const val KIND_CONFIRM = "confirm"
        const val KIND_HANDOFF = "handoff"
        const val KIND_NEED = "need"
        const val KIND_TAKEOVER = "takeover"
    }
}

/** Coordinator 在构造时登记，进程内单例回调。 */
internal object PhoneTaskActionBridge {
    @Volatile
    private var handler: ((Context, String, String, Boolean) -> Unit)? = null

    fun install(block: (Context, String, String, Boolean) -> Unit) {
        handler = block
    }

    fun dispatch(context: Context, session: String, kind: String, accepted: Boolean) {
        handler?.invoke(context, session, kind, accepted)
    }
}
