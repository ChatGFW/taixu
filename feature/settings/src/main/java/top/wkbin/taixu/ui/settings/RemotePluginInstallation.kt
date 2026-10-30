package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.core.database.ToolEntity
import top.wkbin.taixu.core.model.ToolManifest
import top.wkbin.taixu.core.tools.RemotePluginRelease
import top.wkbin.taixu.plugin.packages.PluginPackageProtocol
import top.wkbin.taixu.plugin.packages.SemanticVersion

internal data class RemotePluginInstallation(val version: String?, val installing: Boolean, val updateAvailable: Boolean) {
    val installed: Boolean get() = version != null
}

/** Installation is determined by persisted state, independently of version parsing. */
internal fun remotePluginInstallation(
    release: RemotePluginRelease, tools: List<ToolEntity>, webPackages: List<ToolManifest>,
): RemotePluginInstallation {
    val tool = tools.find { it.id == release.toolId }
    val version = if (release.execution == "WEB") {
        webPackages.find { it.id == release.toolId && PluginPackageProtocol.identity(it) == release.pluginId }?.version
    } else if (tool?.state != "UNINSTALLED") {
        tool?.installedVersion?.takeIf { it.isNotBlank() }
            ?: tool?.manifestVersion?.takeIf { tool.state in setOf("INSTALLED", "UPDATE_AVAILABLE") }
    } else null
    val current = version?.trim()?.removePrefix("v")?.let(SemanticVersion::parse)
    val latest = SemanticVersion.parse(release.version)
    return RemotePluginInstallation(version, release.execution != "WEB" && tool?.state == "INSTALLING",
        current != null && latest != null && latest > current)
}
