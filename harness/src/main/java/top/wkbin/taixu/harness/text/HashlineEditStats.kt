package top.wkbin.taixu.harness.text

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hashline / legacy edit 结果计数。
 * 内存热路径累加；经 [bind] 挂载后可跨进程持久化（由调用方负责落盘）。
 */
object HashlineEditStats {
    private val opsSuccess = AtomicInteger()
    private val opsStale = AtomicInteger()
    private val opsOtherFailure = AtomicInteger()
    private val opsDisabled = AtomicInteger()
    private val legacySuccess = AtomicInteger()
    private val legacyFailure = AtomicInteger()
    private val legacyDisabled = AtomicInteger()
    private val hydrated = AtomicBoolean(false)
    private val sinceFlush = AtomicInteger()
    @Volatile private var onChanged: ((Map<String, Int>) -> Unit)? = null

    fun bind(initial: Map<String, Int>, onChanged: (Map<String, Int>) -> Unit) {
        synchronized(this) {
            if (hydrated.compareAndSet(false, true)) {
                opsSuccess.set(initial["ops_success"] ?: 0)
                opsStale.set(initial["ops_stale"] ?: 0)
                opsOtherFailure.set(initial["ops_other_failure"] ?: 0)
                opsDisabled.set(initial["ops_disabled"] ?: 0)
                legacySuccess.set(initial["legacy_success"] ?: 0)
                legacyFailure.set(initial["legacy_failure"] ?: 0)
                legacyDisabled.set(initial["legacy_disabled"] ?: 0)
            }
            this.onChanged = onChanged
        }
    }

    fun recordOpsSuccess() = bump(opsSuccess)
    fun recordOpsStale() = bump(opsStale)
    fun recordOpsOtherFailure() = bump(opsOtherFailure)
    fun recordOpsDisabled() = bump(opsDisabled)
    fun recordLegacySuccess() = bump(legacySuccess)
    fun recordLegacyFailure() = bump(legacyFailure)
    fun recordLegacyDisabled() = bump(legacyDisabled)

    fun snapshot(): Map<String, Int> = mapOf(
        "ops_success" to opsSuccess.get(),
        "ops_stale" to opsStale.get(),
        "ops_other_failure" to opsOtherFailure.get(),
        "ops_disabled" to opsDisabled.get(),
        "legacy_success" to legacySuccess.get(),
        "legacy_failure" to legacyFailure.get(),
        "legacy_disabled" to legacyDisabled.get(),
    )

    fun flush() {
        onChanged?.invoke(snapshot())
        sinceFlush.set(0)
    }

    fun reset() {
        listOf(
            opsSuccess, opsStale, opsOtherFailure, opsDisabled,
            legacySuccess, legacyFailure, legacyDisabled,
        ).forEach { it.set(0) }
        flush()
    }

    /**
     * 粗观测启发式：ops 够多、过期率不高、且仍有 legacy 失败时，建议关掉 legacy。
     * 不自动改偏好，仅供设置页提示。
     */
    fun shouldSuggestDisableLegacy(
        minOpsSuccess: Int = 20,
        maxStaleRatioPercent: Int = 20,
        minLegacyFailures: Int = 5,
    ): Boolean {
        val ops = opsSuccess.get()
        if (ops < minOpsSuccess) return false
        if (legacyFailure.get() < minLegacyFailures) return false
        val stale = opsStale.get()
        return stale * 100 <= ops * maxStaleRatioPercent
    }

    /**
     * M9 后反向观测：legacy 默认关闭时，若模型仍大量提交 oldText 被拒，
     * 且 ops 尚未明显跑通，建议临时开启 legacy（弱模型兼容）。
     * 不自动改偏好，仅供设置页提示。
     */
    fun shouldSuggestEnableLegacy(
        minLegacyDisabled: Int = 8,
        maxOpsSuccessForTip: Int = 10,
    ): Boolean {
        if (legacyDisabled.get() < minLegacyDisabled) return false
        return opsSuccess.get() <= maxOpsSuccessForTip
    }

    private fun bump(counter: AtomicInteger) {
        counter.incrementAndGet()
        if (sinceFlush.incrementAndGet() >= 10) flush()
    }

    /** 测试用：清空绑定与计数。 */
    internal fun resetForTests() {
        synchronized(this) {
            onChanged = null
            hydrated.set(false)
            sinceFlush.set(0)
            listOf(
                opsSuccess, opsStale, opsOtherFailure, opsDisabled,
                legacySuccess, legacyFailure, legacyDisabled,
            ).forEach { it.set(0) }
        }
    }
}
