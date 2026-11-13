package top.wkbin.taixu.runtime.virtualdisplay

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

enum class PhoneTaskState { RUNNING, PAUSED, NEEDS_USER, COMPLETED, FAILED, CANCELLED }

enum class PhoneTaskWaitMode { PAUSE, HANDOFF, CONFIRM }

/** 当前等待用户的任务快照（暂停或接手），供分身 Tab 显示继续/停止。 */
data class PhoneTaskWait(
    val sessionId: String,
    val state: PhoneTaskState,
    val mode: PhoneTaskWaitMode = PhoneTaskWaitMode.PAUSE,
)

/**
 * 手机任务全局单飞：任意时刻全进程只允许一个任务。
 * 多聊天会话 / 多 VD session 并行会抢 Surface 与任务拉回，导致黑屏。
 * revision 防止人工操作后执行旧截图的动作。
 */
class PhoneTaskRegistry {
    private val active = ConcurrentHashMap<String, PhoneTaskControl>()
    private val _waiting = MutableStateFlow<PhoneTaskWait?>(null)
    val waiting: StateFlow<PhoneTaskWait?> = _waiting.asStateFlow()
    /** 正在跑、尚未暂停/接手的 session；供 UI 显示「我来」。 */
    private val _runningSession = MutableStateFlow<String?>(null)
    val runningSession: StateFlow<String?> = _runningSession.asStateFlow()

    private val waitModes = ConcurrentHashMap<String, PhoneTaskWaitMode>()

    /** 当前占用手机任务的 VD session；无任务时 null。 */
    fun activeSession(): String? = active.keys.firstOrNull()

    fun hasActive(): Boolean = active.isNotEmpty()

    @Synchronized
    fun start(session: String, job: Job): PhoneTaskControl? {
        if (active.isNotEmpty()) return null
        val task = PhoneTaskControl(job) { state -> onState(session, state) }
        active[session] = task
        onState(session, PhoneTaskState.RUNNING)
        return task
    }

    fun get(session: String): PhoneTaskControl? = active[session]

    @Synchronized
    fun release(session: String, task: PhoneTaskControl) {
        if (active.remove(session, task)) {
            waitModes.remove(session)
            if (_waiting.value?.sessionId == session) _waiting.value = null
            if (_runningSession.value == session) _runningSession.value = null
        }
    }

    fun pause(session: String, manual: Boolean = false) {
        waitModes[session] = PhoneTaskWaitMode.PAUSE
        active[session]?.pause(manual)
    }

    fun requestHandoff(session: String, mode: PhoneTaskWaitMode = PhoneTaskWaitMode.HANDOFF) {
        waitModes[session] = mode
        active[session]?.requestHandoff()
    }

    fun resume(session: String) { active[session]?.resume() }
    fun cancel(session: String) { active[session]?.cancel() }

    private fun onState(session: String, state: PhoneTaskState) {
        _runningSession.value = when (state) {
            PhoneTaskState.RUNNING -> session
            else -> _runningSession.value?.takeIf { it != session }
        }
        _waiting.value = when (state) {
            PhoneTaskState.PAUSED ->
                PhoneTaskWait(session, state, waitModes[session] ?: PhoneTaskWaitMode.PAUSE)
            PhoneTaskState.NEEDS_USER ->
                PhoneTaskWait(session, state, waitModes[session] ?: PhoneTaskWaitMode.HANDOFF)
            else -> {
                waitModes.remove(session)
                _waiting.value?.takeIf { it.sessionId != session }
            }
        }
    }
}

class PhoneTaskControl(
    private val job: Job,
    private val onStateChanged: (PhoneTaskState) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(PhoneTaskState.RUNNING)
    val state: StateFlow<PhoneTaskState> = mutableState.asStateFlow()
    private var revision = 0L
    private var touches = 0L

    @Synchronized fun manualRevision(): Long = touches

    @Synchronized fun pause(manual: Boolean = false) {
        val current = mutableState.value
        if (current == PhoneTaskState.RUNNING || current == PhoneTaskState.PAUSED) {
            if (manual) touches++
            revision++
            setState(PhoneTaskState.PAUSED)
        }
    }

    /** 模型请求接手：挂起循环，等用户点「我做完了」再续跑。 */
    @Synchronized fun requestHandoff() {
        val current = mutableState.value
        if (current == PhoneTaskState.RUNNING ||
            current == PhoneTaskState.PAUSED ||
            current == PhoneTaskState.NEEDS_USER
        ) {
            revision++
            setState(PhoneTaskState.NEEDS_USER)
        }
    }

    @Synchronized fun resume() {
        val current = mutableState.value
        if (current == PhoneTaskState.PAUSED || current == PhoneTaskState.NEEDS_USER) {
            revision++
            setState(PhoneTaskState.RUNNING)
        }
    }

    @Synchronized fun cancel() {
        finish(PhoneTaskState.CANCELLED)
        job.cancel(CancellationException("用户停止了手机操作任务"))
    }

    @Synchronized fun finish(state: PhoneTaskState) {
        revision++
        setState(state)
    }

    @Synchronized fun isCurrent(version: Long): Boolean =
        version == revision && mutableState.value == PhoneTaskState.RUNNING

    suspend fun awaitReady(): Long {
        while (true) {
            currentCoroutineContext().ensureActive()
            state.first { it != PhoneTaskState.PAUSED && it != PhoneTaskState.NEEDS_USER }
            synchronized(this) {
                if (mutableState.value == PhoneTaskState.RUNNING) return revision
                if (mutableState.value == PhoneTaskState.PAUSED ||
                    mutableState.value == PhoneTaskState.NEEDS_USER
                ) {
                    return@synchronized
                }
                throw CancellationException("手机操作任务已结束")
            }
        }
    }

    private fun setState(state: PhoneTaskState) {
        mutableState.value = state
        onStateChanged(state)
    }
}
