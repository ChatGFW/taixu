package top.wkbin.taixu.runtime.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 开发者全权授权 —— 把持久的"开发者模式"偏好映射为沙箱凭据动作集。
 *
 * 装配层把 [top.wkbin.taixu.core.datastore.AppearancePreferences.developerMode] 交给本类，
 * 由 [SandboxCredentialStore] 在每次签发时读取 [actions]：
 * - 模式关闭：仅下发 [SandboxCredentialStore.SANDBOX_ACTIONS]，guest/agent 得不到 `host.shell`；
 * - 模式开启：升为 [SandboxCredentialStore.FULL_ACCESS_ACTIONS]，允许 guest/agent 请求宿主执行命令。
 *
 * 模式切换会让动作集变化，[SandboxCredentialStore] 在下次签发时自动重新签发并降权，后续会话立即失去 shell。
 * 首帧前默认按关闭处理（fail-safe），因此启动早期不会误授 shell。
 *
 * 定性：PRoot guest 与 App 同 uid 运行，本机制是应用层代理门控，不构成强隔离边界。
 */
class FullAccessGrant(
    developerMode: Flow<Boolean>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val fullAccess = MutableStateFlow(false)

    init {
        scope.launch { developerMode.collect { fullAccess.value = it } }
    }

    /** 当前应随沙箱凭据下发的动作集。 */
    fun actions(): Set<String> =
        if (fullAccess.value) SandboxCredentialStore.FULL_ACCESS_ACTIONS else SandboxCredentialStore.SANDBOX_ACTIONS
}
