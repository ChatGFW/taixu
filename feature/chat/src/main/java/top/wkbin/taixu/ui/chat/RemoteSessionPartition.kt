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

/**
 * 将远程绑定会话按服务器分组；已配置档案即使无会话也保留空组。
 * 档案已删但会话仍带快照的，归入孤儿组。
 */
fun partitionRemoteSessions(
    sessions: List<HarnessSessionEntity>,
    servers: List<RemoteServer>,
): List<RemoteServerSessionGroup> {
    val byId = mutableMapOf<String, MutableList<HarnessSessionEntity>>()
    val snapshots = mutableMapOf<String, RemoteServer>()
    for (session in sessions) {
        val target = sessionRemoteTarget(session) ?: continue
        val remote = target.remote!!
        byId.getOrPut(remote.id) { mutableListOf() }.add(session)
        snapshots.putIfAbsent(remote.id, remote)
    }
    val groups = mutableListOf<RemoteServerSessionGroup>()
    val seen = mutableSetOf<String>()
    for (server in servers) {
        seen.add(server.id)
        val list = byId[server.id].orEmpty().sortedByDescending { it.updatedAt }
        groups.add(
            RemoteServerSessionGroup(
                serverId = server.id,
                serverName = server.name,
                distribution = server.distributionId,
                hostLabel = "${server.username}@${server.host}",
                sessions = list,
                rootSessions = list,
            ),
        )
    }
    for ((id, list) in byId) {
        if (id in seen) continue
        val remote = snapshots[id] ?: continue
        val sorted = list.sortedByDescending { it.updatedAt }
        groups.add(
            RemoteServerSessionGroup(
                serverId = id,
                serverName = remote.name,
                distribution = remote.distributionId,
                hostLabel = "${remote.username}@${remote.host}",
                sessions = sorted,
                rootSessions = sorted,
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
