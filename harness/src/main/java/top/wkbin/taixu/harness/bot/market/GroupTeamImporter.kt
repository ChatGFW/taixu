package top.wkbin.taixu.harness.bot.market

import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.database.repository.GroupChatRepository
import top.wkbin.taixu.core.model.bot.*
import top.wkbin.taixu.harness.bot.BotOrchestrator

/** Prepare independent members, then publish membership, reporting lines and policy atomically. */
class GroupTeamImporter internal constructor(
    private val catalog: suspend () -> List<MarketBotItem>,
    private val createMember: suspend (MarketBotItem, String, String, String) -> Bot,
    private val removeMember: suspend (String) -> Unit,
    private val groups: GroupChatRepository,
) {
    constructor(market: BotMarketRepository, orchestrator: BotOrchestrator, groups: GroupChatRepository) : this(
        { market.catalog() },
        { item, name, role, persona -> orchestrator.createBot(name, role, persona, colorHex = item.colorHex, avatarSeed = item.avatarSeed) },
        { orchestrator.deleteBot(it) }, groups,
    )
    suspend fun import(templateId: String, name: String, existingGroupId: String? = null): GroupChat {
        val template = GroupTeamTemplates.all.single { it.id == templateId }.also { it.validate() }
        require(name.isNotBlank()) { "请填写群聊名称" }
        val existing = existingGroupId?.let { requireNotNull(groups.getGroup(it)) { "群聊已删除" } }
        val groupId = existing?.id ?: UUID.randomUUID().toString()
        val created = mutableListOf<Bot>()
        val ids = mutableMapOf<String, String>()
        var published = false
        try {
            if (existing != null) ids[template.adminKey] = existing.adminBotId
            val catalog = catalog().associateBy { it.id }
            val usedNames = existing?.let { groups.getMembers(it.id).map { bot -> bot.name }.toMutableSet() } ?: mutableSetOf()
            template.members.filter { it.key !in ids }.forEach { seat ->
                val item = requireNotNull(catalog[seat.marketId]) { "模板成员不存在" }
                val baseName = seat.name ?: item.name
                var memberName = baseName
                var suffix = 2
                while (memberName in usedNames) { val tail = " (${suffix++})"; memberName = baseName.take(40 - tail.length) + tail }
                usedNames += memberName
                val persona = item.systemPrompt + "\n\n本团队身份：$memberName，职责：${seat.role ?: item.role}。群内组织、审议权限及真实工具结果优先于角色叙事。"
                val bot = createMember(item, memberName, seat.role ?: item.role, persona)
                created += bot
                ids[seat.key] = bot.id
            }
            val group = existing ?: GroupChat(groupId, name.trim().take(40), ids.getValue(template.adminKey),
                description = template.description, createdAt = System.currentTimeMillis())
            val supervisors = template.members.mapNotNull { seat -> seat.supervisorKey?.let {
                ids.getValue(seat.key) to ids.getValue(it)
            } }.toMap()
            val rules = template.rules.map { it.copy(id = UUID.randomUUID().toString(), authorId = ids.getValue(it.authorId),
                reviewerId = ids.getValue(it.reviewerId), executorId = ids.getValue(it.executorId)) }
            withContext(NonCancellable) {
                groups.installTeamTemplate(group, created.map { it.id }, supervisors, GroupGovernance(rules), existing == null)
                published = true
            }
            return group
        } catch (failure: Throwable) {
            if (!published) withContext(NonCancellable) {
                created.asReversed().forEach { bot ->
                    runCatching { removeMember(bot.id) }.exceptionOrNull()?.let(failure::addSuppressed)
                }
            }
            throw failure
        }
    }
}
