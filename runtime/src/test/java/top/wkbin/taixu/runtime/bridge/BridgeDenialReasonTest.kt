package top.wkbin.taixu.runtime.bridge

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import top.wkbin.taixu.core.datastore.RuntimePreferences
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.security.SecretManager
import top.wkbin.taixu.runtime.ElfInspector
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.bridge.adb.EmbeddedAdbManager
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import top.wkbin.taixu.runtime.privilege.ShizukuHostServiceClient
import top.wkbin.taixu.runtime.rootfs.RootfsValidator

/**
 * 凭据拒绝原因的区分语义。
 *
 * 背景：`authorize()` 曾把「凭据无效」和「凭据有效但未授权该动作」合并成同一个 false，
 * 端点统一回 `Unauthorized: missing or invalid bridge credential`。默认沙箱不签发
 * `host.shell`，于是用户调用 `/api/shell` 会看到「凭据缺失或无效」，被引向排查 token，
 * 而不是去开启全权模式。
 *
 * 这里锁定两条不变量：
 * 1. 凭据有效、仅缺动作授权时，文案点名该动作，指明是可执行的动作而非失效的凭据；
 * 2. 凭据无效/已撤销时仍回原来的模糊文案，不放大信息（避免把「凭据有效」告诉未授权方）。
 *
 * 两种情况都必须是 401 + `failure: permission_denied`，状态码与分类保持不变。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BridgeDenialReasonTest {
    private val context: Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        // Robolectric leaves nativeLibraryDir unset, but RuntimePathManager derives its bundled tool paths from it.
        override fun getApplicationInfo(): ApplicationInfo = baseContext.applicationInfo.apply {
            nativeLibraryDir = File(baseContext.filesDir, "lib").apply { mkdirs() }.absolutePath
        }
    }
    private val logger = AppLogger(context, SensitiveDataRedactor { it })
    private val preferences = RuntimePreferences(SettingsDataStore(context, SecretManager()))
    private val paths = RuntimePathManager(context, RootfsValidator(ElfInspector()))
    private val authority = HostCredentialAuthority()
    private val bridge = HostBridge(
        context = context,
        logger = logger,
        privilegeManager = PrivilegeManager(context, preferences, logger, ShizukuHostServiceClient(context)),
        embeddedAdbManager = EmbeddedAdbManager(context, preferences, paths),
        pathManager = paths,
        credentialAuthority = authority,
    )

    private fun request(
        path: String,
        token: String? = null,
        method: String = "POST",
        body: String = "{}",
    ) = HostBridgeHttp.HttpRequest(method, path, token?.let { mapOf("authorization" to it) } ?: emptyMap(), body)

    private fun sandboxToken(actions: Set<String> = SandboxCredentialStore.SANDBOX_ACTIONS): String =
        SandboxCredentialStore(authority, grantedActions = { actions }).environment("ubuntu")
            .getValue(SandboxCredentialStore.BRIDGE_TOKEN_ENV)

    // ---------------------------------------------------------------- 权威层

    @Test fun denialReasonSeparatesMissingFromUngranted() {
        val token = sandboxToken()

        // 凭据有效且动作已签发：通过。
        assertNull(authority.denialReason(token, "host.install-apk"))

        // 凭据有效但动作未签发：能区分出来。
        assertEquals(
            HostCredentialAuthority.Denial.ActionNotGranted,
            authority.denialReason(token, "host.shell"),
        )

        // 拿不到票据：一律 Unknown，不透露是缺失、已消费还是已撤销。
        assertEquals(
            HostCredentialAuthority.Denial.Unknown,
            authority.denialReason("not-a-real-token", "host.install-apk"),
        )
        assertEquals(
            HostCredentialAuthority.Denial.Unknown,
            authority.denialReason(null, "host.install-apk"),
        )
    }

    @Test fun revokedCredentialIsUnknownNotActionNotGranted() {
        val token = sandboxToken()
        authority.revokeAll()
        // 撤销后不得再区分出动作层原因，否则等于告诉对方「凭据曾经存在且有效」。
        assertEquals(
            HostCredentialAuthority.Denial.Unknown,
            authority.denialReason(token, "host.install-apk"),
        )
    }

    @Test fun authorizeStillAgreesWithDenialReason() {
        val token = sandboxToken()
        // authorize 委托给 denialReason，两者必须对同一输入给出一致判定。
        listOf("host.install-apk", "host.shell", "host.settings").forEach { action ->
            assertEquals(
                "authorize and denialReason disagree on $action",
                authority.denialReason(token, action) == null,
                authority.authorize(token, action),
            )
        }
    }

    // ---------------------------------------------------------------- 端到端文案

    @Test fun ungrantedActionNamesTheActionAndPointsAtSettings() = runBlocking {
        val token = sandboxToken()
        val response = bridge.route(request("/api/shell", "Bearer $token"))

        assertEquals(401, response.status)
        assertTrue(response.body.contains("\"failure\":\"permission_denied\""))
        // 关键：不再声称凭据无效，而是点名 host.shell。
        assertFalse(
            "ungranted action must not be reported as an invalid credential: ${response.body}",
            response.body.contains("missing or invalid bridge credential"),
        )
        assertTrue(
            "message should name the refused action: ${response.body}",
            response.body.contains("host.shell"),
        )
        assertTrue(
            "message should point at the actionable fix: ${response.body}",
            response.body.contains("full access"),
        )
    }

    @Test fun invalidCredentialKeepsTheOpaqueMessage() = runBlocking {
        listOf("Bearer not-a-real-token", "Bearer expired-legacy-key").forEach { header ->
            val response = bridge.route(request("/api/install-apk", header, body = "{"))
            assertEquals(401, response.status)
            assertTrue(
                "invalid credential must stay opaque: ${response.body}",
                response.body.contains("missing or invalid bridge credential"),
            )
            // 不得反向泄露「凭据有效」或点名动作。
            assertFalse(response.body.contains("full access"))
            assertFalse(response.body.contains("host.install-apk"))
        }
    }

    @Test fun missingCredentialStaysOpaqueToo() = runBlocking {
        val response = bridge.route(request("/api/shell"))
        assertEquals(401, response.status)
        assertTrue(response.body.contains("missing or invalid bridge credential"))
        assertFalse(response.body.contains("full access"))
    }

    @Test fun grantedActionIsUnaffectedByTheNewMessage() = runBlocking {
        val token = sandboxToken()
        // 已签发的动作照常放行到端点内部：畸形正文止于 400，不进入 401 分支。
        val response = bridge.route(request("/api/install-apk", "Bearer $token", body = "{"))
        assertEquals(400, response.status)
        assertTrue(response.body.contains("\"failure\":\"usage\""))
        assertNotNull(response.body)
    }
}
