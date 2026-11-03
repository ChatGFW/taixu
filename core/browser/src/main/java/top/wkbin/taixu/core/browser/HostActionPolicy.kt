package top.wkbin.taixu.core.browser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 浏览器 / 主机动作策略闸门（OpenBot CEL 策略的精简落地）。
 *
 * - **deny 先于 allow**；deny 命中即拒；allow 无一命中则拒（失败封闭）。
 * - 损坏的 deny 规则 → 拒绝；损坏的 allow 规则 → 不放行（跳过）。
 * - 出厂默认 `deny:[]` + `allow:["true"]`（与 OpenBot 启动默认同语义），不打断现有能力。
 * - 云元数据地址始终硬拒绝，不受 allow 覆盖。
 *
 * 仅门控 Bot 侧浏览器工具入口；不替代 harness 审批风险矩阵，也不挡用户手势导航。
 */
object HostActionPolicy {
    const val MAX_JSON_CHARS = 16_384

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prettyJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    @Volatile
    private var document: ActionPolicyDocument = ActionPolicyDocument.DEFAULT

    fun current(): ActionPolicyDocument = document

    fun install(policy: ActionPolicyDocument) {
        document = policy.normalized()
    }

    /** 解析失败返回错误且**不**替换现行策略（避免半截配置把闸门打穿或误封）。 */
    fun installJson(raw: String): Result<ActionPolicyDocument> =
        validateAndParse(raw).onSuccess { document = it }

    /**
     * 解析 + 表达式试跑（不安装）。供设置页保存前校验；allow 不得为空。
     * 空白输入视为出厂默认。
     */
    fun validateAndParse(raw: String): Result<ActionPolicyDocument> = runCatching {
        val trimmed = raw.trim()
        if (trimmed.length > MAX_JSON_CHARS) {
            error("策略 JSON 超过 ${MAX_JSON_CHARS} 字符上限")
        }
        val doc = if (trimmed.isEmpty()) {
            ActionPolicyDocument.DEFAULT
        } else {
            json.decodeFromString(ActionPolicyDocument.serializer(), trimmed).normalized()
        }
        if (doc.allow.isEmpty()) error("allow 不能为空（失败封闭会导致全部拒绝）")
        val sample = ActionContext(
            toolName = "browser.navigate",
            pageUrl = "https://example.com/",
            pageHost = "example.com",
            initiatorKind = "person",
        ).toVars()
        for (rule in doc.deny) {
            runCatching { CelLite.eval(rule, sample) }
                .getOrElse { error("deny 规则无效「$rule」：${it.message}") }
        }
        for (rule in doc.allow) {
            runCatching { CelLite.eval(rule, sample) }
                .getOrElse { error("allow 规则无效「$rule」：${it.message}") }
        }
        doc
    }

    fun encodePretty(policy: ActionPolicyDocument = current()): String =
        prettyJson.encodeToString(ActionPolicyDocument.serializer(), policy.normalized())

    /** DataStore 空串或非法配置时回落默认；合法则安装。 */
    fun installFromStored(raw: String?) {
        val text = raw.orEmpty()
        if (text.isBlank()) {
            resetToDefault()
            return
        }
        installJson(text).getOrElse { resetToDefault() }
    }

    fun resetToDefault() {
        document = ActionPolicyDocument.DEFAULT
    }

    fun evaluate(ctx: ActionContext): PolicyDecision {
        hardDenyReason(ctx.pageHost, ctx.pageUrl)?.let { return PolicyDecision.Deny(it) }

        val vars = ctx.toVars()
        val policy = document

        for (rule in policy.deny) {
            val matched = runCatching { CelLite.eval(rule, vars) }
                .getOrElse { return PolicyDecision.Deny("策略 deny 规则无效（已拒绝）：${it.message}") }
            if (matched) {
                return PolicyDecision.Deny("策略拒绝（deny）：$rule")
            }
        }

        if (policy.allow.isEmpty()) {
            return PolicyDecision.Deny("策略 allow 为空（失败封闭）")
        }

        for (rule in policy.allow) {
            val matched = runCatching { CelLite.eval(rule, vars) }.getOrNull() ?: continue
            if (matched) return PolicyDecision.Allow
        }
        return PolicyDecision.Deny("策略未命中任何 allow 规则")
    }

    fun refuseMessage(decision: PolicyDecision.Deny): String =
        "${decision.reason}。请调整主机动作策略或改用允许的目标。"

    /** 从 URL 抽取 host（JVM 侧，不依赖 android.net.Uri）。 */
    fun hostOf(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val noScheme = trimmed.substringAfter("://", missingDelimiterValue = trimmed)
        val authority = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostPort = authority.substringAfter('@', authority)
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBefore(':')
        }
        return host.trim().lowercase()
    }

    private fun hardDenyReason(host: String, url: String): String? {
        val h = host.lowercase()
        val u = url.lowercase()
        if (h in HARD_DENY_HOSTS || HARD_DENY_HOSTS.any { u.contains(it) }) {
            return "拒绝访问云元数据地址（$h）"
        }
        return null
    }

    private val HARD_DENY_HOSTS = setOf(
        "169.254.169.254",
        "metadata.google.internal",
    )
}

@Serializable
data class ActionPolicyDocument(
    val deny: List<String> = emptyList(),
    val allow: List<String> = listOf("true"),
) {
    fun normalized(): ActionPolicyDocument = copy(
        deny = deny.map { it.trim() }.filter { it.isNotEmpty() },
        allow = allow.map { it.trim() }.filter { it.isNotEmpty() },
    )

    companion object {
        val DEFAULT = ActionPolicyDocument()
    }
}

data class ActionContext(
    val toolName: String,
    val pageUrl: String = "",
    val pageHost: String = "",
    val initiatorKind: String = "person",
    val intent: String = "",
) {
    fun toVars(): Map<String, String> = mapOf(
        "tool.name" to toolName,
        "page.url" to pageUrl,
        "page.host" to pageHost,
        "initiator.kind" to initiatorKind,
        "intent" to intent,
    )
}

sealed class PolicyDecision {
    data object Allow : PolicyDecision()
    data class Deny(val reason: String) : PolicyDecision()
}
