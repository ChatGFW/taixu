package top.wkbin.taixu.ui.chat

import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.core.model.SessionExecutionTarget
import top.wkbin.taixu.runtime.WorkspaceProject

/** 远程服务器下的项目桶（SFTP 工作目录子目录）。 */
data class RemoteProjectBucket(
    val name: String,
    val linuxPath: String,
    val sessions: List<HarnessSessionEntity>,
)

/** 侧边栏按 SSH 连接档案划分的会话组。 */
data class RemoteServerSessionGroup(
    val serverId: String,
    val serverName: String,
    val distribution: String,
    val hostLabel: String,
    val sessions: List<HarnessSessionEntity>,
    val projectBuckets: List<RemoteProjectBucket> = emptyList(),
    val rootSessions: List<HarnessSessionEntity> = emptyList(),
    /** 当前仍存在且身份与会话快照一致的档案；否则禁止新建对话/终端/概览。 */
    val profilePresent: Boolean = true,
    /** 展开与订阅用的稳定键；主机变更后的孤儿组与在线档案区分开。 */
    val groupKey: String = serverId,
)

private val targetJson = Json { ignoreUnknownKeys = true }

/** 解析会话是否绑定 SSH 远程执行目标；本机 / unbound / 损坏 JSON 返回 null。 */
fun sessionRemoteTarget(session: HarnessSessionEntity): SessionExecutionTarget? {
    val raw = session.executionTargetJson ?: return null
    if (raw == "unbound" || raw == "restore-required") return null
    return runCatching {
        targetJson.decodeFromString(SessionExecutionTarget.serializer(), raw)
    }.getOrNull()?.takeIf { it.distribution.startsWith("ssh:") && it.remote != null }
}

fun isRemoteBoundSession(session: HarnessSessionEntity): Boolean = sessionRemoteTarget(session) != null

/** 与执行层 `server == expected` 一致：同 id 但主机/指纹变更视为不同身份。 */
fun remoteIdentityMatches(snapshot: RemoteServer, current: RemoteServer): Boolean = snapshot == current

fun orphanRemoteGroupKey(remote: RemoteServer): String =
    "orphan:${remote.id}:${remote.fingerprint.ifBlank { "${remote.host}:${remote.port}" }}"

/**
 * 将远程绑定会话按服务器分组；已配置档案即使无会话也保留空组。
 * 档案已删、或同 id 但主机身份已变的会话，归入孤儿组并沿用快照标签。
 */
fun partitionRemoteSessions(
    sessions: List<HarnessSessionEntity>,
    servers: List<RemoteServer>,
): List<RemoteServerSessionGroup> {
    val byId = mutableMapOf<String, MutableList<Pair<HarnessSessionEntity, RemoteServer>>>()
    for (session in sessions) {
        val remote = sessionRemoteTarget(session)?.remote ?: continue
        byId.getOrPut(remote.id) { mutableListOf() }.add(session to remote)
    }
    val groups = mutableListOf<RemoteServerSessionGroup>()
    val liveIds = servers.map { it.id }.toSet()
    val orphanBuckets = linkedMapOf<RemoteServer, MutableList<HarnessSessionEntity>>()
    for (server in servers) {
        val entries = byId[server.id].orEmpty()
        val matching = entries.filter { remoteIdentityMatches(it.second, server) }.map { it.first }
            .sortedByDescending { it.updatedAt }
        entries.filterNot { remoteIdentityMatches(it.second, server) }.forEach { (session, snapshot) ->
            orphanBuckets.getOrPut(snapshot) { mutableListOf() }.add(session)
        }
        groups.add(
            RemoteServerSessionGroup(
                serverId = server.id,
                serverName = server.name,
                distribution = server.distributionId,
                hostLabel = "${server.username}@${server.host}",
                sessions = matching,
                rootSessions = matching,
                profilePresent = true,
                groupKey = server.id,
            ),
        )
    }
    for ((id, entries) in byId) {
        if (id in liveIds) continue
        entries.forEach { (session, snapshot) ->
            orphanBuckets.getOrPut(snapshot) { mutableListOf() }.add(session)
        }
    }
    for ((remote, list) in orphanBuckets) {
        val sorted = list.distinctBy { it.id }.sortedByDescending { it.updatedAt }
        groups.add(
            RemoteServerSessionGroup(
                serverId = remote.id,
                serverName = remote.name,
                distribution = remote.distributionId,
                hostLabel = "${remote.username}@${remote.host}",
                sessions = sorted,
                rootSessions = sorted,
                profilePresent = false,
                groupKey = orphanRemoteGroupKey(remote),
            ),
        )
    }
    return groups.sortedWith(
        compareByDescending<RemoteServerSessionGroup> { it.sessions.firstOrNull()?.updatedAt ?: 0L }
            .thenBy { it.serverName.lowercase() },
    )
}

/** 按远程项目路径挂会话；未匹配项目的会话留在 rootSessions。 */
fun attachRemoteProjects(
    groups: List<RemoteServerSessionGroup>,
    projectsByServer: Map<String, List<WorkspaceProject>>,
): List<RemoteServerSessionGroup> = groups.map { group ->
    if (!group.profilePresent) {
        return@map group.copy(projectBuckets = emptyList(), rootSessions = group.sessions)
    }
    val projects = projectsByServer[group.serverId].orEmpty()
    if (projects.isEmpty()) {
        group.copy(projectBuckets = emptyList(), rootSessions = group.sessions)
    } else {
        val remaining = group.sessions.toMutableList()
        val buckets = projects.map { project ->
            val path = project.linuxPath.trimEnd('/')
            val matched = remaining.filter { it.workspace.trimEnd('/') == path }
            remaining.removeAll(matched.toSet())
            RemoteProjectBucket(project.name, project.linuxPath, matched.sortedByDescending { it.updatedAt })
        }
        group.copy(projectBuckets = buckets, rootSessions = remaining.sortedByDescending { it.updatedAt })
    }
}
