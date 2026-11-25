package top.wkbin.taixu.harness.codex

import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentFactory
import top.wkbin.taixu.runtime.shell.SessionConfig

data class CodexWorkspace(val project: String, val distribution: String, val directory: String)

data class CodexLine(val id: String, val user: Boolean, val text: String)

data class CodexApproval(val token: String, val fileChange: Boolean, val summary: String)

sealed interface CodexInstruction {
    data class Decline(val token: String) : CodexInstruction
    data object Interrupt : CodexInstruction
}

enum class CodexEnd { COMPLETED, INTERRUPTED, FAILED }

sealed interface CodexEvent {
    data class Started(val turnId: String) : CodexEvent
    data class Message(val line: CodexLine) : CodexEvent
    data class Approvals(val requests: List<CodexApproval>) : CodexEvent
    data object Interrupting : CodexEvent
    data class Finished(val outcome: CodexEnd) : CodexEvent
}

/** Foreground project chat with its own CLI history. No TaiXu model credentials or tools enter the protocol. */
class WorkspaceCodexBackend(
    private val projects: WorkspaceManager,
    private val runtime: LinuxRuntime,
    private val localEnvironments: ExecutionEnvironmentFactory,
    private val redactor: SensitiveDataRedactor,
) {
    suspend fun bind(project: String): CodexWorkspace {
        val directory = projects.linuxWorkingDirectory(project)
        val distribution = runtime.activeDistroId.value
        if (!directory.startsWith("/workspace/") || distribution.isBlank()) {
            throw CodexException(CodexFailure.WORKSPACE)
        }
        return CodexWorkspace(project, distribution, directory)
    }

    suspend fun history(target: CodexWorkspace, threadId: String): List<CodexLine> =
        withCodex(target) { wire, redact ->
            val result = wire.request("thread/read", buildJsonObject {
                put("threadId", threadId)
                put("includeTurns", false)
            }, wire::reject)
            val thread = result.obj("thread") ?: throw CodexException(CodexFailure.HISTORY)
            if (thread.str("id") != threadId || thread.str("cwd") != target.directory) {
                throw CodexException(CodexFailure.WORKSPACE)
            }
            val page = wire.request("thread/turns/list", buildJsonObject {
                put("threadId", threadId)
                put("limit", 12)
                put("itemsView", "full")
            }, wire::reject)
            page.arr("data").asReversed().flatMap { turn ->
                (turn as? JsonObject)?.arr("items").orEmpty().mapNotNull { item ->
                    (item as? JsonObject)?.toCodexLine(redact)
                }
            }.takeLast(64)
        }

    suspend fun turn(
        target: CodexWorkspace,
        previousThread: String?,
        prompt: String,
        control: ReceiveChannel<CodexInstruction>,
        rememberThread: suspend (String) -> Unit,
        emit: (CodexEvent) -> Unit,
    ) {
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARS)
        withCodex(target) { wire, redact ->
            val thread = prepareThread(wire, target, previousThread)
            val relay = CodexTurnRelay(wire, thread.id, redact, emit)
            relay.run(codexTurnParams(thread.id, target.directory, thread.sandbox, prompt), control) {
                if (previousThread == null) rememberThread(thread.id)
            }
        }
    }

    private suspend fun prepareThread(wire: CodexWire, target: CodexWorkspace, prior: String?): PreparedThread {
        val result = wire.request(if (prior == null) "thread/start" else "thread/resume", buildJsonObject {
            put("cwd", target.directory)
            put("sandbox", "workspace-write")
            put("approvalPolicy", "on-request")
            put("approvalsReviewer", "user")
            if (prior != null) {
                put("threadId", prior)
                put("excludeTurns", true)
            }
        }, wire::reject)
        val thread = result.obj("thread") ?: throw CodexException(CodexFailure.PROTOCOL)
        val id = thread.str("id")?.takeIf(::isCodexThreadId) ?: throw CodexException(CodexFailure.PROTOCOL)
        if ((prior != null && id != prior) || thread.str("cwd") != target.directory) {
            throw CodexException(CodexFailure.WORKSPACE)
        }
        val sandbox = result.obj("sandbox") ?: throw CodexException(CodexFailure.SANDBOX)
        val type = sandbox.str("type")
        if (type !in setOf("workspaceWrite", "readOnly") ||
            (sandbox.has("networkAccess") && sandbox["networkAccess"]?.toString() != "false") ||
            sandbox.arr("writableRoots").any { it != JsonPrimitive(target.directory) } ||
            result.arr("runtimeWorkspaceRoots").any { it != JsonPrimitive(target.directory) } ||
            result.str("approvalPolicy") != "on-request" || result.str("approvalsReviewer") != "user"
        ) {
            throw CodexException(CodexFailure.SANDBOX)
        }
        return PreparedThread(id, requireNotNull(type))
    }

    private suspend fun <T> withCodex(
        target: CodexWorkspace,
        block: suspend (CodexWire, (String) -> String) -> T,
    ): T = coroutineScope {
        if (runtime.installedDistros.value.none { it.id == target.distribution }) {
            throw CodexException(CodexFailure.RUNTIME)
        }
        val env = localEnvironments.open("workspace-codex:${UUID.randomUUID()}", target.directory, target.distribution)
        try {
            if (env.id.backend != "local-proot" || env.id.distribution != target.distribution ||
                env.id.workspace != target.directory
            ) throw CodexException(CodexFailure.WORKSPACE)
            val secrets = env.outputSecrets().filter { it.isNotBlank() }.sortedByDescending(String::length)
            val session = env.openSession(SessionConfig(
                workingDirectory = target.directory,
                commandLine = CODEX_COMMAND,
                allowSttyResize = false,
                usePty = false,
            ))
            val wire = CodexWire(this, session)
            try {
                wire.initialize()
                val account = wire.request("account/read", buildJsonObject {
                    put("refreshToken", false)
                }, wire::reject)
                if (account.obj("account") == null && account["requiresOpenaiAuth"]?.toString() != "false") {
                    throw CodexException(CodexFailure.AUTH)
                }
                block(wire) { raw ->
                    secrets.fold(redactor.redact(raw)) { value, secret -> value.replace(secret, "[REDACTED]") }
                }
            } finally {
                wire.stopReader()
            }
        } finally {
            withContext(NonCancellable) { env.close() }
        }
    }

    private data class PreparedThread(val id: String, val sandbox: String)

    companion object {
        const val MAX_PROMPT_CHARS = 16_384
        // Fixed local command, scrub environment before exec and keep stderr off the JSON channel.
        internal const val CODEX_COMMAND =
            "exec /usr/bin/env -i HOME=/root CODEX_HOME=/opt/taixu/data/codex LANG=C.UTF-8 " +
                "PATH=/opt/taixu/bin:/usr/local/bin:/usr/bin:/bin " +
                "/opt/taixu/bin/codex app-server --listen stdio:// " +
                "-c 'shell_environment_policy.inherit=\"none\"' " +
                "-c 'sandbox_workspace_write.network_access=false' 2>/dev/null"
    }
}

