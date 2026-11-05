package top.wkbin.taixu.harness.validation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 智能体工具调用死循环与重复调用检测器。
 *
 * 专门防御以下异常行为：
 * 1. 同一工具使用完全相同的参数连续失败多次（盲目重试而不纠错）；
 * 2. 相同工具调用序列在多轮对话中反复空转（无论成功与否，没有取得实质性进展）；
 * 3. 参数校验连续失败却不按 Schema 修正；
 * 4. 同一 path 上连续 Hashline STALE（改 ops 盲试）却不先 read。
 */
class ToolCallLoopDetector(
    private val maxSameCallFailures: Int = 2,
    private val maxIdenticalCallsStreak: Int = 3,
    private val maxHashlineStalePerPath: Int = 2,
) {
    private val callHistory = mutableListOf<CallRecord>()

    data class CallRecord(
        val toolName: String,
        val argsJson: String,
        var success: Boolean? = null,
        /** 相同 (tool, args) 的上一次调用输出是否发生了变化：变了 = 有进展，不计入空转。 */
        var outputChanged: Boolean = false,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /** (toolName:argsJson) → 上一次输出的指纹，用于识别"参数相同但结果在变"的合法轮询。 */
    private val lastOutputFingerprints = mutableMapOf<String, Int>()

    /** path → 连续 HASHLINE_STALE 次数；成功 read 同 path 清零。 */
    private val hashlineStaleByPath = mutableMapOf<String, Int>()

    /** 最近编造的工具名；名称每次都换时，按"同名同参失败"的规则抓不到。 */
    private val unknownToolAttempts = mutableListOf<String>()

    sealed interface LoopVerdict {
        data object Pass : LoopVerdict
        data class Warn(val message: String) : LoopVerdict
        data class Block(val reason: String, val guidance: String) : LoopVerdict
    }

    /**
     * 在工具执行前进行循环检测。
     *
     * @param toolName 工具 API 名称
     * @param args 工具入参
     * @return 检测裁决结果
     */
    @Synchronized
    fun evaluate(toolName: String, args: JsonObject): LoopVerdict {
        val currentArgsJson = canonical(args).toString()
        // 窗口须显著大于单轮工具上限（默认 12）：跨轮震荡的模式可能被同轮后续调用推出
        // 小窗口而逃过检测；上限 50 条历史内取 30 是覆盖与噪声的平衡。
        val recentCalls = callHistory.takeLast(30)

        // 0. 同 path 连续 Hashline STALE：即使用不同 ops 盲试也拦截，强制先 read。
        if (isEditTool(toolName)) {
            val path = pathOf(args)
            val staleStreak = path?.let { hashlineStaleByPath[it] } ?: 0
            if (path != null && staleStreak >= maxHashlineStalePerPath) {
                val shown = "/$path"
                return LoopVerdict.Block(
                    reason = "检测到 Hashline 死循环：`$shown` 已连续 HASHLINE_STALE $staleStreak 次",
                    guidance = buildString {
                        append("【已强制拦截过期锚点盲试】\n")
                        append("文件 `$shown` 的 Hashline 锚点已连续过期 $staleStreak 次；继续改 ops 无效。\n\n")
                        append("请立即：\n")
                        append("1. 调用 `read(\"$shown\")`（可带合适 offset/limit）获取最新 `n:h` 锚点；\n")
                        append("2. 仅使用本次 read 返回的锚点重新构造 ops；\n")
                        append("3. 禁止改用猜测的 oldText/newText 盲试同一处。")
                    },
                )
            }
        }

        // 1. 检测连续相同调用的失败历史
        val sameFailedStreak = recentCalls.reversed().takeWhile { record ->
            record.toolName.equals(toolName, ignoreCase = true) &&
                record.argsJson == currentArgsJson &&
                record.success == false
        }.count()

        if (sameFailedStreak >= maxSameCallFailures) {
            return LoopVerdict.Block(
                reason = "检测到死循环：工具 `$toolName` 已使用完全相同的参数连续执行失败 $sameFailedStreak 次",
                guidance = buildString {
                    append("【已强制拦截该重复调用并要求反思】\n")
                    append("你正在反复发起完全相同的失败操作：`$toolName($currentArgsJson)`。\n")
                    append("这是典型的死循环行为，已直接阻断执行。\n\n")
                    append("请立即执行以下反思与策略变更：\n")
                    append("1. **定位根因**：仔细阅读前述错误输出，分析上几次失败的具体原因（路径错误？内容不匹配？参数缺失？缺少依赖？）；\n")
                    append("2. **严禁重试**：严禁再次发起完全相同的调用；\n")
                    append("3. **改变策略**：\n")
                    append("   - 如果是 `edit` 失败：先 `read` 取最新 `n:h` 锚点，优先用 ops 重试；legacy oldText 须逐字唯一匹配，禁止盲猜；\n")
                    append("   - 如果是 `base` 命令失败：调用 `read`/`base` 检查环境、路径或查看日志，或切换实现方式；\n")
                    append("   - 如果缺少工具或能力：向用户明确说明当前遇到的障碍与已完成的步骤。")
                },
            )
        }

        // 2. 检测完全相同的调用连续出现（即使成功也可能是无进展空转）。
        // 输出有变化的不计入：`process logs`/`read` 轮询增长中的日志时参数完全相同，
        // 但每次输出都在变，这正是被提示词鼓励的合法用法，按字面判空转会误杀。
        val identicalStreak = recentCalls.reversed().takeWhile { record ->
            record.toolName.equals(toolName, ignoreCase = true) &&
                record.argsJson == currentArgsJson &&
                !record.outputChanged
        }.count()

        if (identicalStreak >= maxIdenticalCallsStreak) {
            return LoopVerdict.Block(
                reason = "检测到重复空转：工具 `$toolName` 已连续执行 $identicalStreak 次，参数与输出均无变化",
                guidance = buildString {
                    append("【已强制拦截该无进展重复调用】\n")
                    append("你已连续 $identicalStreak 次执行完全相同的操作 `$toolName`，系统检测到任务陷入停滞空转。\n")
                    append("请跳出当前循环，总结已获取的信息，推进到下一阶段或向用户汇报。")
                },
            )
        }

        // 3. 检测多工具交替震荡死循环（例如 A -> B -> A -> B 或 A -> B -> C -> A -> B -> C）
        val simulatedHistory = recentCalls.map { "${it.toolName.lowercase()}:${it.argsJson}" } + "${toolName.lowercase()}:$currentArgsJson"
        for (period in 2..3) {
            if (simulatedHistory.size >= period * 3) {
                val cycle1 = simulatedHistory.takeLast(period)
                val cycle2 = simulatedHistory.dropLast(period).takeLast(period)
                val cycle3 = simulatedHistory.dropLast(period * 2).takeLast(period)
                if (cycle1 == cycle2 && cycle2 == cycle3) {
                    return LoopVerdict.Block(
                        reason = "检测到交替震荡死循环：工具调用序列以周期 $period 反复交替循环了 3 次",
                        guidance = buildString {
                            append("【已强制拦截交替震荡死循环】\n")
                            append("你正在反复交替执行相同的操作序列（周期 $period）。\n")
                            append("系统检测到任务陷入来回摆动的震荡死循环，已直接阻断执行。\n\n")
                            append("请立即执行以下反思：\n")
                            append("1. 为什么上一个步骤的结果无法推进任务？\n")
                            append("2. 立即停止当前循环模式，切换到新的解决思路或向用户汇报当前阻碍。")
                        },
                    )
                }
            }
        }

        return LoopVerdict.Pass
    }

    /**
     * 未知工具名的累计升级。
     *
     * 模型编造工具名时往往每次换一个（bash → shell → sh），同名同参的失败规则抓不到，
     * 整轮预算会被耗在猜名字上。连续第 [MAX_UNKNOWN_TOOL_ATTEMPTS] 次起返回升级提示，
     * 由调用方拼在常规的"未知工具"清单之后。
     */
    @Synchronized
    fun unknownToolEscalation(toolName: String): String? {
        unknownToolAttempts.add(toolName)
        while (unknownToolAttempts.size > MAX_UNKNOWN_TOOL_ATTEMPTS) unknownToolAttempts.removeAt(0)
        if (unknownToolAttempts.size < MAX_UNKNOWN_TOOL_ATTEMPTS) return null
        return "【已连续 $MAX_UNKNOWN_TOOL_ATTEMPTS 次调用不存在的工具】最近尝试：" +
            unknownToolAttempts.joinToString("、") +
            "。不要再猜工具名：从上方清单中原样复制一个名称重新发起；若确实缺少所需能力，直接向用户说明。"
    }

    /**
     * 登记单次工具调用的开始。
     */
    @Synchronized
    fun recordIntent(toolName: String, args: JsonObject) {
        callHistory.add(CallRecord(toolName = toolName, argsJson = canonical(args).toString()))
        // 限制历史记录上限
        if (callHistory.size > 50) {
            callHistory.removeAt(0)
        }
    }

    /**
     * 记录单次工具调用的结算结果。
     *
     * @param output 工具输出正文（可空）。相同 (tool, args) 之间输出发生变化时标记
     *  [CallRecord.outputChanged]，使结果感知的空转检测不再误杀合法轮询。
     * @param metadata 工具结果元数据；含 `hashline_error=HASHLINE_STALE` 时计入同 path 过期计数。
     */
    @Synchronized
    fun recordSettled(
        toolName: String,
        args: JsonObject,
        success: Boolean,
        output: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        // 任何一个真实工具的调用成功，都说明模型已从"猜名字"里恢复，累计清零。
        if (success) unknownToolAttempts.clear()
        val currentArgsJson = canonical(args).toString()
        val lastRecord = callHistory.lastOrNull {
            it.toolName.equals(toolName, ignoreCase = true) && it.argsJson == currentArgsJson && it.success == null
        }
        if (lastRecord != null) {
            lastRecord.success = success
            lastRecord.outputChanged = markOutputProgress(toolName, currentArgsJson, output)
        } else {
            callHistory.add(
                CallRecord(
                    toolName = toolName,
                    argsJson = currentArgsJson,
                    success = success,
                    outputChanged = markOutputProgress(toolName, currentArgsJson, output),
                ),
            )
            if (callHistory.size > 50) callHistory.removeAt(0)
        }
        trackHashlineStale(toolName, args, success, metadata)
    }

    /** 相同 (tool, args) 的输出与上一次相比是否发生变化；同时滚动更新指纹。 */
    private fun markOutputProgress(toolName: String, argsJson: String, output: String?): Boolean {
        if (output == null) return false
        val key = "${toolName.lowercase()}:$argsJson"
        val fingerprint = output.hashCode()
        val previous = lastOutputFingerprints.put(key, fingerprint)
        return previous != null && previous != fingerprint
    }

    private fun trackHashlineStale(
        toolName: String,
        args: JsonObject,
        success: Boolean,
        metadata: Map<String, String>,
    ) {
        val path = pathOf(args) ?: return
        if (isReadTool(toolName) && success) {
            hashlineStaleByPath.remove(path)
            return
        }
        if (!isEditTool(toolName)) return
        if (success) {
            hashlineStaleByPath.remove(path)
            return
        }
        val code = metadata["hashline_error"].orEmpty()
        if (code == "HASHLINE_STALE" || code == "stale") {
            hashlineStaleByPath[path] = (hashlineStaleByPath[path] ?: 0) + 1
        } else {
            // 非 STALE 的 edit 失败打断「连续过期盲试」计数，避免与其它错误混叠。
            hashlineStaleByPath.remove(path)
        }
    }

    /**
     * 清空当前检测器的历史（在用户发送新消息或会话重置时调用）。
     */
    @Synchronized
    fun reset() {
        callHistory.clear()
        lastOutputFingerprints.clear()
        hashlineStaleByPath.clear()
        unknownToolAttempts.clear()
    }

    private fun canonical(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }

    private fun pathOf(args: JsonObject): String? {
        val raw = (args["path"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return raw.trimStart('/')
    }

    private fun isEditTool(name: String): Boolean = name.equals("edit", ignoreCase = true)

    private fun isReadTool(name: String): Boolean = name.equals("read", ignoreCase = true)

    private companion object {
        const val MAX_UNKNOWN_TOOL_ATTEMPTS = 3
    }
}
