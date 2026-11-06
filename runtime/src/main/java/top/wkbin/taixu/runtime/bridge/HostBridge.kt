package top.wkbin.taixu.runtime.bridge

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.runtime.AttachmentMigrator
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.gui.HostGuiController
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import top.wkbin.taixu.runtime.bridge.HostBridgeHttp.HttpRequest
import top.wkbin.taixu.runtime.bridge.HostBridgeHttp.HttpResponse
import top.wkbin.taixu.runtime.bridge.HostBridgeHttp.parseHttpRequest
import top.wkbin.taixu.runtime.bridge.HostBridgeHttp.writeResponse
import top.wkbin.taixu.runtime.bridge.adb.EmbeddedAdbManager

/**
 * 宿主桥接 (Host Bridge) — 沙箱与 Android 宿主之间的 localhost HTTP 通道。
 *
 * 沙箱（PRoot）与 App 共享网络命名空间，因此沙箱内可通过 `127.0.0.1:7980` 访问本服务。
 *
 * 提供以下端点：
 * - `GET  /api/health`       — 桥接健康检查 + 当前特权状态与 ADB 状态
 * - `POST /api/install-apk`  — 在宿主侧安装 APK（无线 ADB 静默安装或系统安装器）
 * - `POST /api/shell`        — 在宿主侧执行 Shell 命令（Shizuku/Root 或内置无线 ADB）
 * - `POST /api/logcat`       — 抓取目标应用或系统 Logcat 日志
 *
 * 安全：仅监听 127.0.0.1；每个写操作都要绑定一张由宿主签发、可撤销的凭据（[HostCredentialAuthority]），
 * 并校验端点所需的结构化动作。凭据只经原生代码路径注入 guest 环境，不存在共享密钥文件或签发端点。
 */
