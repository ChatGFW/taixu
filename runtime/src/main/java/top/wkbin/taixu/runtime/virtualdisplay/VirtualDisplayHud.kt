package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 分身任务步骤状态：进度走通知 + 分身 Tab，不再挂系统 Overlay 窗。
 * 保留 show/hide API 供旧调用兼容；一律只更新 [step]，不创建 WindowManager 视图。
 */
object VirtualDisplayHud {

    enum class Mode { HIDDEN, MINIMIZED, EXPANDED }

    @Volatile
    var showingSessionId: String? = null
        private set

    @Volatile
    var mode: Mode = Mode.HIDDEN
        private set

    private var stepText: String = "等待操作"
    private val _step = MutableStateFlow(stepText)
    val step: StateFlow<String> = _step.asStateFlow()

    fun setStep(step: String) {
        val text = step.trim().ifBlank { "等待操作" }
        stepText = text
        _step.value = text
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun show(context: Context, sessionId: String, coordinator: VirtualDisplayCoordinator) {
        present(sessionId)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun showMinimized(context: Context, sessionId: String, coordinator: VirtualDisplayCoordinator) {
        present(sessionId)
    }

    @Synchronized
    fun minimize() {
        if (mode != Mode.HIDDEN) mode = Mode.MINIMIZED
        setStep(stepText)
    }

    @Synchronized
    fun expand() {
        TwinPhonePresence.requestOpen()
        minimize()
    }

    @Synchronized
    fun hide() {
        showingSessionId = null
        mode = Mode.HIDDEN
    }

    private fun present(sessionId: String) {
        TwinPhonePresence.requestOpen()
        showingSessionId = sessionId
        mode = Mode.MINIMIZED
        setStep(stepText)
    }
}
