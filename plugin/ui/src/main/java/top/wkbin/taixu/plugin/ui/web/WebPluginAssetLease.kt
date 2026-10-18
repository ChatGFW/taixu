package top.wkbin.taixu.plugin.ui.web

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Host-owned source instance. No endpoint or credential is given to Web content. */
class WebPluginAssetLease(
    assets: Map<String, ByteArray>,
    private val available: () -> Boolean = { true },
    private val release: suspend () -> Boolean = { true },
    private val load: (suspend () -> Map<String, ByteArray>)? = null,
) {
    private val lock = Mutex()
    private var released = false
    @Volatile private var snapshot: Map<String, ByteArray>? = assets.mapValues { it.value.copyOf() }
    val isAvailable: Boolean get() = snapshot != null && runCatching(available).getOrDefault(false)
    internal fun assets(): Map<String, ByteArray> = checkNotNull(snapshot)
    internal suspend fun loadAssets(): Map<String, ByteArray> {
        check(isAvailable)
        val result = load?.invoke() ?: assets()
        check(isAvailable)
        return result
    }

    /** False keeps the resource in the activation ledger for a later cleanup attempt. */
    suspend fun close(): Boolean = lock.withLock {
        snapshot = null // Close admission even when the backing service fails to stop.
        if (released) true else try { release().also { released = it } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}
