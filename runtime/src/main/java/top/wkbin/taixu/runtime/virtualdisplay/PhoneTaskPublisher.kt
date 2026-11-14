package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.content.Intent

/**
 * 分身任务通知与暂停/继续入口（从 [VirtualDisplayCoordinator] 拆出，压尺寸棘轮）。
 */
internal class PhoneTaskPublisher(
    private val context: Context,
    private val phoneTasks: PhoneTaskRegistry,
) {
    private val notifier by lazy { PhoneTaskNotifier(context.applicationContext) }
    @Volatile
    private var taskTitle: String = DEFAULT_TITLE

    fun installBridge() {
        PhoneTaskActionBridge.install { appContext, session, kind, accepted ->
            answer(appContext, session, kind, accepted)
        }
    }

    /** 进度通知标题用整单任务名，步骤文案放正文。 */
    fun setTaskTitle(title: String?) {
        taskTitle = title?.trim()?.take(40)?.ifBlank { DEFAULT_TITLE } ?: DEFAULT_TITLE
    }

    fun progress(step: String, stepIndex: Int = 0) {
        VirtualDisplayHud.setStep(step)
        notifier.showProgress(taskTitle, step, stepIndex)
    }

    fun paused(
        sessionId: String = VirtualDisplayCoordinator.DEFAULT_SESSION_ID,
        step: String = "人工操作中 · 任务已暂停",
    ) {
        TwinPhonePresence.requestOpen()
        VirtualDisplayHud.setStep(step)
        notifier.showPaused(sessionId, step)
        VirtualDisplayHud.hide()
    }

    fun need(
        step: String,
        sessionId: String = VirtualDisplayCoordinator.DEFAULT_SESSION_ID,
    ) {
        TwinPhonePresence.requestOpen()
        VirtualDisplayHud.setStep(step)
        notifier.showNeed(sessionId, step)
        VirtualDisplayHud.hide()
    }

    /** 用户主动接管：暂停 agent 循环。 */
    fun takeOver(
        sessionId: String = VirtualDisplayCoordinator.DEFAULT_SESSION_ID,
        step: String = "你正在操作 · 接管中",
    ) {
        phoneTasks.pause(sessionId, manual = true)
        paused(sessionId, step)
    }

    /** 模型请求接手：挂起任务，通知栏「我做完了」=resume、「停止」=cancel。 */
    fun handoff(
        sessionId: String = VirtualDisplayCoordinator.DEFAULT_SESSION_ID,
        step: String,
    ) {
        TwinPhonePresence.requestOpen()
        VirtualDisplayHud.setStep(step)
        notifier.showHandoff(sessionId, step)
        VirtualDisplayHud.hide()
    }

    fun confirm(
        sessionId: String = VirtualDisplayCoordinator.DEFAULT_SESSION_ID,
        step: String,
    ) {
        TwinPhonePresence.requestOpen()
        VirtualDisplayHud.setStep(step)
        notifier.showConfirm(sessionId, step)
        VirtualDisplayHud.hide()
    }

    fun result(step: String, success: Boolean, cancelled: Boolean = false) {
        VirtualDisplayHud.setStep(step)
        notifier.showResult(step, success, cancelled)
        TwinPhonePresence.showResultSticky(step, success = success, cancelled = cancelled)
        VirtualDisplayHud.hide()
        setTaskTitle(null)
    }

    fun resume(sessionId: String = VirtualDisplayCoordinator.DEFAULT_SESSION_ID) {
        phoneTasks.resume(sessionId)
        progress("继续操作")
    }

    fun cancel(sessionId: String = VirtualDisplayCoordinator.DEFAULT_SESSION_ID) {
        phoneTasks.cancel(sessionId)
        result("任务已停止", success = false, cancelled = true)
        setTaskTitle(null)
    }

    fun cancelAllNotifications() {
        notifier.cancel()
        setTaskTitle(null)
    }

    companion object {
        private const val DEFAULT_TITLE = "分身任务进行中"
    }

    private fun answer(
        appContext: Context,
        sessionId: String,
        kind: String,
        accepted: Boolean,
    ) {
        when (kind) {
            PhoneTaskActionReceiver.KIND_PAUSE,
            PhoneTaskActionReceiver.KIND_CONFIRM,
            PhoneTaskActionReceiver.KIND_HANDOFF,
            -> {
                if (accepted) resume(sessionId) else cancel(sessionId)
            }
            else -> {
                if (accepted) {
                    TwinPhonePresence.requestOpen()
                    appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.let { launch ->
                        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                        launch.putExtra(PhoneTaskNotifier.EXTRA_OPEN_TWIN, true)
                        runCatching { appContext.startActivity(launch) }
                    }
                } else {
                    notifier.cancelNeed()
                }
            }
        }
    }
}
