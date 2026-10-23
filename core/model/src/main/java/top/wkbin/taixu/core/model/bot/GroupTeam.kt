package top.wkbin.taixu.core.model.bot

/** Group-local reporting lines. Missing overrides mean a direct report to the group administrator. */
data class GroupTeam(
    val adminBotId: String,
    val memberIds: Set<String>,
    val supervisors: Map<String, String> = emptyMap(),
) {
    fun supervisorOf(botId: String): String? = when {
        botId == adminBotId || botId !in memberIds -> null
        else -> supervisors[botId]?.takeIf { it in memberIds } ?: adminBotId
    }

    fun directReports(botId: String): Set<String> = memberIds.filterTo(linkedSetOf()) { supervisorOf(it) == botId }

    fun canAssign(callerId: String, targetId: String): Boolean =
        callerId in memberIds && targetId in memberIds && supervisorOf(targetId) == callerId

    fun canInspect(callerId: String, targetId: String): Boolean {
        if (callerId !in memberIds || targetId !in memberIds) return false
        var current: String? = targetId
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current)) {
            if (current == callerId) return true
            current = supervisorOf(current)
        }
        return false
    }

    fun validate() {
        require(adminBotId in memberIds) { "群主控必须在团队中" }
        require(supervisors.keys.all { it in memberIds && it != adminBotId }) { "只能设置普通群成员的上级" }
        require(supervisors.values.all { it in memberIds }) { "直属上级必须是本群成员" }
        memberIds.forEach { id ->
            var current: String? = id
            val visited = mutableSetOf<String>()
            while (current != null) {
                require(visited.add(current)) { "上下级关系不能形成循环" }
                require(visited.size <= MAX_LEVELS) { "团队最多支持三层；简单任务保持直属关系" }
                current = supervisorOf(current)
            }
        }
    }

    companion object { const val MAX_LEVELS = 3 }
}
