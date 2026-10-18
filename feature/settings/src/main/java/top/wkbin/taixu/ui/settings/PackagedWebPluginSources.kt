package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.core.datastore.RemoteServerRepository
import top.wkbin.taixu.core.model.ToolManifest
import top.wkbin.taixu.core.tools.WebPluginRepository
import top.wkbin.taixu.plugin.ui.web.WebPluginAssetLease
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.service.ProotWebAssetService

/** Called only inside the page's reserved resource owner; construction never launches Linux. */
class PackagedWebPluginSources(
    private val packages: WebPluginRepository,
    private val linux: LinuxRuntime,
    private val servers: RemoteServerRepository,
) {
    suspend fun openProot(expected: ToolManifest): WebPluginAssetLease {
        val spec = requireNotNull(expected.plugin)
        require(spec.execution == "WEB" && spec.webSource == "PROOT_STATIC")
        // Bind bytes to the exact selected immutable version before preparing a lazy process source.
        val assets = packages.snapshot(expected)
        val source = ProotWebAssetService(linux, assets,
            isLocal = { servers.activeId.value == null && packages.isCurrent(expected) },
            ownerPluginId = spec.id)
        return WebPluginAssetLease(emptyMap(), available = { source.isAvailable },
            release = { source.close() }, load = { source.snapshot() })
    }
}
