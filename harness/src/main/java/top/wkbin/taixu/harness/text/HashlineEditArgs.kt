package top.wkbin.taixu.harness.text

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.runtime.environment.HashlineAnchors

/** 从 edit 工具参数解析 Hashline ops；与 legacy oldText 互斥由调用方检查。 */
object HashlineEditArgs {
    fun parseOps(args: JsonObject): List<HashlineAnchors.Op> {
        val raw = args["ops"] ?: throw IllegalArgumentException("缺少参数：ops")
        val array = raw as? JsonArray
            ?: throw IllegalArgumentException("参数 ops 必须是数组")
        require(array.isNotEmpty()) { "ops 不能为空" }
        require(array.size <= HashlineAnchors.MAX_OPS) { "ops 最多 ${HashlineAnchors.MAX_OPS} 条" }
        return array.mapIndexed { index, element ->
            val obj = element as? JsonObject
                ?: throw IllegalArgumentException("ops[$index] 必须是对象")
            parseOp(obj, index)
        }
    }

    private fun parseOp(obj: JsonObject, index: Int): HashlineAnchors.Op {
        val kindRaw = obj["op"]?.jsonPrimitive?.content?.trim().orEmpty()
        require(kindRaw.isNotEmpty()) { "ops[$index] 缺少 op" }
        val kind = when (kindRaw) {
            "replace" -> HashlineAnchors.Kind.REPLACE
            "insert_after" -> HashlineAnchors.Kind.INSERT_AFTER
            "delete" -> HashlineAnchors.Kind.DELETE
            else -> throw IllegalArgumentException("ops[$index].op 非法：$kindRaw")
        }
        fun anchor(key: String): HashlineAnchors.Anchor? =
            obj[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { HashlineAnchors.parseAnchor(it) }
        // replace 缺 content 会在 apply 里变成删行；解析期要求显式字符串（空串仍表示清空该区间）。
        val content = when (kind) {
            HashlineAnchors.Kind.REPLACE -> requireStringField(obj, "content", index)
            else -> if ("content" in obj) requireStringField(obj, "content", index) else null
        }
        return HashlineAnchors.Op(
            op = kind,
            start = anchor("start"),
            end = anchor("end"),
            after = anchor("after"),
            content = content,
        )
    }

    private fun requireStringField(obj: JsonObject, key: String, index: Int): String {
        val primitive = obj[key] as? JsonPrimitive
        require(primitive != null && primitive.isString) { "ops[$index].$key 必须是字符串" }
        return primitive.content
    }

    /** 审批摘要：write/edit 路径，edit 带 ops 时追加 `Hashline N ops`。 */
    fun fileMutationSummary(toolName: String, args: JsonObject): String {
        val verb = toolName.lowercase()
        val path = args["path"]?.jsonPrimitive?.content.orEmpty()
        val hashline = if (verb == "edit") opsLabel(args) else null
        return if (hashline != null) "$verb $path · $hashline" else "$verb $path"
    }

    fun hasOps(args: JsonObject): Boolean = "ops" in args

    fun hasLegacy(args: JsonObject): Boolean = "oldText" in args || "newText" in args

    /** ops 条数；非数组或空返回 0。 */
    fun opCount(args: JsonObject): Int = (args["ops"] as? JsonArray)?.size ?: 0

    /**
     * 审批 / 状态 / 卡片用的短摘要，例如 `Hashline 3 ops`。
     * 无 ops 时返回 null（走 legacy 展示）。
     */
    fun opsLabel(args: JsonObject): String? {
        val count = opCount(args)
        if (count <= 0) return null
        return "Hashline $count ops"
    }
}