fun isCodexThreadId(id: String): Boolean = id.length in 1..128 && id.all {
    it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_'
}

internal const val CODEX_MAX_TEXT = 24_576

internal fun JsonObject.toCodexLine(redact: (String) -> String): CodexLine? {
    val id = str("id") ?: return null
    val user = str("type") == "userMessage"
    val raw = when (str("type")) {
        "userMessage" -> arr("content").asSequence().take(16).mapNotNull {
            (it as? JsonObject)?.str("text")?.take(CODEX_MAX_TEXT)
        }.joinToString("\n")
        "agentMessage" -> str("text").orEmpty()
        else -> return null
    }
    if (raw.isBlank()) return null
    return CodexLine(id, user, redact(raw.take(CODEX_MAX_TEXT)).take(CODEX_MAX_TEXT))
}

internal fun codexTurnParams(threadId: String, directory: String, sandboxType: String, prompt: String) = buildJsonObject {
    put("threadId", threadId)
    put("cwd", directory)
    put("approvalPolicy", "on-request")
    put("approvalsReviewer", "user")
    put("sandboxPolicy", buildJsonObject {
        put("type", sandboxType)
        put("networkAccess", false)
        if (sandboxType == "workspaceWrite") {
            put("excludeSlashTmp", true)
            put("excludeTmpdirEnvVar", true)
            put("writableRoots", buildJsonArray { })
        }
    })
    put("input", buildJsonArray {
        add(buildJsonObject {
            put("type", "text")
            put("text", prompt)
        })
    })
}
