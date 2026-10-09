package top.wkbin.taixu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 沙箱工具链版本判定逻辑单测。
 *
 * 覆盖三类最容易出错的点：
 *  1. 版本号抽取（各种工具输出形态）；
 *  2. 版本比较（段数不等、无法解析、判不了时必须返回 null）；
 *  3. 状态判定顺序（沙箱不可达 ≠ 工具缺失；版本读不到 ≠ 已就绪）。
 */
class ToolchainVersionTest {

    // ---------- 版本抽取 ----------

    @Test
    fun extractsVersionFromCmakeOutput() {
        assertEquals("3.22.1", ToolchainVersion.extractVersion("cmake version 3.22.1"))
    }

    @Test
    fun extractsVersionFromJadxOutput() {
        assertEquals("1.5.6", ToolchainVersion.extractVersion("jadx 1.5.6"))
    }

    @Test
    fun extractsVersionFromApktoolOutput() {
        assertEquals("2.9.3", ToolchainVersion.extractVersion("2.9.3"))
    }

    @Test
    fun extractsVersionFromBinutilsOutput() {
        // 关键：不能把 "GNU Binutils for aarch64" 里的杂散数字误当版本
        assertEquals("2.42", ToolchainVersion.extractVersion("readelf (GNU Binutils) 2.42"))
    }

    @Test
    fun extractsVersionFromVPrefixedOutput() {
        assertEquals("22.14.0", ToolchainVersion.extractVersion("v22.14.0"))
    }

    @Test
    fun returnsNullWhenNoVersionPresent() {
        assertNull(ToolchainVersion.extractVersion("command not found"))
        assertNull(ToolchainVersion.extractVersion(""))
        assertNull(ToolchainVersion.extractVersion(null))
    }

    // ---------- 版本比较 ----------

    @Test
    fun comparesVersionsSegmentBySegment() {
        assertEquals(1, ToolchainVersion.compare("4.3.4", "3.22"))
        assertEquals(-1, ToolchainVersion.compare("3.21", "3.22"))
        assertEquals(0, ToolchainVersion.compare("3.22", "3.22.0"))
    }

    @Test
    fun comparesDifferentMajorVersions() {
        assertTrue(ToolchainVersion.compare("10.0", "9.9")!! > 0)
        assertTrue(ToolchainVersion.compare("2.0", "10.0")!! < 0)
    }

    @Test
    fun returnsNullWhenVersionUnparsable() {
        // 判不了必须返回 null，交由上层降级为 UNKNOWN，绝不臆断
        assertNull(ToolchainVersion.compare("abc", "3.22"))
        assertNull(ToolchainVersion.compare(null, "3.22"))
        assertNull(ToolchainVersion.compare("3.22", null))
    }

    // ---------- 状态判定 ----------

    @Test
    fun reportsUnknownWhenSandboxUnreachableInsteadOfMissing() {
        val probe = SandboxToolchainCatalog.probes.first { it.id == "cmake" }

        val status = ToolchainVersion.resolveStatus(
            probe = probe,
            resolvedPath = null,
            version = null,
            reachable = false,
        )

        // 探测不可达 ≠ 工具缺失：绝不能误报 MISSING 让用户去装一堆其实装好的东西
        assertEquals(ToolchainStatus.UNKNOWN, status)
    }

    @Test
    fun reportsMissingWhenExecutableNotFound() {
        val probe = SandboxToolchainCatalog.probes.first { it.id == "cmake" }

        val status = ToolchainVersion.resolveStatus(probe, resolvedPath = null, version = null)

        assertEquals(ToolchainStatus.MISSING, status)
    }

    @Test
    fun reportsOutdatedWhenVersionBelowMinimum() {
        val probe = SandboxToolchainCatalog.probes.first { it.id == "cmake" }

        val status = ToolchainVersion.resolveStatus(probe, resolvedPath = "/usr/bin/cmake", version = "3.18.0")

        assertEquals(ToolchainStatus.OUTDATED, status)
    }

    @Test
    fun reportsReadyWhenVersionMeetsMinimum() {
        val probe = SandboxToolchainCatalog.probes.first { it.id == "cmake" }

        val status = ToolchainVersion.resolveStatus(probe, resolvedPath = "/usr/bin/cmake", version = "4.3.4")

        assertEquals(ToolchainStatus.READY, status)
    }

