package top.wkbin.taixu.harness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * render_surface（A2UI PoC）工具的界面载荷总线。
 *
 * A2UI = Agent-to-UI：智能体不写代码，而是输出 JSON Lines 协议消息描述界面，
 * 客户端用 Compose 渲染器（feature:a2uipoc 模块）映射为原生组件。安全边界来自
 * Component Catalog：智能体只能使用目录里声明的组件，与工具白名单同构。
 *
 * PoC 简化说明：总线用 object 单例而非 Koin 注入，避免给 ToolExecutor/KoinModule
 * 两个棘轮基线文件增行；转正式实现时应改为接口 + 注入，并把总线收敛进会话生命周期。
 */
object A2uiSurfaceBus {

    /** 工具产出的一条界面载荷（一次 render_surface 调用 = 一个 surface 的协议消息集）。 */
    data class A2uiSurfacePayload(
        val surfaceId: String,
        val title: String,
        /** A2UI 协议消息数组的 JSON 字符串（原样保存，渲染器逐条 processInput）。 */
        val messagesJson: String,
        val messageCount: Int,
        val createdAt: Long,
    )

    private val _surfaces = MutableStateFlow<List<A2uiSurfacePayload>>(emptyList())

    /** 最近发布的界面载荷（新的在前，最多 [MAX_CACHED_SURFACES] 条，同 surfaceId 覆盖旧条目）。 */
    val surfaces: StateFlow<List<A2uiSurfacePayload>> = _surfaces.asStateFlow()

    fun clear() {
        _surfaces.value = emptyList()
    }

    /**
     * render_surface 工具执行入口：校验参数 → 登记载荷 → 返回给模型的结果文本。
     * 返回值约定与 ToolExecutor 一致（success to output）。
     */
    fun publishFromTool(args: JsonObject): Pair<Boolean, String> {
        val payload = parseToolArgs(args).getOrElse { failure ->
            return false to "render_surface 参数校验未通过：${failure.message}。请修正参数后重新调用。"
        }
        publish(payload)
        return true to buildString {
            append("已提交 A2UI 界面「${payload.title}」（surfaceId=${payload.surfaceId}，")
            append("${payload.messageCount} 条协议消息）。界面已在用户聊天流中渲染为原生组件；")
            append("如需更新该界面，请用相同 surfaceId 再次调用并只携带 updateComponents 消息。")
        }
    }

    private fun publish(payload: A2uiSurfacePayload) {
        _surfaces.value = (listOf(payload) + _surfaces.value.filterNot { it.surfaceId == payload.surfaceId })
            .take(MAX_CACHED_SURFACES)
    }

    internal fun parseToolArgs(args: JsonObject): Result<A2uiSurfacePayload> = runCatching {
        val surfaceId = args["surfaceId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(surfaceId.isNotEmpty()) { "缺少 surfaceId" }
        require(surfaceId.length <= MAX_ID_CHARS && SURFACE_ID_REGEX.matches(surfaceId)) {
            "surfaceId 仅允许字母数字与 _ . : -，且不超过 $MAX_ID_CHARS 字符"
        }
        val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim().takeIf { !it.isNullOrBlank() } ?: "A2UI 界面"
        require(title.length <= MAX_TITLE_CHARS) { "title 不超过 $MAX_TITLE_CHARS 字符" }
        val messagesJson = args["messages"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(messagesJson.length in MIN_JSON_CHARS..MAX_JSON_CHARS) {
            "messages 必须是 A2UI 协议消息数组的 JSON 字符串（长度 $MIN_JSON_CHARS..$MAX_JSON_CHARS）"
        }
        val array = Json.parseToJsonElement(messagesJson) as? JsonArray
            ?: error("messages 必须是 JSON 数组（首条 createSurface，其后 updateComponents）")
        require(array.isNotEmpty() && array.all { it is JsonObject && it.containsKey("version") }) {
            "messages 数组每项必须是带 version 的 A2UI 协议消息对象"
        }
        A2uiSurfacePayload(
            surfaceId = surfaceId,
            title = title,
            messagesJson = messagesJson,
            messageCount = array.size,
            createdAt = System.currentTimeMillis(),
        )
    }

    /** 供 feature:a2uipoc 演示屏使用的内置示例（与 [A2uiSurfaceContract.CATALOG_ID] 对齐）。 */
    fun sampleMessagesJson(): String = SAMPLE_MESSAGES_JSON

    internal const val MAX_ID_CHARS = 64
    internal const val MAX_TITLE_CHARS = 80
    internal const val MIN_JSON_CHARS = 8
    internal const val MAX_JSON_CHARS = 200_000
    internal const val MAX_CACHED_SURFACES = 8
    internal val SURFACE_ID_REGEX = Regex("[A-Za-z0-9_.:-]+")

    // 注意：含模板引用，不能声明为 const val（棘轮文件外的新文件，保持 private val 即可）
    private val SAMPLE_MESSAGES_JSON =
        """[{"version":"v0.9","createSurface":{"surfaceId":"demo-1","catalogId":"${A2uiSurfaceContract.CATALOG_ID}"}},
{"version":"v0.9","updateComponents":{"surfaceId":"demo-1","components":[
{"id":"root","component":"Column","children":["title","card","hint"]},
{"id":"title","component":"Text","text":"太墟 A2UI PoC"},
{"id":"card","component":"Card","child":"cardText"},
{"id":"cardText","component":"Text","text":"这段界面由智能体通过 render_surface 生成，渲染为原生 Compose 组件。"},
{"id":"hint","component":"Text","text":"组件范围由 Catalog 声明，智能体无法执行任意代码。"}
]}}]"""
}
