package top.wkbin.taixu.ui.workspace.codex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.wkbin.taixu.harness.codex.CodexApproval
import top.wkbin.taixu.harness.codex.CodexEnd
import top.wkbin.taixu.harness.codex.CodexEvent
import top.wkbin.taixu.harness.codex.CodexException
import top.wkbin.taixu.harness.codex.CodexFailure
import top.wkbin.taixu.harness.codex.CodexInstruction
import top.wkbin.taixu.harness.codex.CodexLine
import top.wkbin.taixu.harness.codex.CodexWorkspace
import top.wkbin.taixu.harness.codex.WorkspaceCodexBackend

internal data class WorkspaceCodexState(
    val target: CodexWorkspace? = null,
    val lines: List<CodexLine> = emptyList(),
    val approvals: List<CodexApproval> = emptyList(),
    val phase: CodexPhase = CodexPhase.LOADING,
    val failure: CodexFailure? = null,
    val outcome: CodexEnd? = null,
    val draft: String = "",
    val canReset: Boolean = false,
) {
    val canSend: Boolean
        get() = phase == CodexPhase.READY && target != null && draft.isNotBlank() &&
            failure != CodexFailure.HISTORY && failure != CodexFailure.STORAGE
}

internal enum class CodexPhase { LOADING, READY, STARTING, RUNNING, STOPPING }

internal class WorkspaceCodexViewModel(
    private val backend: WorkspaceCodexBackend,
    private val store: WorkspaceCodexThreadStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(WorkspaceCodexState())
    val state = mutable.asStateFlow()
    private var initialized = false
    private var threadId: String? = null
    private var operation: Job? = null
    private var control: Channel<CodexInstruction>? = null

    fun initialize(project: String) {
        if (initialized) return
        initialized = true
        retry(project)
    }

    fun retry(project: String) {
        if (operation?.isActive == true) return
        mutable.update { it.copy(phase = CodexPhase.LOADING, failure = null, outcome = null) }
        operation = viewModelScope.launch {
            try {
                val target = mutable.value.target ?: backend.bind(project)
                mutable.update { it.copy(target = target, canReset = true) }
                threadId = null
                val stored = store.read(target)
                threadId = stored
                val lines = stored?.let { backend.history(target, it) }.orEmpty()
                mutable.update { it.copy(lines = lines, canReset = stored != null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(failure = failure.code(CodexFailure.HISTORY)) }
            } finally {
                mutable.update { it.copy(phase = CodexPhase.READY) }
            }
        }
    }

    fun edit(text: String) {
        mutable.update { it.copy(draft = text.take(WorkspaceCodexBackend.MAX_PROMPT_CHARS)) }
    }

    fun send() {
        val current = mutable.value
        val target = current.target ?: return
        if (operation?.isActive == true || !current.canSend) return
        val prompt = current.draft.trim()
        val input = Channel<CodexInstruction>(capacity = 8)
        control = input
        mutable.update { it.copy(phase = CodexPhase.STARTING, failure = null, outcome = null) }
        operation = viewModelScope.launch {
            try {
                backend.turn(target, threadId, prompt, input, rememberThread = { id ->
                    try {
                        store.write(target, id)
                        threadId = id
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        throw CodexException(CodexFailure.STORAGE)
                    }
                }) { event ->
                    when (event) {
                        is CodexEvent.Started -> mutable.update {
                            it.copy(
                                phase = if (it.phase == CodexPhase.STOPPING) CodexPhase.STOPPING else CodexPhase.RUNNING,
                                draft = if (it.draft.trim() == prompt) "" else it.draft,
                                lines = (it.lines + CodexLine("user:${event.turnId}", true, prompt)).takeLast(64),
                                canReset = true,
                            )
                        }
                        is CodexEvent.Message -> mutable.update { it.copy(lines = upsert(it.lines, event.line)) }
                        is CodexEvent.Approvals -> mutable.update { it.copy(approvals = event.requests) }
                        is CodexEvent.Finished -> mutable.update {
                            it.copy(outcome = event.outcome, phase = CodexPhase.STOPPING)
                        }
                        CodexEvent.Interrupting -> mutable.update { it.copy(phase = CodexPhase.STOPPING) }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (mutable.value.phase == CodexPhase.STOPPING) mutable.update { it.copy(outcome = CodexEnd.INTERRUPTED) }
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(failure = failure.code(CodexFailure.PROTOCOL)) }
            } finally {
                input.cancel()
                control = null
                mutable.update { it.copy(phase = CodexPhase.READY, approvals = emptyList()) }
            }
        }
    }

    fun decline(token: String) {
        if (mutable.value.approvals.any { it.token == token }) {
            control?.trySend(CodexInstruction.Decline(token))
        }
    }

    fun stop() {
        val running = operation ?: return
        val phase = mutable.value.phase
        if (phase !in setOf(CodexPhase.STARTING, CodexPhase.RUNNING)) return
        mutable.update { it.copy(phase = CodexPhase.STOPPING) }
        if (phase == CodexPhase.STARTING || control?.trySend(CodexInstruction.Interrupt)?.isSuccess != true) {
            running.cancel()
            return
        }
        viewModelScope.launch {
            delay(5_000)
            if (operation === running && running.isActive) running.cancel()
        }
    }

    fun newConversation() {
        val target = mutable.value.target ?: return
        if (operation?.isActive == true) return
        mutable.update { it.copy(phase = CodexPhase.LOADING) }
        operation = viewModelScope.launch {
            try {
                store.forget(target)
                threadId = null
                mutable.value = WorkspaceCodexState(target = target, phase = CodexPhase.READY)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(phase = CodexPhase.READY, failure = CodexFailure.STORAGE) }
            }
        }
    }

    private fun Exception.code(fallback: CodexFailure) = (this as? CodexException)?.failure ?: fallback

    private fun upsert(lines: List<CodexLine>, line: CodexLine): List<CodexLine> {
        val existing = lines.indexOfFirst { it.id == line.id && !it.user }
        return if (existing < 0) (lines + line).takeLast(64)
        else lines.toMutableList().also { it[existing] = line }
    }
}
