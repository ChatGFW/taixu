package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage

/** Baseline existing transcripts; only completed new replies enter the speech queue. */
internal class ChatSpeechGate {
    var sessionId: String? = null
        private set
    private var enabled = false
    private var since = 0L
    private val seen = mutableSetOf<String>()
    private val pending = mutableListOf<String>()

    fun update(session: String, messages: List<HarnessMessage>, running: Boolean, active: Boolean) {
        if (sessionId != session || enabled != active) {
            sessionId = session
            enabled = active
            since = System.currentTimeMillis()
            seen.clear()
            seen.addAll(messages.map { it.id })
            pending.clear()
            return
        }
        if (!active || running) return
        messages.filterIsInstance<AssistantText>().forEach { message ->
            if (seen.add(message.id) && message.createdAt >= since) {
                speechText(message.text).takeIf(String::isNotBlank)?.let(pending::add)
            }
        }
    }

    fun drain(): List<String> = pending.toList().also { pending.clear() }
}

/** Keep prose, avoid reading code blocks, image URLs and Markdown punctuation aloud. */
internal fun speechText(text: String): String = text
    .replace(REASONING_BLOCK, " ")
    .replace(FENCED_CODE, " ")
    .replace(IMAGE_MARKDOWN, " ")
    .replace(LINK_MARKDOWN, "$1")
    .replace(LINE_PREFIX, "")
    .replace(INLINE_MARKUP, "")
    .trim()

// 这些模式是编译期常量：原来每次朗读都现编 6 个 Regex。
private val REASONING_BLOCK = Regex("(?s)<think>.*?(?:</think>|$)")
private val FENCED_CODE = Regex("(?s)```.*?```|~~~.*?~~~")
private val IMAGE_MARKDOWN = Regex("!\\[[^]]*]\\([^)]*\\)")
private val LINK_MARKDOWN = Regex("\\[([^]]+)]\\([^)]*\\)")
private val LINE_PREFIX = Regex("(?m)^\\s{0,3}(#{1,6}\\s+|>\\s*|[-*+]\\s+)")
private val INLINE_MARKUP = Regex("[`*_~]")
