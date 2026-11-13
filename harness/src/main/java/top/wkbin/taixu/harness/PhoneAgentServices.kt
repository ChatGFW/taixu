package top.wkbin.taixu.harness

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.database.WorkflowRepository
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.datastore.PhoneAgentEndpoint
import top.wkbin.taixu.core.model.workflow.PhoneOperationWorkflow
import top.wkbin.taixu.core.model.workflow.PhoneWorkflowOperation
import top.wkbin.taixu.harness.events.AgentEventLogger

internal fun virtualScreenSession(args: JsonObject): String =
    args["session"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ToolExecutor.VIRTUAL_SCREEN_DEFAULT_SESSION

/** Phone requests deliberately have a smaller output budget than the chat model. */
internal fun phoneAgentModel(endpoint: PhoneAgentEndpoint) = ModelConfig(
    name = "手机操作", provider = "phone-agent", model = endpoint.model.trim(),
    baseUrl = endpoint.baseUrl.trim(), apiKey = endpoint.apiKey, maxTokens = 2048,
    pureChatMode = true, toolCallMode = ToolCallMode.DISABLED,
    visionEnabled = true, reasoningMode = ReasoningMode.DISABLED,
)

/**
 * 把聊天主模型收成手机操作协议：关工具、开视觉、限制输出预算。
 * 专用手机模型未配置时用它代跑，避免任务直接失败。
 */
internal fun phoneAgentModelFromMain(main: ModelConfig): ModelConfig = main.copy(
    name = "${main.name}（主模型代跑手机）",
    maxTokens = (main.maxTokens ?: 2048).coerceIn(512, 4096),
    pureChatMode = true,
    toolCallMode = ToolCallMode.DISABLED,
    visionEnabled = true,
    reasoningMode = ReasoningMode.DISABLED,
    dynamicMcpTools = emptyList(),
)

/**
 * @return first = 运行配置，second = 来源标记（phone-agent / main-fallback）
 */
internal suspend fun resolvePhoneRunModel(
    preferences: AgentPreferences,
    fallbackMain: (suspend () -> ModelConfig)? = null,
): Pair<ModelConfig, String> {
    val endpoint = preferences.phoneAgentConfig.first()
    if (endpoint.ready) return phoneAgentModel(endpoint) to "phone-agent"
    val main = runCatching { fallbackMain?.invoke() }.getOrNull()
    if (main != null && main.baseUrl.isNotBlank() && main.model.isNotBlank()) {
        return phoneAgentModelFromMain(main) to "main-fallback"
    }
    error(
        "还没有可用的手机操作模型：专用接口未配置，且当前主模型也不可用。" +
            "请到设置 → 智能体与模型 → 虚拟屏 → 手机操作模型填写接口，" +
            "或先在设置 → 模型里激活一个支持看图的主模型（未配专用接口时会自动用主模型代跑）。",
    )
}

class PhoneAgentServices(
    private val events: AgentEventLogger,
    private val workflows: WorkflowRepository,
) {
    suspend fun log(session: String, message: String) {
        try { events.log(session, "PhoneAgent", message) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Diagnostic I/O must not abort a phone action. */ }
    }

    suspend fun save(name: String, operations: List<PhoneWorkflowOperation>): String {
        require(phoneRecordingIssue(operations) == null) { phoneRecordingIssue(operations).orEmpty() }
        val id = "phone_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        workflows.upsert(PhoneOperationWorkflow.create(id, name, operations).copy(createdAt = now, updatedAt = now))
        return "已保存工作流「$name」（$id）。可在工作区 → 工作流编辑每步坐标、等待时间和输入；运行时可更改 TEXT_1 等变量。"
    }
}

internal fun phoneRecordingIssue(operations: List<PhoneWorkflowOperation>): String? = when {
    operations.isEmpty() -> "没有录到可重放的操作步骤，未保存工作流。"
    operations.firstOrNull { it.action != "virtual_screen_wait" }?.action != "virtual_screen_launch" ->
        "录制缺少打开应用的起始步骤，未保存工作流。请指定目标应用并从打开应用开始录制。"
    else -> null
}

internal fun phoneActionLog(action: PhoneAgentAction): String = when (action) {
    is PhoneAgentAction.Type -> "Type textLength=${action.text.length}"
    is PhoneAgentAction.Finish -> "Finish"
    is PhoneAgentAction.Note -> "Note"
    is PhoneAgentAction.TakeOver -> "TakeOver"
    is PhoneAgentAction.Launch -> "Launch"
    else -> action.toString()
}

internal fun PhoneAgentAction.workflowOperation(waitMs: Long, resolvedPackage: String? = null): PhoneWorkflowOperation? {
    fun point(x: Int, y: Int) = mapOf("x" to "$x", "y" to "$y")
    val (action, parameters) = when (this) {
        is PhoneAgentAction.Tap -> "virtual_screen_click" to point(x, y)
        is PhoneAgentAction.DoubleTap -> "virtual_screen_double_click" to point(x, y)
        is PhoneAgentAction.LongPress -> "virtual_screen_long_press" to (point(x, y) + ("duration_ms" to "800"))
        is PhoneAgentAction.Swipe -> "virtual_screen_swipe" to mapOf(
            "x1" to "$x1", "y1" to "$y1", "x2" to "$x2", "y2" to "$y2", "duration_ms" to "300",
        )
        is PhoneAgentAction.Type -> "virtual_screen_set_text" to mapOf("text" to text)
        is PhoneAgentAction.Launch -> "virtual_screen_launch" to mapOf("package" to (resolvedPackage ?: return null))
        PhoneAgentAction.Back -> "virtual_screen_key" to mapOf("key" to "back")
        PhoneAgentAction.Home -> "virtual_screen_key" to mapOf("key" to "home")
        is PhoneAgentAction.Wait -> return PhoneWorkflowOperation("virtual_screen_wait", mapOf("duration_ms" to "$durationMs"))
        else -> return null
    }
    return PhoneWorkflowOperation(action, parameters, waitMs)
}
