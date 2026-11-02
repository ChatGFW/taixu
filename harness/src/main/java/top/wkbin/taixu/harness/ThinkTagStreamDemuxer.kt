package top.wkbin.taixu.harness

/**
 * 流式 chunk 的 <think> 标签与思考/正文智能分流器。
 * 兼容：原生 reasoning_chunk、在 content 中输出 <think>...</think> 标签以及跨 chunk 标签碎片。
 * 内置思维链死循环/重复自旋检测与保护机制。
 */
internal class ThinkTagStreamDemuxer(
    private val onReasoning: (String) -> Unit,
    private val onDelta: (String) -> Unit,
    private val maxReasoningChars: Int = ProviderClient.MAX_STREAM_REASONING_CHARS,
    /** 跨协议流式输出硬失败预算；非空时正文/思考增量计入，超限抛异常终止本轮请求。 */
    private val budget: StreamBudget? = null,
) {
    val fullText = StringBuilder()
    val fullReasoning = StringBuilder()

    private var inThinkTag = false
    private val pendingBuffer = StringBuilder()
    private var reasoningMutedDueToLoop = false

    fun onExplicitReasoningChunk(chunk: String) {
        if (chunk.isEmpty()) return
        appendReasoning(chunk)
    }

    fun onContentChunk(chunk: String) {
        if (chunk.isEmpty()) return
        pendingBuffer.append(chunk)
        processPending()
    }

    fun flush() {
        if (pendingBuffer.isNotEmpty()) {
            val leftover = pendingBuffer.toString()
            pendingBuffer.clear()
            if (inThinkTag) {
                appendReasoning(leftover)
            } else {
                appendText(leftover)
            }
        }
    }

    private fun processPending() {
        while (pendingBuffer.isNotEmpty()) {
            if (!inThinkTag) {
                val thinkStartIdx = pendingBuffer.indexOf("<think>")
                if (thinkStartIdx != -1) {
                    val before = pendingBuffer.substring(0, thinkStartIdx)
                    if (before.isNotEmpty()) {
                        appendText(before)
                    }
                    pendingBuffer.delete(0, thinkStartIdx + "<think>".length)
                    inThinkTag = true
                } else {
                    val possiblePrefixLen = matchTrailingPrefix(pendingBuffer, "<think>")
                    if (possiblePrefixLen > 0) {
                        val emitLen = pendingBuffer.length - possiblePrefixLen
                        if (emitLen > 0) {
                            val toEmit = pendingBuffer.substring(0, emitLen)
                            appendText(toEmit)
                            pendingBuffer.delete(0, emitLen)
                        }
                        break
                    } else {
                        val textToEmit = pendingBuffer.toString()
                        pendingBuffer.clear()
                        appendText(textToEmit)
                    }
                }
            } else {
                val thinkEndIdx = pendingBuffer.indexOf("</think>")
                if (thinkEndIdx != -1) {
                    val reasoningContent = pendingBuffer.substring(0, thinkEndIdx)
                    if (reasoningContent.isNotEmpty()) {
                        appendReasoning(reasoningContent)
                    }
                    pendingBuffer.delete(0, thinkEndIdx + "</think>".length)
                    inThinkTag = false
                } else {
                    val possiblePrefixLen = matchTrailingPrefix(pendingBuffer, "</think>")
                    if (possiblePrefixLen > 0) {
                        val emitLen = pendingBuffer.length - possiblePrefixLen
                        if (emitLen > 0) {
                            val toEmit = pendingBuffer.substring(0, emitLen)
                            appendReasoning(toEmit)
                            pendingBuffer.delete(0, emitLen)
                        }
                        break
                    } else {
                        val reasoningToEmit = pendingBuffer.toString()
                        pendingBuffer.clear()
                        appendReasoning(reasoningToEmit)
                    }
                }
            }
        }
    }

    private fun appendText(str: String) {
        if (str.isEmpty()) return
        budget?.add(str)
        fullText.append(str)
        onDelta(str)
    }

    private fun appendReasoning(str: String) {
        if (str.isEmpty()) return
        // 死循环静默后的重复思考不再计入预算：它已被丢弃、不会继续占用内存。
        if (reasoningMutedDueToLoop) return
        budget?.add(str)

        if (detectRepetitionLoop(fullReasoning, str)) {
            reasoningMutedDueToLoop = true
            val notice = "\n[太墟提示：检测到思维链重复自旋死循环，已自动截断冗余思考内容并继续执行]\n"
            if (fullReasoning.length + notice.length <= maxReasoningChars) {
                fullReasoning.append(notice)
            }
            onReasoning(notice)
            return
        }

        if (fullReasoning.length < maxReasoningChars) {
            val takeCount = (maxReasoningChars - fullReasoning.length).coerceAtMost(str.length)
            fullReasoning.append(str.take(takeCount))
        }
        onReasoning(str)
    }

    private fun matchTrailingPrefix(sb: CharSequence, target: String): Int {
        for (len in target.length - 1 downTo 1) {
            if (sb.length >= len) {
                val sub = sb.subSequence(sb.length - len, sb.length)
                if (target.startsWith(sub)) {
                    return len
                }
            }
        }
        return 0
    }

    private fun detectRepetitionLoop(history: StringBuilder, newChunk: String): Boolean {
        if (history.length < 200) return false
        val recent = history.takeLast(300).toString() + newChunk
        for (patternLen in 12..40) {
            if (recent.length >= patternLen * 4) {
                val p = recent.takeLast(patternLen)
                val p2 = recent.substring(recent.length - patternLen * 2, recent.length - patternLen)
                val p3 = recent.substring(recent.length - patternLen * 3, recent.length - patternLen * 2)
                val p4 = recent.substring(recent.length - patternLen * 4, recent.length - patternLen * 3)
                if (p == p2 && p2 == p3 && p3 == p4) {
                    return true
                }
            }
        }
        return false
    }
}
