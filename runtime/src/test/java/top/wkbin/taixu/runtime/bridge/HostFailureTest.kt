package top.wkbin.taixu.runtime.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostFailureTest {

    @Test fun httpStatusKeepsTheDecisionBoundariesAgentsNeed() {
        assertNull(HostFailure.ofHttpStatus(200))
        assertEquals(HostFailure.USAGE, HostFailure.ofHttpStatus(400))
        assertEquals(HostFailure.PERMISSION_DENIED, HostFailure.ofHttpStatus(401))
        assertEquals(HostFailure.PERMISSION_DENIED, HostFailure.ofHttpStatus(403))
        assertEquals(HostFailure.NOT_FOUND, HostFailure.ofHttpStatus(404))
        assertEquals(HostFailure.TIMEOUT, HostFailure.ofHttpStatus(504))
        assertEquals(HostFailure.INTERNAL, HostFailure.ofHttpStatus(500))
    }

    @Test fun shellExitDistinguishesParamChannelAndTimeoutFailures() {
        assertNull(HostFailure.ofShellExit(0, ""))
        assertEquals(HostFailure.PERMISSION_DENIED, HostFailure.ofShellExit(126, ""))
        assertEquals(HostFailure.PERMISSION_DENIED, HostFailure.ofShellExit(1, "sh: Permission denied"))
        assertEquals(HostFailure.NOT_FOUND, HostFailure.ofShellExit(127, ""))
        assertEquals(HostFailure.TIMEOUT, HostFailure.ofShellExit(124, ""))
        assertEquals(HostFailure.UNAVAILABLE, HostFailure.ofShellExit(1, "error: device offline"))
        assertEquals(HostFailure.INTERNAL, HostFailure.ofShellExit(1, "boom"))
    }

    @Test fun everyFailureCarriesANonZeroExitAndAHint() {
        HostFailure.entries.forEach {
            assertTrue(it.wire.isNotBlank())
            assertTrue(it.exitCode != 0)
            assertTrue(it.hint.isNotBlank())
        }
    }

    @Test fun cliScriptResolvesPlaceholdersAndExportsTheSameTaxonomy() {
        val script = HostBridgeCliScript.TAIXU_HOST_SCRIPT
        assertTrue(script.startsWith("#!/bin/sh"))
        assertFalse("script must not leak Kotlin placeholder", script.contains("@@"))
        assertTrue(script.contains("permission_denied) echo 126"))
        assertTrue(script.contains("unavailable) echo 69"))
        assertTrue(script.contains("not_found) echo 66"))
        assertTrue(script.contains("timeout) echo 124"))
        assertTrue(script.contains("  open)"))
        assertTrue(script.contains("  clipboard)"))
    }

    @Test fun capabilityShimsForwardToTheSameCli() {
        val shims = HostBridgeCliScript.SHIMS
        assertEquals(setOf("taixu-open", "taixu-clipboard"), shims.keys)
        shims.forEach { (_, body) ->
            assertTrue(body.startsWith("#!/bin/sh"))
            assertTrue(body.contains("/opt/taixu/bin/taixu-host"))
            assertFalse(body.contains("@@"))
        }
    }
}
