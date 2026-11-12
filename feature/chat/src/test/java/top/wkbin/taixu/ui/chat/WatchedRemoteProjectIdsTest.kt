package top.wkbin.taixu.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.model.RemoteServer

class WatchedRemoteProjectIdsTest {
    private val alpha = RemoteServerSessionGroup(
        serverId = "a",
        serverName = "Alpha",
        distribution = "ssh:a",
        hostLabel = "root@a.test",
        sessions = emptyList(),
        profilePresent = true,
        groupKey = "a",
    )
    private val beta = alpha.copy(
        serverId = "b",
        serverName = "Beta",
        distribution = "ssh:b",
        hostLabel = "root@b.test",
        groupKey = "b",
    )
    private val stale = alpha.copy(
        serverName = "Old",
        hostLabel = "root@old.test",
        profilePresent = false,
        groupKey = orphanRemoteGroupKey(
            RemoteServer("a", "Old", "old.test", username = "root", fingerprint = "SHA256:old"),
        ),
    )

    @Test
    fun `closed drawer or collapsed section yields empty watch set`() {
        assertTrue(
            watchedRemoteProjectIds(
                drawerVisible = false,
                remotesSectionExpanded = true,
                searchActive = false,
                expandedGroupKeys = setOf("a", "b"),
                groups = listOf(alpha, beta),
            ).isEmpty(),
        )
        assertTrue(
            watchedRemoteProjectIds(
                drawerVisible = true,
                remotesSectionExpanded = false,
                searchActive = false,
                expandedGroupKeys = setOf("a", "b"),
                groups = listOf(alpha, beta),
            ).isEmpty(),
        )
    }

    @Test
    fun `only currently expanded live profiles are watched`() {
        assertEquals(
            setOf("a"),
            watchedRemoteProjectIds(
                drawerVisible = true,
                remotesSectionExpanded = true,
                searchActive = false,
                expandedGroupKeys = setOf("a"),
                groups = listOf(alpha, beta, stale),
            ),
        )
        assertEquals(
            setOf("b"),
            watchedRemoteProjectIds(
                drawerVisible = true,
                remotesSectionExpanded = true,
                searchActive = false,
                expandedGroupKeys = setOf("b"),
                groups = listOf(alpha, beta),
            ),
        )
    }

    @Test
    fun `search watches all matching live profiles then clears when collapsed`() {
        assertEquals(
            setOf("a", "b"),
            watchedRemoteProjectIds(
                drawerVisible = true,
                remotesSectionExpanded = true,
                searchActive = true,
                expandedGroupKeys = emptySet(),
                groups = listOf(alpha, beta, stale),
            ),
        )
        assertTrue(
            watchedRemoteProjectIds(
                drawerVisible = true,
                remotesSectionExpanded = false,
                searchActive = true,
                expandedGroupKeys = emptySet(),
                groups = listOf(alpha, beta),
            ).isEmpty(),
        )
    }
}