class HostBridge(
    private val context: Context,
    private val logger: AppLogger,
    private val privilegeManager: PrivilegeManager,
    private val embeddedAdbManager: EmbeddedAdbManager,
    private val pathManager: RuntimePathManager,
    private val credentialAuthority: HostCredentialAuthority,
    private val guiController: HostGuiController? = null,
) {
    companion object {
        const val BRIDGE_PORT = 7980
        const val BRIDGE_HOST = "127.0.0.1"
        const val BRIDGE_URL = "http://127.0.0.1:7980"
        private const val READ_TIMEOUT_MS = 10_000

        /** The plugin-facing logcat request is fixed here, so a page can never widen it. */
        private const val PLUGIN_LOGCAT_BODY = """{"lines":200}"""
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val apkInstall = HostBridgeApkInstall(context, logger, embeddedAdbManager, pathManager)

    private val capabilities = HostCapabilityActions(context, privilegeManager, guiController, pathManager.workspaceDir)

    private val controlEndpoints = HostControlEndpoints(json, capabilities)

    private val pluginGateway = PluginHostGateway(
        appInfo = {
            buildJsonObject {
                put("package", context.packageName)
                put("androidApi", android.os.Build.VERSION.SDK_INT)
            }.toString()
        },
        clipboardWrite = { PluginHostClipboard.write(context, it) },
        installApkFile = { path ->
            // The page named a sandbox path; this host resolves it and reuses the guest channels.
            val response = apkInstall.installAt(path)
            check(response.status == 200) { "APK install refused" }
            response.body
        },
        captureLogcat = {
            // A page never chooses tag, priority or line count: one fixed bounded snapshot.
            val response = handleLogcat(PLUGIN_LOGCAT_BODY)
            check(response.status == 200) { "Log capture refused" }
            response.body
        },
        authority = credentialAuthority,
    )

    /** Native-only call after document approval; the credential never leaves this host adapter. */
    fun readPluginAppInfo(scope: PluginHostScope, available: () -> Boolean): String =
        pluginGateway.readAppInfo(scope, available)

    fun writePluginClipboard(scope: PluginHostScope, text: String, available: () -> Boolean): String =
        pluginGateway.writeClipboard(scope, text, available)

    /** Privileged plugin actions reuse the same one-shot authority; only native callers reach them. */
    suspend fun installPluginApk(scope: PluginHostScope, path: String, available: () -> Boolean): String =
        pluginGateway.installApk(scope, path, available)

    suspend fun readPluginLogcat(scope: PluginHostScope, available: () -> Boolean): String =
        pluginGateway.readLogcat(scope, available)

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val bridgeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 启动 HTTP 监听。重复调用安全，且绑定操作不会被并发启动打断。 */
    @Synchronized
    fun start() {
        if (_isRunning.value) return
        try {
            val socket = ServerSocket(BRIDGE_PORT, 16, InetAddress.getByName(BRIDGE_HOST))
            serverSocket = socket
            _isRunning.value = true
            serverJob = bridgeScope.launch {
                logger.i("HostBridge listening on $BRIDGE_HOST:$BRIDGE_PORT")
                while (isActive) {
                    try {
                        val client = socket.accept()
                        launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (isActive) logger.w("HostBridge accept error", e)
                    }
                }
            }
        } catch (e: Exception) {
            serverSocket?.runCatching { close() }
            serverSocket = null
            _isRunning.value = false
            logger.e("Failed to start HostBridge on port $BRIDGE_PORT", e)
        }
    }

    /** 停止 HTTP 监听，并撤销所有插件与沙箱凭据（代次推进，缓存层随后自愈）。 */
    @Synchronized
    fun stop() {
        credentialAuthority.revokeAll()
        serverJob?.cancel()
        serverSocket?.runCatching { close() }
        serverSocket = null
        _isRunning.value = false
        logger.i("HostBridge stopped")
    }

    // ============================ 请求处理 ============================

    private suspend fun handleClient(client: Socket) {
        try {
            client.soTimeout = READ_TIMEOUT_MS
            val request = parseHttpRequest(client.getInputStream()) ?: run {
                writeResponse(client, 400, errorJson("Failed to parse HTTP request", HostFailure.USAGE))
                return
            }
            val response = route(request)
            writeResponse(client, response.status, response.body)
        } catch (e: Exception) {
            runCatching { writeResponse(client, 500, errorJson("Internal error: ${e.message}", HostFailure.INTERNAL)) }
        } finally {
            runCatching { client.close() }
        }
    }

    /** Visible to in-module tests so the converged credential path can be asserted without a socket. */
    internal suspend fun route(request: HttpRequest): HttpResponse {
        pluginGateway.route(request)?.let { return HttpResponse(it.status, it.body) }
        // health 端点免认证（只暴露特权/ADB 状态，无凭证）
        if (request.method == "GET" && request.path.startsWith("/api/health")) return handleHealth()
        if (request.method == "GET" && request.path.startsWith("/api/doctor")) return handleDoctor()

        // 每个写端点绑定一个结构化动作；未知路径也先过"有效凭据"这一关再 404，避免未认证探测面。
        val action = when {
            request.method == "POST" && request.path.startsWith("/api/install-apk") -> "host.install-apk"
            request.method == "POST" && request.path.startsWith("/api/shell") -> "host.shell"
            request.method == "POST" && request.path.startsWith("/api/logcat") -> "host.logcat"
            request.method == "POST" && request.path.startsWith("/api/open") -> "host.open"
            request.method == "POST" && request.path.startsWith("/api/clipboard") -> "host.clipboard"
            request.method == "POST" && request.path.startsWith("/api/notify") -> "host.notify"
            request.method == "POST" && request.path.startsWith("/api/settings") -> "host.settings"
            request.method == "POST" && request.path.startsWith("/api/app") -> "host.app"
            request.method == "POST" && request.path.startsWith("/api/screen") -> "host.screen"
            else -> null
        }
        if (!credentialAuthority.authorize(bearerToken(request.headers), action)) {
            return HttpResponse(
                401,
                errorJson("Unauthorized: missing or invalid bridge credential", HostFailure.PERMISSION_DENIED),
            )
        }
        return when (action) {
            "host.install-apk" -> handleInstallApk(request.body)
            "host.shell" -> handleShell(request.body)
            "host.logcat" -> handleLogcat(request.body)
            "host.open" -> handleOpen(request.body)
            "host.clipboard" -> handleClipboard(request.body)
            "host.notify" -> handleNotify(request.body)
            "host.settings", "host.app", "host.screen" -> controlEndpoints.handle(action, request.body)
            else -> HttpResponse(404, errorJson("Not found: ${request.method} ${request.path}", HostFailure.NOT_FOUND))
        }
    }

    private fun bearerToken(headers: Map<String, String>): String? =
        headers["authorization"]?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()?.ifEmpty { null }

    // ============================ 端点实现 ============================

    private suspend fun handleHealth(): HttpResponse {
        val info = privilegeManager.getPrivilegeInfo()
        val adbState = embeddedAdbManager.state.value
        val adbConnected = adbState is EmbeddedAdbManager.ConnectionState.Connected
        val body = buildJsonObject {
            put("status", "ok")
            put("bridge", "1.0")
            put("port", BRIDGE_PORT)
            put("mode", info.mode.id)
            put("modeLabel", info.mode.shortLabel)
            put("modeActive", info.modeActive || adbConnected)
            put("shizuku", info.shizukuAvailable)
            put("root", info.rootAvailable)
            put("adb", adbConnected)
            put("adbPort", (adbState as? EmbeddedAdbManager.ConnectionState.Connected)?.port ?: 0)
            put("shellSupported", info.shizukuAvailable || info.rootAvailable || adbConnected)
        }.toString()
        return HttpResponse(200, body)
    }

    private suspend fun handleDoctor(): HttpResponse {
        val info = privilegeManager.getPrivilegeInfo()
        val adb = embeddedAdbManager.state.value is EmbeddedAdbManager.ConnectionState.Connected
        val attachments = AttachmentMigrator.inspect(context.filesDir)
        return HttpResponse(200, hostDoctorReport(info, adb, guiController != null, BRIDGE_PORT, attachments))
    }

    private suspend fun handleInstallApk(body: String): HttpResponse {
        val apkPath = try {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["path"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            return HttpResponse(400, errorJson("Invalid JSON body: ${e.message}", HostFailure.USAGE))
        }

        if (apkPath.isNullOrBlank()) {
            return HttpResponse(400, errorJson("Missing 'path' field", HostFailure.USAGE))
        }
        return apkInstall.installAt(apkPath)
    }

    private suspend fun handleShell(body: String): HttpResponse {
        val obj = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return HttpResponse(400, errorJson("Invalid JSON body: ${e.message}", HostFailure.USAGE))
        }

        val command = obj["command"]?.jsonPrimitive?.content
        if (command.isNullOrBlank()) {
            return HttpResponse(400, errorJson("Missing 'command' field", HostFailure.USAGE))
        }

        val info = privilegeManager.getPrivilegeInfo()
        val responseJson = if (info.modeActive && (info.shizukuAvailable || info.rootAvailable)) {
            val result = privilegeManager.executeShellCommand(command)
            hostShellJson(result.success, result.exitCode, result.stdout, result.stderr, "privilege")
        } else {
            // 端口固定由内置无线 ADB 管理器持有；沙箱不得传入任意端口作为回退通道。
            val adbResult = embeddedAdbManager.executeShell(command, null)
            hostShellJson(
                success = adbResult.success,
                exitCode = adbResult.exitCode ?: if (adbResult.success) 0 else 1,
                stdout = if (adbResult.success) adbResult.output else "",
                stderr = if (adbResult.success) "" else adbResult.output,
                channel = "wireless-adb",
            )
        }
        return HttpResponse(200, responseJson)
    }

    private suspend fun handleLogcat(body: String): HttpResponse {
        val obj = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return HttpResponse(400, errorJson("Invalid JSON body: ${e.message}", HostFailure.USAGE))
        }

        val clear = obj["clear"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        if (clear) {
            val clearResult = embeddedAdbManager.clearLogcat()
            return HttpResponse(200, buildJsonObject {
                put("success", clearResult.success)
                put("message", if (clearResult.success) "Logcat buffer cleared" else clearResult.output)
            }.toString())
        }

        val pkg = obj["package"]?.jsonPrimitive?.content?.trim().orEmpty()
        val tag = obj["tag"]?.jsonPrimitive?.content?.trim().orEmpty()
        val priority = obj["priority"]?.jsonPrimitive?.content?.trim()?.uppercase()?.firstOrNull() ?: 'V'
        val keyword = obj["keyword"]?.jsonPrimitive?.content?.trim().orEmpty()
        val lines = obj["lines"]?.jsonPrimitive?.content?.toIntOrNull() ?: 200

        val result = embeddedAdbManager.captureLogcat(
            EmbeddedAdbManager.LogcatRequest(
                packageName = pkg,
                tag = tag,
                priority = priority,
                keyword = keyword,
                lines = lines,
            ),
            explicitPort = null,
        )

        val exitCode = result.exitCode ?: if (result.success) 0 else 1
        return HttpResponse(200, buildJsonObject {
            put("success", result.success)
            put("exitCode", exitCode)
            if (!result.success) {
                HostFailure.ofShellExit(exitCode, result.output)?.let { put("failure", it.wire) }
            }
            put("output", result.output)
        }.toString())
    }

    private fun handleOpen(body: String): HttpResponse {
        val uri = try {
            json.parseToJsonElement(body).jsonObject["uri"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            return HttpResponse(400, errorJson("Invalid JSON body: ${e.message}", HostFailure.USAGE))
        }
        if (uri.isNullOrBlank()) return HttpResponse(400, errorJson("Missing 'uri' field", HostFailure.USAGE))
        return capabilityResponse(capabilities.open(uri))
    }

    private fun handleClipboard(body: String): HttpResponse {
        val text = try {
            json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            return HttpResponse(400, errorJson("Invalid JSON body: ${e.message}", HostFailure.USAGE))
        }
        if (text.isNullOrEmpty()) return HttpResponse(400, errorJson("Missing 'text' field", HostFailure.USAGE))
        return capabilityResponse(capabilities.writeClipboard(text))
    }

    private fun handleNotify(body: String): HttpResponse {
        val (title, text) = try {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["title"]?.jsonPrimitive?.content to (obj["body"]?.jsonPrimitive?.content.orEmpty())
        } catch (e: Exception) {
            return HttpResponse(400, errorJson("Invalid JSON body: ${e.message}", HostFailure.USAGE))
        }
        if (title.isNullOrBlank()) return HttpResponse(400, errorJson("Missing 'title' field", HostFailure.USAGE))
        return capabilityResponse(capabilities.notify(title, text))
    }

    /** 能力动作失败仍走 200 + success=false，与 shell 端点一致；失败类别由 failure 字段表达。 */
    private fun capabilityResponse(outcome: HostCapabilityActions.Outcome): HttpResponse = when (outcome) {
        is HostCapabilityActions.Outcome.Ok -> HttpResponse(200, HostJson.ok(outcome.output))
        is HostCapabilityActions.Outcome.Rejected -> HttpResponse(200, errorJson(outcome.message, outcome.failure))
    }

    // ============================ 工具方法 ============================

    /** shell 通道的统一回包：失败时把退出码翻译成 agent 可据以决策的失败分类。 */
    private fun hostShellJson(
        success: Boolean,
        exitCode: Int,
        stdout: String,
        stderr: String,
        channel: String,
    ): String = buildJsonObject {
        put("success", success)
        put("exitCode", exitCode)
        if (!success) HostFailure.ofShellExit(exitCode, stderr)?.let { put("failure", it.wire) }
        put("stdout", stdout)
        put("stderr", stderr)
        put("channel", channel)
    }.toString()

    private fun errorJson(message: String, failure: HostFailure? = null): String = HostJson.error(message, failure)
}
