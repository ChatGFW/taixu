package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.core.model.bot.BotSessionGroupRef
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.RuntimeWidthClass

internal data class DrawerPartitions(
    val local: SessionPartitionResult,
    val remotes: List<RemoteServerSessionGroup>,
    val bots: List<HarnessSessionEntity>,
    val pinned: List<HarnessSessionEntity>,
)

/** 侧栏本地/远程/Bot/置顶分区；从 SessionsSideDrawer 抽出以守尺寸棘轮。 */
internal fun buildDrawerPartitions(
    sessions: List<HarnessSessionEntity>,
    botSessions: List<HarnessSessionEntity>,
    botSessionGroups: Map<String, BotSessionGroupRef>,
    hiddenBotSessionIds: Set<String>,
    workspaces: List<WorkspaceProject>,
    remoteServers: List<RemoteServer>,
    projectsByServer: Map<String, List<WorkspaceProject>>,
    query: String,
): DrawerPartitions {
    val term = query.trim()
    fun HarnessSessionEntity.matches() = term.isEmpty() ||
        title.contains(term, ignoreCase = true) || workspace.contains(term, ignoreCase = true)
    val filtered = sessions.filter { it.matches() && it.pinnedAt == 0L }
    val pinnedMatches = sessions.filter { it.matches() && it.pinnedAt != 0L }.sortedByDescending { it.pinnedAt }
    val (remoteSessions, localSessions) = filtered.partition(::isRemoteBoundSession)
    val localPartition = partitionSessions(localSessions, workspaces)
    val remotes = attachRemoteProjects(partitionRemoteSessions(remoteSessions, remoteServers), projectsByServer)
    val bots = botSessions.filter { it.matches() && it.id !in hiddenBotSessionIds }
        .filter { term.isNotEmpty() || it.id in botSessionGroups }
        .sortedByDescending { it.updatedAt }
    return DrawerPartitions(
        local = if (term.isEmpty()) localPartition
        else localPartition.copy(projectGroups = localPartition.projectGroups.filter { it.sessions.isNotEmpty() }),
        remotes = if (term.isEmpty()) remotes
        else remotes.filter { it.sessions.isNotEmpty() || it.projectBuckets.any { b -> b.sessions.isNotEmpty() } },
        bots = bots,
        pinned = pinnedMatches,
    )
}

/**
 * 宽屏 Expanded 看停靠态 [docked]；紧凑/中等看覆盖抽屉 [overlayVisible]。
 * 不能只用 overlayVisible，否则 Expanded 停靠侧栏永远不订阅。
 */
internal fun remoteSidebarWatchEnabled(
    widthClass: RuntimeWidthClass,
    overlayVisible: Boolean,
    docked: Boolean,
): Boolean = when (widthClass) {
    RuntimeWidthClass.Expanded -> docked
    else -> overlayVisible
}

/**
 * 侧栏远程 SFTP 订阅集合：仅抽屉可见、远程分区展开、且当前展开的在线档案。
 * 搜索态视为展开全部匹配组；关闭/折叠必须得到空集以停掉 15s 轮询。
 */
internal fun watchedRemoteProjectIds(
    drawerVisible: Boolean,
    remotesSectionExpanded: Boolean,
    searchActive: Boolean,
    expandedGroupKeys: Set<String>,
    groups: List<RemoteServerSessionGroup>,
): Set<String> {
    if (!drawerVisible || !remotesSectionExpanded) return emptySet()
    val expanded = if (searchActive) groups.map { it.groupKey }.toSet() else expandedGroupKeys
    return groups.asSequence()
        .filter { it.profilePresent && it.groupKey in expanded }
        .map { it.serverId }
        .toSet()
}

internal fun initialExpandedServers(
    remoteGroups: List<RemoteServerSessionGroup>,
    currentSessionId: String,
): Set<String> = remoteGroups
    .filter { group -> group.sessions.any { it.id == currentSessionId } }
    .map { it.groupKey }
    .toSet()

internal fun initialExpandedRemoteProjects(
    remoteGroups: List<RemoteServerSessionGroup>,
    currentSessionId: String,
): Set<String> = remoteGroups.flatMap { group ->
    group.projectBuckets.filter { bucket -> bucket.sessions.any { it.id == currentSessionId } }
        .map { "${group.groupKey}:${it.linuxPath}" }
}.toSet()