    @Test
    fun reportsUnknownWhenMinVersionRequiredButVersionUnreadable() {
        val probe = SandboxToolchainCatalog.probes.first { it.id == "cmake" }

        val status = ToolchainVersion.resolveStatus(probe, resolvedPath = "/usr/bin/cmake", version = null)

        // 找到工具但读不到版本，如实 UNKNOWN，不能武断判成 READY
        assertEquals(ToolchainStatus.UNKNOWN, status)
    }

    @Test
    fun reportsReadyWhenNoMinVersionRequired() {
        val probe = SandboxToolchainCatalog.probes.first { it.id == "readelf" }
        assertNull(probe.minVersion)

        val status = ToolchainVersion.resolveStatus(probe, resolvedPath = "/usr/bin/readelf", version = null)

        assertEquals(ToolchainStatus.READY, status)
    }

    // ---------- 中文结论 ----------

    @Test
    fun summaryMarksRootDependentToolsClearly() {
        val frida = SandboxToolchainCatalog.probes.first { it.id == "frida" }
        assertTrue(frida.requiresRootAtRuntime)

        val summary = ToolchainVersion.describe(frida, "/usr/bin/frida", "16.0.0", ToolchainStatus.READY)

        // 装好 ≠ 能用：必须明确告诉用户驱动需要 root
        assertTrue(summary.contains("root"))
    }

    @Test
    fun summaryForMissingToolDoesNotClaimInstalled() {
        val probe = SandboxToolchainCatalog.probes.first { it.id == "cmake" }

        val summary = ToolchainVersion.describe(probe, null, null, ToolchainStatus.MISSING)

        assertTrue(summary.contains("未安装"))
        assertFalse(summary.contains("已就绪"))
    }

    @Test
    fun summaryForOutdatedMentionsTargetVersion() {
        val probe = SandboxToolchainCatalog.probes.first { it.id == "cmake" }

        val summary = ToolchainVersion.describe(probe, "/usr/bin/cmake", "3.18.0", ToolchainStatus.OUTDATED)

        assertTrue(summary.contains("版本过低"))
        assertTrue(summary.contains("3.22"))
    }

    // ---------- 报告聚合 ----------

    @Test
    fun reportAggregatesCountsAndRepairables() {
        val probe = SandboxToolchainCatalog.probes.first()
        val results = listOf(
            ToolchainProbeResult(probe, "/usr/bin/cmake", "4.0.0", ToolchainStatus.READY, "已就绪"),
            ToolchainProbeResult(probe, null, null, ToolchainStatus.MISSING, "未安装"),
            ToolchainProbeResult(probe, "/usr/bin/cmake", "3.1.0", ToolchainStatus.OUTDATED, "版本过低"),
        )

        val report = ToolchainReport(results = results)

        assertEquals(1, report.readyCount)
        assertEquals(1, report.missingCount)
        assertEquals(1, report.outdatedCount)
        // 缺失与落后都要进补齐队列
        assertEquals(2, report.repairable.size)
        assertFalse(report.isAllReady)
    }

    @Test
    fun catalogCoversAllGroupsAndMarksRootTools() {
        val probes = SandboxToolchainCatalog.probes
        assertTrue(probes.isNotEmpty())
        // 三个分组都要有覆盖，避免面板出现空分区
        assertTrue(probes.any { it.group == ToolchainGroup.NATIVE_BUILD })
        assertTrue(probes.any { it.group == ToolchainGroup.REVERSE_ENGINEERING })
        assertTrue(probes.any { it.group == ToolchainGroup.DEBUG_INSPECT })
        // root 依赖工具必须显式标注
        assertTrue(probes.any { it.requiresRootAtRuntime })
        // 每个探针都要有用途说明，用户才看得懂为什么要装
        assertTrue(probes.all { it.purpose.isNotBlank() })
        // id 必须唯一，否则解析会串号
        assertEquals(probes.size, probes.map { it.id }.distinct().size)
        assertNotNull(SandboxToolchainCatalog.probes.firstOrNull { it.id == "cmake" })
    }
}
