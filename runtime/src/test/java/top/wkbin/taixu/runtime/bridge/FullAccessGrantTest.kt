package top.wkbin.taixu.runtime.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** 开发者全权授权把持久开发者模式流映射为沙箱凭据动作集：关闭不含 shell，开启才升权。 */
class FullAccessGrantTest {
    @Test fun actionsTrackDeveloperModeAndNeverGrantShellWhileOff() {
        val mode = MutableStateFlow(false)
        val grant = FullAccessGrant(mode, CoroutineScope(Dispatchers.Unconfined))
        assertEquals(SandboxCredentialStore.SANDBOX_ACTIONS, grant.actions())

        mode.value = true
        assertEquals(SandboxCredentialStore.FULL_ACCESS_ACTIONS, grant.actions())

        mode.value = false
        assertEquals(SandboxCredentialStore.SANDBOX_ACTIONS, grant.actions())
    }
}
