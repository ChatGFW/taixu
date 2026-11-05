package top.wkbin.taixu.runtime.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostCapabilityActionsTest {

    @Test fun allowsUserVisibleSchemes() {
        assertEquals("https://example.com/a?b=1", HostCapabilityActions.parseAllowedUri(" https://example.com/a?b=1 "))
        assertEquals("http://example.com", HostCapabilityActions.parseAllowedUri("http://example.com"))
        assertEquals("mailto:a@b.com", HostCapabilityActions.parseAllowedUri("mailto:a@b.com"))
        assertEquals("tel:10086", HostCapabilityActions.parseAllowedUri("tel:10086"))
    }

    @Test fun rejectsSchemesThatCanTriggerPrivilegedSurfaces() {
        assertNull(HostCapabilityActions.parseAllowedUri("javascript:alert(1)"))
        assertNull(HostCapabilityActions.parseAllowedUri("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(HostCapabilityActions.parseAllowedUri("file:///etc/passwd"))
        assertNull(HostCapabilityActions.parseAllowedUri("content://com.example/secret"))
        assertNull(HostCapabilityActions.parseAllowedUri("market://details?id=x"))
    }

    @Test fun rejectsMalformedOversizedAndControlCharacterInput() {
        assertNull(HostCapabilityActions.parseAllowedUri(""))
        assertNull(HostCapabilityActions.parseAllowedUri("   "))
        assertNull(HostCapabilityActions.parseAllowedUri("https://example.com/" + "a".repeat(3_000)))
        assertNull(HostCapabilityActions.parseAllowedUri("https://example.com/\nrm -rf /"))
        assertNull(HostCapabilityActions.parseAllowedUri("https://a b"))
    }

    @Test fun schemelessInputIsNotTreatedAsAWebLink() {
        assertNull(HostCapabilityActions.parseAllowedUri("example.com"))
    }

    @Test fun notificationRequiresATitleAndBoundedBody() {
        assertEquals(
            "构建完成" to "APK 已产出",
            HostCapabilityActions.sanitizeNotification(" 构建完成 ", " APK 已产出 "),
        )
        assertEquals("只有标题" to "", HostCapabilityActions.sanitizeNotification("只有标题", ""))
        assertNull(HostCapabilityActions.sanitizeNotification("   ", "body"))
        assertNull(HostCapabilityActions.sanitizeNotification("t".repeat(81), "body"))
        assertNull(HostCapabilityActions.sanitizeNotification("标题", "b".repeat(501)))
        assertNull(HostCapabilityActions.sanitizeNotification("标\u0000题", "body"))
    }
}
