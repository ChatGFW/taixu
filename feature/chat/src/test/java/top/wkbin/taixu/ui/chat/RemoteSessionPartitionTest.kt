package top.wkbin.taixu.ui.chat

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.core.model.SessionExecutionTarget
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject

class RemoteSessionPartitionTest {
    private val serverA = RemoteServer(
        id = "a",
        name = "Alpha",
        host = "a.test",
        username = "root",
        workspace = "/home/a",
        fingerprint = "SHA256:a",
    )
    private val serverB = RemoteServer(
        id = "b",
        name = "Beta",
        host = "b.test",
        username = "agent",
        workspace = "/home/b",
        fingerprint = "SHA256:b",
    )

    private fun session(
        id: String,
        workspace: String = "",
        updatedAt: Long = 1L,
        target: SessionExecutionTarget? = null,
    ) = HarnessSessionEntity(
        id = id,
        title = id,
        createdAt = 0,
        updatedAt = updatedAt,
        modelId = null,
        workspace = workspace,
        executionTargetJson = target?.let { Json.encodeToString(SessionExecutionTarget.serializer(), it) },
    )

    @Test
    fun `remote sessions are excluded from local project partition`() {
        val localProject = WorkspaceProject("demo", "/workspace/demo", "/workspace/demo", 0, projectType = ProjectType.ANDROID)
        val local = session("local", "/workspace/demo", 10, SessionExecutionTarget("debian", "/workspace/demo"))
        val remote = session(
            "remote",
            "/workspace/demo",
            20,
            SessionExecutionTarget(serverA.distributionId, "/workspace/demo", serverA),
        )
        val recent = session("recent", "", 5, SessionExecutionTarget("debian", ""))
        val (remoteOnly, localOnly) = listOf(local, remote, recent).partition(::isRemoteBoundSession)
        assertEquals(listOf("remote"), remoteOnly.map { it.id })
        val partition = partitionSessions(localOnly, listOf(localProject))
        assertEquals(listOf("local"), partition.projectGroups.single().sessions.map { it.id })
        assertEquals(listOf("recent"), partition.recentSessions.map { it.id })
        assertFalse(partition.projectGroups.single().sessions.any { it.id == "remote" })
    }

    @Test
    fun `servers group sessions and keep empty configured profiles`() {
        val s1 = session(
            "s1",
            "/workspace",
            30,
            SessionExecutionTarget(serverA.distributionId, "/workspace", serverA),
        )
        val s2 = session(
            "s2",
            "/workspace",
            10,
            SessionExecutionTarget(serverA.distributionId, "/workspace", serverA),
        )
        val groups = partitionRemoteSessions(listOf(s1, s2), listOf(serverA, serverB))
        assertEquals(2, groups.size)
        val alpha = groups.first { it.serverId == "a" }
        assertEquals(listOf("s1", "s2"), alpha.sessions.map { it.id })
        assertEquals(serverA.distributionId, alpha.distribution)
        val beta = groups.first { it.serverId == "b" }
        assertTrue(beta.sessions.isEmpty())
    }

    @Test
    fun `attachRemoteProjects nests matching workspaces under project buckets`() {
        val root = session(
            "root",
            "/workspace",
            40,
            SessionExecutionTarget(serverA.distributionId, "/workspace", serverA),
        )
        val inDemo = session(
            "demo",
            "/workspace/demo",
            30,
            SessionExecutionTarget(serverA.distributionId, "/workspace/demo", serverA),
        )
        val groups = attachRemoteProjects(
            partitionRemoteSessions(listOf(root, inDemo), listOf(serverA)),
            mapOf(
                "a" to listOf(
                    WorkspaceProject("demo", "/home/a/demo", "/workspace/demo", 0, ownsDirectory = false),
                ),
            ),
        )
        val alpha = groups.single()
        assertEquals(listOf("demo"), alpha.projectBuckets.map { it.name })
        assertEquals(listOf("demo"), alpha.projectBuckets.single().sessions.map { it.id })
        assertEquals(listOf("root"), alpha.rootSessions.map { it.id })
    }

    @Test
    fun `orphan remote snapshot remains after profile removal`() {
        val orphan = session(
            "orphan",
            "/workspace",
            1,
            SessionExecutionTarget(serverA.distributionId, "/workspace", serverA),
        )
        val groups = partitionRemoteSessions(listOf(orphan), emptyList())
        assertEquals(1, groups.size)
        assertEquals("a", groups.single().serverId)
        assertEquals("Alpha", groups.single().serverName)
        assertFalse(groups.single().profilePresent)
        assertEquals(orphanRemoteGroupKey(serverA), groups.single().groupKey)
    }

    @Test
    fun `edited host keeps old sessions under snapshot label not live profile`() {
        val oldSession = session(
            "old",
            "/workspace",
            20,
            SessionExecutionTarget(serverA.distributionId, "/workspace", serverA),
        )
        val edited = serverA.copy(host = "new.test", fingerprint = "SHA256:new")
        val groups = partitionRemoteSessions(listOf(oldSession), listOf(edited))
        assertEquals(2, groups.size)
        val live = groups.single { it.profilePresent }
        assertEquals(edited.host, live.hostLabel.substringAfter('@'))
        assertTrue(live.sessions.isEmpty())
        assertEquals(edited.id, live.serverId)
        val stale = groups.single { !it.profilePresent }
        assertEquals(listOf("old"), stale.sessions.map { it.id })
        assertEquals("root@a.test", stale.hostLabel)
        assertEquals(orphanRemoteGroupKey(serverA), stale.groupKey)
    }
}
