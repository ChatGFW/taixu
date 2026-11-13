package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.ui.components.RuntimeIconName

/** Read-only presentation input. Renderers do not execute tools or decide approvals/retries. */
internal data class ToolUiContext(val call: ToolCall, val result: ToolResult?, val workspace: String, val executing: Boolean)

internal interface ToolUiRenderer {
    fun icon(context: ToolUiContext): RuntimeIconName = toolIcon(context.call.tool)
    @Composable fun title(context: ToolUiContext): String = defaultToolStepTitle(context.call)
    fun target(context: ToolUiContext): String? = toolStepTarget(context.call)
    fun input(context: ToolUiContext): String = toolDetailInput(context.call)
    fun diffStat(context: ToolUiContext): Pair<Int, Int>? = null
    val previewLabel: Int? get() = null
    @Composable fun Preview(context: ToolUiContext, onOpenFile: ((String, String) -> Unit)?) = Unit
}

private object GenericToolUi : ToolUiRenderer
private object FileToolUi : ToolUiRenderer {
    override val previewLabel = R.string.chat_tool_details_file
    override fun diffStat(context: ToolUiContext): Pair<Int, Int>? =
        if (context.call.tool == HarnessTool.READ) null else parseDiffStat(context.call, context.result)
    @Composable override fun Preview(context: ToolUiContext, onOpenFile: ((String, String) -> Unit)?) {
        ToolDiffView(context.call, context.result, context.workspace, onOpenFile)
    }
}

/** start_task / read_screen 在时间线上与泛化「操作设备」区分开。 */
private object HostToolUi : ToolUiRenderer {
    @Composable
    override fun title(context: ToolUiContext): String {
        val action = (context.call.args["action"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        return when (action) {
            "virtual_screen_task" -> stringResource(R.string.chat_action_twin_task)
            "virtual_screen_screenshot" -> stringResource(R.string.chat_action_twin_read)
            else -> stringResource(R.string.chat_action_host)
        }
    }

    override fun target(context: ToolUiContext): String? {
        val action = (context.call.args["action"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (action != "virtual_screen_task") return null
        val ack = (context.call.args["ack"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val goal = (context.call.args["goal"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        return (ack.ifBlank { goal }).replace(Regex("[\\r\\n\\t]+"), " ").take(100).takeIf { it.isNotBlank() }
    }
}

/** Immutable native registration. Exact raw-name renderers are scoped to MCP/extension calls. */
internal class ToolUiRegistry(
    extensions: Map<String, ToolUiRenderer> = emptyMap(),
    builtIns: Map<HarnessTool, ToolUiRenderer> = mapOf(
        HarnessTool.READ to FileToolUi, HarnessTool.WRITE to FileToolUi, HarnessTool.EDIT to FileToolUi,
        HarnessTool.HOST to HostToolUi,
    ),
) {
    private val extensions = extensions.toMap()
    private val builtIns = builtIns.toMap()
    fun resolve(call: ToolCall): ToolUiRenderer =
        (if (call.tool == HarnessTool.MCP) extensions[call.rawToolName] else null)
            ?: builtIns[call.tool] ?: GenericToolUi
}

internal val LocalToolUiRegistry = staticCompositionLocalOf { ToolUiRegistry() }

@Composable
internal fun defaultToolStepTitle(call: ToolCall): String = when (call.tool) {
    HarnessTool.MCP -> toolDisplayName(call)
    HarnessTool.BASE -> stringResource(commandTitleResource(call))
    else -> stringResource(when (call.tool) {
        HarnessTool.READ -> R.string.chat_action_read
        HarnessTool.WRITE -> R.string.chat_action_write
        HarnessTool.EDIT -> R.string.chat_action_edit
        HarnessTool.BASE -> R.string.chat_action_command
        HarnessTool.PROCESS -> R.string.chat_action_process
        HarnessTool.HOST -> R.string.chat_action_host
        HarnessTool.DOWNLOAD -> R.string.chat_action_download
        HarnessTool.SEARCH_WEB -> R.string.chat_action_search_web
        HarnessTool.MEMORY -> R.string.chat_action_memory
        HarnessTool.PLAN -> R.string.chat_action_plan
        HarnessTool.SCRATCHPAD -> R.string.chat_action_scratchpad
        HarnessTool.HISTORY_SEARCH -> R.string.chat_action_search
        HarnessTool.HISTORY_READ -> R.string.chat_action_history
        HarnessTool.BUILD_SCRIPT -> R.string.chat_action_build_script
        HarnessTool.SUBAGENT -> R.string.chat_action_subagent
        HarnessTool.LOAD_RULE -> R.string.chat_action_rule
        HarnessTool.LOAD_SKILL -> R.string.chat_action_skill
        HarnessTool.COMPRESS -> R.string.chat_action_compress
        HarnessTool.ASK_USER -> R.string.chat_action_question
        HarnessTool.RENDER_SURFACE -> R.string.chat_action_surface
        HarnessTool.MCP -> error("Handled above")
    })
}
