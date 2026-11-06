package top.wkbin.taixu.runtime.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.runtime.privilege.PrivilegeInfo

class HostDoctorReportTest {

    private fun info(active: Boolean) = PrivilegeInfo(
        mode = ExecutionMode.SHIZUKU,
        modeActive = active,
        shizukuAvailable = active,
        rootAvailable = false,
    )

    @Test fun explainsWhyCapabilitiesAreUnavailable() {
        val report = hostDoctorReport(info(active = true), adbConnected = false, guiPresent = true, port = 7980)

        assertTrue(report.contains("127.0.0.1:7980"))
        assertTrue(report.contains("active"))
        assertTrue(report.contains("wireless-adb      : disconnected"))
        assertTrue(report.contains("gui-controller    : present"))
        assertTrue(report.contains("host.open"))
        assertTrue(report.contains("host.screen"))
        assertTrue(report.contains("full access only"))
    }

    @Test fun reportsInactivePrivilegeAndMissingGui() {
        val report = hostDoctorReport(info(active = false), adbConnected = false, guiPresent = false, port = 7980)

        assertTrue(report.contains("inactive"))
        assertTrue(report.contains("gui-controller    : absent"))
        assertFalse(report.contains("Bearer"))
    }
}
