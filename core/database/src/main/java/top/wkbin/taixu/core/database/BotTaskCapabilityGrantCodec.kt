package top.wkbin.taixu.core.database

import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.model.bot.BotCapabilityPolicy
import top.wkbin.taixu.core.model.bot.BotTaskCapabilityGrant

/** 空值沿用旧任务语义，损坏的非空授权保留为无法匹配任何任务的拒绝记录。 */
internal fun decodeTaskCapabilityGrant(raw: String?, json: Json): BotTaskCapabilityGrant? = raw?.let {
    runCatching { json.decodeFromString<BotTaskCapabilityGrant>(it) }.getOrElse {
        BotTaskCapabilityGrant("", "", null, "", null, BotCapabilityPolicy(emptyList(), emptyMap()), version = 0)
    }
}
