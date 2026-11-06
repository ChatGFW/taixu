package top.wkbin.taixu.runtime.bridge

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
import top.wkbin.taixu.runtime.rootfs.RootfsValidator

/**
 * 安装端点的路径闸门测试。
 *
 * `/api/install-apk` 与插件网关共用 [HostBridgeApkInstall]；历史上
 * `resolveSandboxPath` 在未命中已知前缀时会把入参原样返回，等于允许调用方指定
 * 任意宿主机路径。这里锁定收紧后的行为：
 *
 * - 未知前缀、`../` 穿越、允许根之外——一律 403 + `permission_denied`，且在触达
 *   ADB / 安装器之前就返回；
 * - `/sdcard/...`、`/workspace/...`、`/attachments/...` 仍能映射进允许根——
 *   文件不存在时是 404，而不是被闸门拦下的 403。
 *
 * 断言只依赖状态码与 failure 字段，不触发真实安装：被接受的路径都指向不存在的文件，
 * 因此止步于「APK file not found」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostBridgeApkInstallPathTest {
    private val context: Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        // Robolectric leaves nativeLibraryDir unset, but RuntimePathManager derives its bundled tool paths from it.
        override fun getApplicationInfo(): ApplicationInfo = baseContext.applicationInfo.apply {
            nativeLibraryDir = File(baseContext.filesDir, "lib").apply { mkdirs() }.absolutePath
        }
    }
    private val logger = AppLogger(context, SensitiveDataRedactor { it })
    private val preferences = RuntimePreferences(SettingsDataStore(context, SecretManager()))
    private val paths = RuntimePathManager(context, RootfsValidator(ElfInspector()))
    private val apkInstall = HostBridgeApkInstall(
        context = context,
        logger = logger,
        embeddedAdbManager = EmbeddedAdbManager(context, preferences, paths),
        pathManager = paths,
    )

    private fun install(path: String) = runBlocking { apkInstall.installAt(path) }

    private fun assertRefusedOutsideAllowedRoots(path: String) {
        val response = install(path)
        assertEquals("'$path' should be refused before any install attempt", 403, response.status)
        assertTrue(
            "'$path' should report permission_denied, body=${response.body}",
            response.body.contains("\"failure\":\"permission_denied\""),
        )
    }

    private fun assertAcceptedByPathGate(path: String) {
        // 被闸门放行的路径会继续走到「文件不存在」，因此是 404 而不是 403。
        val response = install(path)
        assertEquals("'$path' should pass the path gate, body=${response.body}", 404, response.status)
        assertTrue(
            "'$path' should not be reported as denied, body=${response.body}",
            !response.body.contains("\"failure\":\"permission_denied\""),
        )
    }

    @Test fun unknownAbsolutePrefixIsRefusedInsteadOfPassedThrough() {
        // 旧的 else 分支会原样返回这些宿主绝对路径。
        assertRefusedOutsideAllowedRoots("/data/local/tmp/payload.apk")
        assertRefusedOutsideAllowedRoots("/system/app/Anything.apk")
        assertRefusedOutsideAllowedRoots("/etc/path-gate-probe.apk")
    }

    @Test fun parentTraversalCannotEscapeTheWorkspace() {
        assertRefusedOutsideAllowedRoots("/workspace/../../../../etc/passwd.apk")
        assertRefusedOutsideAllowedRoots("/workspace/../path-gate-outside.apk")
        assertRefusedOutsideAllowedRoots("/sdcard/../../data/local/tmp/x.apk")
        assertRefusedOutsideAllowedRoots("/attachments/../../../data/x.apk")
    }

    @Test fun blankInputIsRefused() {
        assertRefusedOutsideAllowedRoots("")
        assertRefusedOutsideAllowedRoots("   ")
    }

    @Test fun knownSandboxPrefixesStillMapIntoAllowedRoots() {
        // 提示词约定用法：先复制到 /sdcard/Download/ 再安装。
        assertAcceptedByPathGate("/sdcard/Download/taixu-path-gate-demo.apk")
        // 内置工作流按工作区构建产物传入。
        assertAcceptedByPathGate("/workspace/build/outputs/apk/debug/path-gate-probe.apk")
        // 会话附件目录。
        assertAcceptedByPathGate("/attachments/sessions/s1/path-gate-probe.apk")
    }

    @Test fun bareKnownRootsAreTreatedAsDirectoriesNotApks() {
        // 根目录本身能通过闸门，但因不是 .apk 文件而在后续步骤中止，绝不是 403。
        listOf("/workspace", "/attachments").forEach { assertAcceptedByPathGate(it) }
    }
}
