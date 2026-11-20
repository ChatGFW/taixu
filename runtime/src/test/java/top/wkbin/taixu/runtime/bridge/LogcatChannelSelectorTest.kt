package top.wkbin.taixu.runtime.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.runtime.bridge.LogcatChannelSelector.Decision
import top.wkbin.taixu.runtime.bridge.LogcatChannelSelector.PrivilegeInfoSnapshot

/**
 * 锁定 `/api/logcat` 的通道选择规则。
 *
 * 上一轮评审的阻塞全部出在这段选择逻辑上，因此逐条钉死：
 * 1. ADB 已返回非空 exitCode 时不回退，且不触发特权探测；
 * 2. 生效模式为 PRoot 时不回退，且不触发特权探测；
 * 3. 仅在 exitCode == null 且特权就绪时才走特权通道；
 * 4. 特权路径 exitCode == -1 时保留 ADB 结果；
 * 5. 仅 success 时做关键词过滤。
 */
class LogcatChannelSelectorTest {

    private val rootReady = PrivilegeInfoSnapshot(
        modeActive = true,
        mode = ExecutionMode.ROOT,
        shizukuAvailable = false,
        rootAvailable = true,
    )

    // --- 1. ADB 已实际执行并拿到退出码：不得回退，也不得探测 ---

    @Test
    fun adbNonZeroExitCodeDoesNotFallBack() {
        // exit 3 = 目标应用未运行：ADB 通道本身可用，换通道无收益
        assertEquals(
            Decision.USE_ADB,
            LogcatChannelSelector.decide(3, ExecutionMode.ROOT, rootReady),
        )
    }

    @Test
    fun adbNonZeroExitCodeSkipsPrivilegeProbeEntirely() {
        // 关键：Root 模式下 getPrivilegeInfo() 会实跑 su 探测，失败即降级 PRoot，
        // 因此 ADB 已成功时必须连探测都不发生
        assertFalse(LogcatChannelSelector.needsProbe(0, ExecutionMode.ROOT))
        assertFalse(LogcatChannelSelector.needsProbe(3, ExecutionMode.ROOT))
    }

    // --- 2. PRoot 下不得回退（即使 modeActive 恒为 true） ---

    @Test
    fun prootModeSkipsProbeAndStaysOnAdb() {
        assertFalse(LogcatChannelSelector.needsProbe(null, ExecutionMode.PROOT))
    }

    @Test
    fun prootModeDoesNotFallBackEvenWhenShizukuAvailable() {
        val prootInfo = PrivilegeInfoSnapshot(
            modeActive = true,
            mode = ExecutionMode.PROOT,
            shizukuAvailable = true,
            rootAvailable = false,
        )
        assertEquals(
            Decision.USE_ADB,
            LogcatChannelSelector.decide(null, ExecutionMode.PROOT, prootInfo),
        )
    }

    // --- 3. 仅 exitCode == null 且特权就绪时才走特权通道 ---

    @Test
    fun adbUnavailableTriggersProbe() {
        assertTrue(LogcatChannelSelector.needsProbe(null, ExecutionMode.ROOT))
    }

    @Test
    fun adbUnavailableAndRootReadyFallsBackToPrivilege() {
        assertEquals(
            Decision.USE_PRIVILEGE,
            LogcatChannelSelector.decide(null, ExecutionMode.ROOT, rootReady),
        )
    }

    @Test
    fun adbUnavailableAndShizukuReadyFallsBackToPrivilege() {
        val shizukuReady = PrivilegeInfoSnapshot(
            modeActive = true,
            mode = ExecutionMode.SHIZUKU,
            shizukuAvailable = true,
            rootAvailable = false,
        )
        assertEquals(
            Decision.USE_PRIVILEGE,
            LogcatChannelSelector.decide(null, ExecutionMode.SHIZUKU, shizukuReady),
        )
    }

    @Test
    fun adbUnavailableButPrivilegeNotReadyStaysOnAdb() {
        val notReady = PrivilegeInfoSnapshot(
            modeActive = false,
            mode = ExecutionMode.ROOT,
            shizukuAvailable = false,
            rootAvailable = false,
        )
        assertEquals(
            Decision.USE_ADB,
            LogcatChannelSelector.decide(null, ExecutionMode.ROOT, notReady),
        )
    }

    @Test
    fun probeDoneButModeDegradedToProotStaysOnAdb() {
        // 探测把状态降级成 PRoot 后，即便 shizukuAvailable 也不能回退
        val degraded = PrivilegeInfoSnapshot(
            modeActive = true,
            mode = ExecutionMode.PROOT,
            shizukuAvailable = true,
            rootAvailable = false,
        )
        assertEquals(
            Decision.USE_ADB,
            LogcatChannelSelector.decide(null, ExecutionMode.ROOT, degraded),
        )
    }

    // --- 4. 特权路径自身未跑起来（exitCode == -1）时保留 ADB 结果 ---

    @Test
    fun privilegeOwnFailureIsRejected() {
        // -1 = 命令为空 / 超时 / 进程异常 / PRoot 拒绝
        assertFalse(LogcatChannelSelector.acceptPrivilegeResult(-1))
    }

    @Test
    fun privilegeRealExitCodeIsAccepted() {
        // 哪怕是非零（如 exit 3），也说明特权通道确实执行过，应当采用
        assertTrue(LogcatChannelSelector.acceptPrivilegeResult(3))
        assertTrue(LogcatChannelSelector.acceptPrivilegeResult(0))
    }

    // --- 5. 仅 success 时做关键词过滤 ---

    @Test
    fun keywordFilterSkipsFailedOutput() {
        val raw = "目标应用未运行：com.example.missing"
        // 失败输出带分类依据，过滤会把原因滤空 → 误分类为 internal
        assertEquals(
            raw,
            LogcatChannelSelector.shapePrivilegeOutput(false, raw, "Permission denied"),
        )
    }

    @Test
    fun keywordFilterDropsNonMatchingLinesOnSuccess() {
        val raw = "line-alpha\nline-beta\nline-gamma"
        assertEquals(
            "line-beta",
            LogcatChannelSelector.shapePrivilegeOutput(true, raw, "beta"),
        )
    }

    @Test
    fun emptyKeywordKeepsOutputUntouched() {
        val raw = "line-alpha\nline-beta"
        assertEquals(raw, LogcatChannelSelector.shapePrivilegeOutput(true, raw, ""))
    }
}
