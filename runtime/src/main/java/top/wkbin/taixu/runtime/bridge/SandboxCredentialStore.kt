package top.wkbin.taixu.runtime.bridge

/**
 * 沙箱宿主凭据存储器 —— 原生代码路径上唯一的沙箱凭据注入点。
 *
 * 每个已就绪的发行版签发一枚"宿主签发、可撤销"的可复用凭据，并按发行版缓存复用：
 * - 凭据通过 PRoot 命令环境注入 guest（[BRIDGE_TOKEN_ENV] / [BRIDGE_ACTIONS_ENV]）；
 * - guest 内 `taixu-host` / `logcat-grabber` 只能使用该凭据绑定的动作集；
 * - HTTP 面不提供签发或续期端点，Web 内容与磁盘上都不存在任何共享密钥文件。
 *
 * 默认动作集 [SANDBOX_ACTIONS] 不含任意宿主 shell；只有开发者全权模式开启时由装配层注入的
 * [grantedActions] 才升为 [FULL_ACCESS_ACTIONS]。动作集变化会触发重新签发，故模式关闭后后续会话
 * 立即降权。
 *
 * 定性：PRoot guest 与 App 同 uid 运行，本机制是应用层代理门控，不构成强隔离边界。
 */
class SandboxCredentialStore(
    private val authority: HostCredentialAuthority,
    private val grantedActions: () -> Set<String> = { SANDBOX_ACTIONS },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Entry(val token: String, val actions: Set<String>, val issuedAt: Long)

    private val entries = mutableMapOf<String, Entry>()
    private val monitor = Any()
    private var observedEpoch = authority.epoch()

    /** guest 环境片段；凭据缺失、动作集变化、临期或代次切换时重新签发。 */
    fun environment(distroId: String): Map<String, String> = synchronized(monitor) {
        val actions = grantedActions().ifEmpty { SANDBOX_ACTIONS }
        val epoch = authority.epoch()
        if (epoch != observedEpoch) {
            entries.clear()
            observedEpoch = epoch
        }
        val entry = entries[distroId]
        val reusable = entry != null && entry.actions == actions && now() - entry.issuedAt < TTL_MILLIS / 2
        val current = if (reusable) {
            entry!!
        } else {
            entry?.let { authority.revoke(it.token) }
            Entry(authority.issue(binding(distroId, actions), TTL_MILLIS, reusable = true) { true }, actions, now())
                .also { entries[distroId] = it }
        }
        mapOf(
            BRIDGE_TOKEN_ENV to current.token,
            BRIDGE_ACTIONS_ENV to current.actions.sorted().joinToString(","),
        )
    }

    /** 发行版销毁（卸载/重置/重建）时回收该发行版凭据。 */
    fun revoke(distroId: String) {
        synchronized(monitor) { entries.remove(distroId)?.let { authority.revoke(it.token) } }
        authority.revokeAllForSubject(distroId)
    }

    fun revokeAll() {
        synchronized(monitor) {
            entries.values.forEach { authority.revoke(it.token) }
            entries.clear()
            observedEpoch = authority.epoch()
        }
    }

    private fun binding(distroId: String, actions: Set<String>) = HostCredentialAuthority.Binding(
        subject = distroId,
        version = SANDBOX_VERSION,
        generation = 0,
        origin = SANDBOX_ORIGIN,
        environment = SANDBOX_ENVIRONMENT,
        actions = actions,
        target = SANDBOX_TARGET,
    )

    companion object {
        const val BRIDGE_TOKEN_ENV = "TAIXU_BRIDGE_TOKEN"
        const val BRIDGE_ACTIONS_ENV = "TAIXU_BRIDGE_ACTIONS"

        const val SHELL_ACTION = "host.shell"

        /** 默认沙箱子集：APK 安装与 logcat 抓取；任意宿主 shell 不在其中。 */
        val SANDBOX_ACTIONS: Set<String> = setOf("host.install-apk", "host.logcat")

        /** 开发者全权模式下的动作集：默认沙箱子集加上宿主 shell。 */
        val FULL_ACCESS_ACTIONS: Set<String> = SANDBOX_ACTIONS + SHELL_ACTION

        private const val TTL_MILLIS = 8L * 60 * 60 * 1000
        private const val SANDBOX_VERSION = "sandbox"
        private const val SANDBOX_ORIGIN = "taixu:proot"
        private const val SANDBOX_ENVIRONMENT = "android:local"
        private const val SANDBOX_TARGET = "device"
    }
}
