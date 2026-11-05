package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.harness.session.InteractiveSessionControl

/** Main-thread startup coordinator shared by the full-screen and floating chat ViewModels. */
class ChatSessionStartup(
    private val sessions: HarnessSessionRepository,
    private val control: InteractiveSessionControl,
) {
    private val initializationMutex = Mutex()
    private val initializations = mutableSetOf<Job>()
    private var userSelections = 0

    suspend fun restore(preferredSessionId: String, defaultTitle: String) {
        initializationMutex.withLock {
            if (userSelections > 0 || control.currentSessionId.value.isNotBlank()) return
            val initialization = currentCoroutineContext().job
            initializations += initialization
            try {
                val available = sessions.observeAll().first()
                currentCoroutineContext().ensureActive()
                if (userSelections > 0 || control.currentSessionId.value.isNotBlank()) return
                val preferred = available.firstOrNull { it.id == preferredSessionId }
                val latest = available.maxWithOrNull(compareBy({ it.updatedAt }, { it.createdAt }, { it.id }))
                val target = preferred ?: latest
                if (target == null) control.newSession(defaultTitle)
                else control.loadSession(target.id)
            } finally {
                initializations -= initialization
            }
        }
    }

    /** Explicit navigation takes ownership before its coroutine can suspend. */
    fun select(scope: CoroutineScope, action: suspend () -> Unit): Job {
        if (!scope.isActive) return scope.launch { }
        userSelections++
        initializations.toList().forEach { it.cancel() }
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { action() }
            finally { userSelections-- }
        }
    }
}
