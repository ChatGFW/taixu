package top.wkbin.taixu.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * render_surface 工具对 LLM 暴露的契约（名称 / 描述 / 参数 Schema / ApiToolDefinition）。
 *
 * 单独成文件的原因：ProviderClient 的 TOOLS 列表在尺寸棘轮基线内（只许缩减），
 * 这里把定义收敛为一个常量，ProviderClient 只需追加一行引用。
 */
object A2uiSurfaceContract {

    const val TOOL_NAME = "render_surface"

    /**
     * M3 Basic Catalog 的目录 id：createSurface 消息里的 catalogId 必须与之一致。
     * 值与 androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.CatalogId 对齐
     * （harness 不依赖该库，故此处以常量同步；库升级时需联动核对）。
     */
    const val CATALOG_ID = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

    private const val TOOL_DESCRIPTION =
        "把结构化数据渲染为设备上的原生交互界面（A2UI 协议 PoC）。当回答包含适合可视化的内容" +
            "（对比、排行、表单、看板、卡片列表）时，用本工具提交界面，然后用一两句话总结即可，不要在正文里重复数据。" +
            "messages 是 A2UI 协议消息数组的 JSON 字符串：首条 createSurface（新界面必须用全新 surfaceId），" +
            "其后一条 updateComponents 携带扁平的 components 列表；每个组件有唯一 id 与 component 类型名，" +
            "容器通过 children/child 引用子组件 id，恰好一个组件 id 为 root 作为布局顶部。" +
            "文本属性直接写明文常量，不要用表达式。可用组件由客户端 Catalog 定义，不得杜撰组件类型。"

    private const val TOOL_SCHEMA_JSON =
        """{"type":"object","properties":{"surfaceId":{"type":"string","description":"界面唯一 id（字母数字与 _ . : -，≤64 字符）；更新已有界面时必须复用原 id"},"title":{"type":"string","description":"界面短标题（≤80 字符）"},"messages":{"type":"string","description":"A2UI 协议消息数组的 JSON 字符串；catalogId 固定为 $CATALOG_ID。示例：[{\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\"s1\",\"catalogId\":\"$CATALOG_ID\"}},{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"t\"]},{\"id\":\"t\",\"component\":\"Text\",\"text\":\"你好\"}]}}]"}},"required":["surfaceId","messages"]}"""

    /** ProviderClient.TOOLS 直接引用，避免在棘轮基线文件里展开多行定义。 */
    val TOOL_DEFINITION: ApiToolDefinition = ApiToolDefinition(
        function = ApiFunctionDefinition(
            name = TOOL_NAME,
            description = TOOL_DESCRIPTION,
            parameters = Json.parseToJsonElement(TOOL_SCHEMA_JSON) as JsonObject,
        ),
    )
}
