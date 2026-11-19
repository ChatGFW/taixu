package top.wkbin.taixu.runtime.bridge.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAdbContractTest {

    @Test
    fun logcatRequestDefaultsAreValid() {
        val request = EmbeddedAdbManager.LogcatRequest()
        assertEquals("", request.packageName)
        assertEquals("", request.tag)
        assertEquals('V', request.priority)
        assertEquals("", request.keyword)
        assertEquals(200, request.lines)
    }

    @Test
    fun connectionStatesExposeExpectedProperties() {
        val connected = EmbeddedAdbManager.ConnectionState.Connected("127.0.0.1", 5555)
        assertEquals("127.0.0.1", connected.host)
        assertEquals(5555, connected.port)

        val failed = EmbeddedAdbManager.ConnectionState.Failed("配对失败")
        assertEquals("配对失败", failed.message)
    }

    @Test
    fun discoveryStateTracksEndpoints() {
        val pairing = listOf(EmbeddedAdbManager.Endpoint("pairing-service", "127.0.0.1", 37159))
        val connect = listOf(EmbeddedAdbManager.Endpoint("connect-service", "127.0.0.1", 41235))
        val state = EmbeddedAdbManager.DiscoveryState(
            running = true,
            pairingEndpoints = pairing,
            connectEndpoints = connect,
        )
        assertTrue(state.running)
        assertEquals(1, state.pairingEndpoints.size)
        assertEquals(37159, state.pairingEndpoints.first().port)
        assertEquals(1, state.connectEndpoints.size)
        assertEquals(41235, state.connectEndpoints.first().port)
    }

    @Test
    fun shellOutcomeReflectsStatus() {
        val successOutcome = EmbeddedAdbManager.ShellOutcome(0, "ok\n", true)
        assertEquals(0, successOutcome.exitCode)
        assertTrue(successOutcome.success)
        assertEquals("ok\n", successOutcome.output)

        val failureOutcome = EmbeddedAdbManager.ShellOutcome(1, "error", false)
        assertEquals(1, failureOutcome.exitCode)
        assertFalse(failureOutcome.success)
    }

    @Test
    fun consumerTagsAreDefinedAndDiscoveryDefaultsToNotRunning() {
        assertEquals("manual", EmbeddedAdbManager.TAG_MANUAL)
        assertEquals("adb_notification", EmbeddedAdbManager.TAG_NOTIFICATION)
        assertEquals("developer_ui", EmbeddedAdbManager.TAG_DEVELOPER_UI)

        val defaultDiscovery = EmbeddedAdbManager.DiscoveryState()
        assertFalse("默认发现状态必须为非运行态，避免冷启动触发 mDNS 扫描", defaultDiscovery.running)
        assertTrue(defaultDiscovery.pairingEndpoints.isEmpty())
        assertTrue(defaultDiscovery.connectEndpoints.isEmpty())
    }

    // ── 执行通道可用性判据 ────────────────────────────────────────────────
    // /api/health 与 /api/doctor 曾直接读 state is Connected；而 state 同时被
    // mDNS 发现/配对流程改写为 Discovering/Pairing，导致「logcat 能跑但 health
    // 报 adb:false」。以下用例钉住：有 client 即为真，且发现类状态不得否定通道。

    @Test
    fun channelIsLiveWheneverAClientIsHeldRegardlessOfVisibleState() {
        val pollutedStates = listOf(
            EmbeddedAdbManager.ConnectionState.Disconnected,
            EmbeddedAdbManager.ConnectionState.Discovering,
            EmbeddedAdbManager.ConnectionState.Pairing,
            EmbeddedAdbManager.ConnectionState.Connecting,
            EmbeddedAdbManager.ConnectionState.Failed("mDNS 自动发现失败"),
            EmbeddedAdbManager.ConnectionState.Connected("127.0.0.1", 5555),
        )
        pollutedStates.forEach { state ->
            assertTrue(
                "持有 client 时任何可见状态都必须视为可用，实际=$state",
                AdbChannelState.isLive(hasClient = true, state = state),
            )
        }
    }

    @Test
    fun discoveryProgressStatesDoNotCountAsLiveWithoutClient() {
        // 回归点：这两个状态既不表示可用，也不表示不可用，不能当作「已连接」的真值。
        listOf(
            EmbeddedAdbManager.ConnectionState.Discovering,
            EmbeddedAdbManager.ConnectionState.Pairing,
            EmbeddedAdbManager.ConnectionState.Connecting,
        ).forEach { state ->
            assertFalse(
                "无 client 时发现类状态不得判为可用，实际=$state",
                AdbChannelState.isLive(hasClient = false, state = state),
            )
        }
    }

    @Test
    fun explicitConnectedStateIsLiveEvenBeforeClientFieldIsObserved() {
        assertTrue(
            AdbChannelState.isLive(
                hasClient = false,
                state = EmbeddedAdbManager.ConnectionState.Connected("127.0.0.1", 5555),
            ),
        )
        assertFalse(
            AdbChannelState.isLive(
                hasClient = false,
                state = EmbeddedAdbManager.ConnectionState.Failed("无线 ADB 连接失败"),
            ),
        )
        assertFalse(
            AdbChannelState.isLive(
                hasClient = false,
                state = EmbeddedAdbManager.ConnectionState.Disconnected,
            ),
        )
    }
}
