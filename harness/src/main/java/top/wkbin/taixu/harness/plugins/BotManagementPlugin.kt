package top.wkbin.taixu.harness.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.repository.GroupChatRepository
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupChat
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus
import top.wkbin.taixu.harness.bot.BotAssignmentTracker
import top.wkbin.taixu.harness.bot.BotOrchestrator
import top.wkbin.taixu.harness.groupchat.GroupChatDispatcher

/**
 * 主控 Bot 的团队管理工具：孵化新 Bot、向指定 Bot 派发后台子任务、查看名册。
 *
 * 只有主控 Bot（isMainBot）可以调用；身份由当前会话反查，模型无法通过参数伪造。
 */
fun botManagementPlugin(
    botRepository: PersonaBotRepository,
    groupRepository: GroupChatRepository,
    orchestrator: () -> BotOrchestrator,
    dispatcher: () -> GroupChatDispatcher,
    assignments: BotAssignmentTracker,
    logger: AppLogger,
): HarnessPlugin {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val descriptor = botManagementDescriptor()
    val createSchema = pluginObjectSchema(
        required = listOf("name", "role", "system_prompt"),
        properties = mapOf(
            "name" to pluginStringProperty("新 Bot 的名称（团队内建议唯一）"),
            "role" to pluginStringProperty("一句话职责，如「后端工程师」"),
            "system_prompt" to pluginStringProperty("完整人格与工作约束，会写入 persona.md"),
            "model_id" to pluginStringProperty("可选模型 ID；省略则使用默认模型"),
            "color_hex" to pluginStringProperty("可选主题色，如 #3D5AFE"),
            "group_id" to pluginStringProperty("可选群聊 ID；省略时加入你所在的第一个群聊"),
        ),
    )
    val assignSchema = pluginObjectSchema(
        required = listOf("bot_name", "task"),
        properties = mapOf(
            "bot_name" to pluginStringProperty("目标 Bot 名称"),
            "task" to pluginStringProperty("要交给它独立完成的具体任务"),
            "write_paths" to pluginStringArrayProperty(
                "可选写入租约；为空表示只读任务，写入会被拦截",
                "允许写入的沙箱路径",
            ),
            "group_id" to pluginStringProperty("可选群聊 ID；省略时结果回传到主控 Bot 所在群"),
            "wait" to pluginBooleanProperty("是否等待子任务完成后直接返回结果；省略时后台执行并回传群聊"),
            "depends_on" to pluginStringArrayProperty(
                "可选前置任务 ID 列表；全部 COMPLETED 后才允许派发，用于串行依赖",
                "assign-task 返回的任务 ID",
            ),
        ),
    )
    val listSchema = pluginObjectSchema(emptyList(), emptyMap())
    val assignmentListSchema = pluginObjectSchema(
        emptyList(),
        mapOf("group_id" to pluginStringProperty("可选群聊 ID；省略时列出最近 20 条")),
    )
    val assignmentIdSchema = pluginObjectSchema(
        required = listOf("assignment_id"),
        properties = mapOf("assignment_id" to pluginStringProperty("assign-task 返回的任务 ID")),
    )
    return HarnessPlugin(
        descriptor = descriptor,
        tools = listOf(
            PluginTool(
                id = "create-bot",
                description = "创建一名具有独立人格、独占会话与 Git 记忆的 Bot。仅主控 Bot 可用。",
                schema = createSchema,
            ) { request ->
                val caller = requireMainBot(request.sessionId, botRepository)
                    ?: return@PluginTool PluginToolOutput(false, MAIN_BOT_ONLY)
                val name = request.arguments["name"]?.jsonPrimitive?.content.orEmpty()
                val role = request.arguments["role"]?.jsonPrimitive?.content.orEmpty()
                val prompt = request.arguments["system_prompt"]?.jsonPrimitive?.content.orEmpty()
                val modelId = request.arguments["model_id"]?.jsonPrimitive?.content
                val color = request.arguments["color_hex"]?.jsonPrimitive?.content
                val group = resolveGroup(
                    caller,
                    request.arguments["group_id"]?.jsonPrimitive?.content,
                    groupRepository,
                )
                if (!request.arguments["group_id"]?.jsonPrimitive?.content.isNullOrBlank() && group == null) {
                    return@PluginTool PluginToolOutput(false, "找不到群聊或你不是群成员")
                }
                runCatching {
                    orchestrator().createBot(
                        name = name,
                        role = role,
                        systemPrompt = prompt,
                        modelId = modelId,
                        colorHex = normalizeColor(color),
                        groupId = group?.id,
                    )
                }.fold(
                    onSuccess = { bot ->
                        PluginToolOutput(
                            true,
                            "✅ 已孵化 ${bot.name}（${bot.role}）${group?.let { "，已加入群聊「${it.name}」" } ?: ""}",
                        )
                    },
                    onFailure = { PluginToolOutput(false, "创建 Bot 失败：${it.message}") },
                )
            },
            PluginTool(
                id = "assign-task",
                description = "把一项专项任务派发给指定 Bot，在独立后台 Lane 中执行并持久化任务记录。仅主控 Bot 可用。",
                schema = assignSchema,
            ) { request ->
                val caller = requireMainBot(request.sessionId, botRepository)
                    ?: return@PluginTool PluginToolOutput(false, MAIN_BOT_ONLY)
                val targetName = request.arguments["bot_name"]?.jsonPrimitive?.content.orEmpty()
                val task = request.arguments["task"]?.jsonPrimitive?.content.orEmpty()
                if (task.isBlank()) return@PluginTool PluginToolOutput(false, "任务内容不能为空")
                val target = botRepository.findBotByName(targetName)
                    ?: return@PluginTool PluginToolOutput(false, "找不到名为「$targetName」的 Bot")
                if (target.id == caller.id) {
                    return@PluginTool PluginToolOutput(false, "不能把任务派发给自己；请直接执行")
                }
                val unmet = unmetDependencies(assignments, request.stringArray("depends_on"))
                if (unmet.isNotEmpty()) {
                    return@PluginTool PluginToolOutput(
                        false,
                        "前置任务尚未完成：${unmet.joinToString("；")}。请先用 list-assignments / inspect-assignment 确认状态为 COMPLETED 后再派发。",
                    )
                }
                val writePaths = request.stringArray("write_paths")
                val group = resolveGroup(
                    caller,
                    request.arguments["group_id"]?.jsonPrimitive?.content,
                    groupRepository,
                )
                if (!request.arguments["group_id"]?.jsonPrimitive?.content.isNullOrBlank() && group == null) {
                    return@PluginTool PluginToolOutput(false, "找不到群聊或你不是群成员")
                }
                val groupSessionId = group?.let { resolved ->
                    runCatching { orchestrator().ensureGroupSession(target, resolved.id) }
                        .onFailure {
                            logger.w("[BotManagementPlugin] 准备群维度会话失败: ${it.message}", it)
                        }
                        .getOrElse { return@PluginTool PluginToolOutput(false, "准备群会话失败：${it.message}") }
                }
                val wait = request.arguments["wait"]?.jsonPrimitive?.booleanOrNull ?: (group == null)
                if (wait) {
                    val assignment = newAssignment(
                        target, group, task, writePaths, sessionId = groupSessionId,
                    )
                    assignments.start(assignment, currentCoroutineContext()[Job])
                    var status = BotAssignmentStatus.FAILED
                    var summary: String? = null
                    var error: String? = null
                    try {
                        val result = orchestrator().dispatchSubtask(
                            target.id,
                            task,
                            writePaths,
                            laneName = assignment.laneName,
                            groupId = group?.id,
                        )
                        summary = result.summary
                        if (result.success) {
                            status = BotAssignmentStatus.COMPLETED
                        } else {
                            error = "子任务未成功结束（${result.termination}）"
                        }
                        val mark = if (result.success) "✅" else "⚠️"
                        PluginToolOutput(
                            result.success,
                            "$mark ${target.name} 执行结束（任务 ${assignment.id}，工具调用 ${result.toolCallCount} 次）\n\n${result.summary.take(BOT_TOOL_MAX_CHARS)}",
                        )
                    } catch (cancelled: CancellationException) {
                        status = BotAssignmentStatus.CANCELLED
                        error = "已取消"
                        throw cancelled
                    } catch (failure: Throwable) {
                        error = failure.message ?: "未知错误"
                        PluginToolOutput(false, "派发失败（任务 ${assignment.id}）：$error")
                    } finally {
                        withContext(NonCancellable) { assignments.finish(assignment.id, status, summary, error) }
                    }
                } else {
                    val notifyGroup = group ?: return@PluginTool PluginToolOutput(
                        false,
                        "后台委派需要至少一个群聊来回传结果；请加入群聊、指定 group_id，或改用 wait=true",
                    )
                    val assignment = newAssignment(
                        target, notifyGroup, task, writePaths, sessionId = groupSessionId,
                    )
                    scope.launchBotAssignment(assignments, assignment,
                        run = {
                            orchestrator().dispatchSubtask(
                                target.id, task, writePaths, laneName = assignment.laneName,
                                groupId = notifyGroup.id,
                            )
                        },
                        report = { text, wakeMainBot ->
                            notifyAssignmentResult(
                                dispatcher(), logger, caller, notifyGroup, assignment.id, text, wakeMainBot,
                            )
                        },
                    )
                    PluginToolOutput(
                        true,
                        "🚀 已把任务派发给 ${target.name}（任务 ${assignment.id}），完成后会回传到群聊「${notifyGroup.name}」；可用 list-assignments 查看进度。",
                    )
                }
            },
            PluginTool(
                id = "list-bots",
                description = "查看当前团队的 Bot 名册与主控标记。",
                schema = listSchema,
            ) { request ->
                val caller = requireMainBot(request.sessionId, botRepository)
                    ?: return@PluginTool PluginToolOutput(false, MAIN_BOT_ONLY)
                val bots = botRepository.listBots()
                if (bots.isEmpty()) {
                    PluginToolOutput(true, "（暂无 Bot）")
                } else {
                    val text = bots.joinToString("\n") { bot ->
                        val mark = if (bot.isMainBot) "👑 主控" else "成员"
                        "- ${bot.name}（${bot.role}）· $mark · 模型 ${bot.modelId ?: "默认"}"
                    }
                    PluginToolOutput(true, text.take(BOT_TOOL_MAX_CHARS))
                }
            },
        ) + botAssignmentTools(
            botRepository = botRepository,
            groupRepository = groupRepository,
            assignments = assignments,
        ),
    )
}

internal suspend fun requireMainBot(
    sessionId: String,
    botRepository: PersonaBotRepository,
): Bot? = botRepository.getBotBySessionId(sessionId)?.takeIf { it.isMainBot }

internal suspend fun resolveGroup(
    caller: Bot,
    requestedGroupId: String?,
    groupRepository: GroupChatRepository,
): GroupChat? {
    if (requestedGroupId.isNullOrBlank()) {
        return groupRepository.observeGroupsForBot(caller.id).first().firstOrNull()
    }
    val group = groupRepository.getGroup(requestedGroupId) ?: return null
    val member = groupRepository.getMembers(group.id).any { it.id == caller.id }
    return group.takeIf { member }
}

private fun normalizeColor(raw: String?): String =
    raw?.trim()?.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#3D5AFE"

internal const val MAIN_BOT_ONLY = "只有主控 Bot 可以管理团队"
internal const val BOT_TOOL_MAX_CHARS = 12_000
internal const val BOT_BACKGROUND_SUMMARY_CHARS = 4_000
