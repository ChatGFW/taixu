package top.wkbin.taixu.harness.session

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.harness.ApiMessage
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.UserMessage

/**
 * LLM 自动会话标题：一个回合结束后，若会话标题仍是
 * 占位符（「新会话」等），用会话绑定的模型生成一个不超过 [MAX_TITLE_CHARS] 字的标题并改名。
 *
 * 契约：进程内每会话只发起一次 LLM 尝试（失败不自动重试，避免每轮都烧 token）；
 * 标题已被用户改过、会话未绑定模型、或首回合没有用户/助手正文时静默跳过；
 * 全部失败路径不抛出（标题生成永远不能干扰会话本身）。
 * 模型解析与补全通过窄函数端口注入，便于在无网络的测试里打桩。
 */
/** 标题生成的模型端口：解析会话绑定的模型并发起一次非流式补全，返回正文（失败抛异常或返回 null）。 */
fun interface SessionTitleModelPort {
    suspend fun complete(modelId: String?, variant: String?, messages: List<ApiMessage>, sessionId: String): String?
}

/** 标题生成的日志端口：Koin verify 不接受 kotlin.Function* 构造参数，日志走具名接口。 */
interface SessionTitleLog {
    fun info(message: String)
    fun error(message: String, failure: Throwable)
}

class SessionTitleGenerator(
    private val sessions: HarnessSessionRepository,
    private val modelPort: SessionTitleModelPort,
    private val log: SessionTitleLog,
) {
    private val attempted = ConcurrentHashMap.newKeySet<String>()

    /** 回合结束时调用；[conversation] 为该会话的当前消息投影。 */
    suspend fun maybeGenerate(
        sessionId: String,
        conversation: List<HarnessMessage>,
        /** 自定义标题提示词；空白用内置默认（偏好注入） */
        promptOverride: String? = null,
        /** 指定标题模型档案 id；空白用会话绑定模型 */
        modelIdOverride: String? = null,
    ) {
        val session = sessions.findById(sessionId) ?: return
        if (!DefaultSessionTitle.isPlaceholder(session.title)) return
        val effectiveModelId = modelIdOverride?.trim()?.takeIf { it.isNotBlank() } ?: session.modelId
        // 未绑定模型档案的会话无法定价生成，直接跳过（不占用尝试额度）。
        if (effectiveModelId.isNullOrBlank()) return
        // 占位符确认后才占用「每会话一次」的尝试额度：老会话（标题已改名）永远不烧调用。
        if (!attempted.add(sessionId)) return
        try {
            val userText = conversation.filterIsInstance<UserMessage>().firstOrNull()
                ?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(SNIPPET_CHARS) ?: return
            val assistantText = conversation.filterIsInstance<AssistantText>().lastOrNull()
                ?.textContent?.toString()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(SNIPPET_CHARS) ?: return
            val prompt = buildString {
                if (promptOverride.isNullOrBlank()) {
                    appendLine("根据下面这轮对话生成一个不超过 16 个字的简短标题，概括用户的核心意图或主题。")
                    appendLine("只输出标题文本本身：不要引号、书名号、句号，不要任何前缀或解释。")
                } else {
                    appendLine(promptOverride.trim())
                }
                appendLine()
                appendLine("用户：$userText")
                appendLine()
                appendLine("助手：$assistantText")
            }
            val body = modelPort.complete(
                effectiveModelId,
                session.modelVariant.takeIf { modelIdOverride.isNullOrBlank() },
                listOf(
                    ApiMessage(role = "system", content = "你是标题生成器，只输出标题文本。"),
                    ApiMessage(role = "user", content = prompt),
                ),
                sessionId,
            )?.trim().orEmpty()
            val title = sanitizeTitle(body)
            if (title.isBlank() || DefaultSessionTitle.isPlaceholder(title)) return
            sessions.rename(sessionId, title, System.currentTimeMillis())
            log.info("会话 $sessionId 自动标题：$title")
        } catch (cancellation: CancellationException) {
            // 协程取消时归还尝试额度，下一次回合结束仍可生成
            attempted.remove(sessionId)
            throw cancellation
        } catch (failure: Throwable) {
            log.error("自动标题生成失败（会话 $sessionId）：${failure.message}", failure)
        }
    }

    private fun sanitizeTitle(raw: String): String {
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        // 先去尾标点再去包裹引号：「标题」。 → 标题
        return line.removeSuffix("。").removeSuffix(".").trim()
            .removeSurrounding("\"").trim()
            .removePrefix("「").removeSuffix("」").removePrefix("『").removeSuffix("』").trim()
            .take(MAX_TITLE_CHARS)
    }

    companion object {
        const val MAX_TITLE_CHARS = 24
        private const val SNIPPET_CHARS = 600
    }
}
