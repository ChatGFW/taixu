package top.wkbin.taixu.plugin.packages

import top.wkbin.taixu.core.model.PackageCapability
import top.wkbin.taixu.core.model.PackageExtension
import top.wkbin.taixu.core.model.ToolManifest

/** Static-only v2 Web packages. Metadata can select host operations, never executable callbacks. */
object WebPluginPackagePolicy {
    const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024L
    const val MAX_FILES = 63 // One additional slot is reserved for the host's theme.css.
    const val MAX_ARCHIVE_BYTES = MAX_PAYLOAD_BYTES + 1024 * 1024
    const val MAX_ARCHIVE_ENTRIES = 129
    const val MAX_TREE_ENTRIES = 1 + MAX_FILES * 16
    const val PACKAGE_SOURCE = "PACKAGE"
    const val PROOT_STATIC_SOURCE = "PROOT_STATIC"
    val APP_INFO = PackageCapability("APP_INFO", "application")
    val CLIPBOARD_WRITE = PackageCapability("CLIPBOARD_WRITE", "clipboard")

    fun isWeb(manifest: ToolManifest) = manifest.schemaVersion == 2 && manifest.plugin?.execution == "WEB"

    fun validate(manifest: ToolManifest) {
        val spec = requireNotNull(manifest.plugin)
        require(isWeb(manifest)) { "不是静态 Web 插件" }
        require(spec.webSource in setOf(PACKAGE_SOURCE, PROOT_STATIC_SOURCE)) { "不支持的 Web 页面来源" }
        require(manifest.name.isNotBlank() && manifest.name.length <= 128 && manifest.description.length in 1..2048) { "Web 插件名称或描述无效" }
        require(manifest.installSteps.isEmpty() && manifest.uninstallSteps.isEmpty() && manifest.environment.isEmpty() &&
            manifest.commandLinks.isEmpty() && manifest.launchCommand == null && manifest.verifyCommand == null &&
            manifest.dependencies.isEmpty() && manifest.permissions.isEmpty() && manifest.servicePort == null &&
            manifest.launchType == "one_shot" && manifest.servicePath == "/") { "静态 Web 插件不能声明脚本、进程、环境变量或服务" }
        require(spec.extensions == listOf(PackageExtension("main", "PAGE"))) { "Web 插件必须声明 main PAGE 扩展" }
        require(spec.dependencies.isEmpty() && spec.resources.isEmpty()) { "静态 Web 插件不支持依赖或全局资源" }
        require(spec.capabilities.distinct() == spec.capabilities && spec.capabilities.all { it == APP_INFO || it == CLIPBOARD_WRITE }) { "不支持的 Web 插件能力" }
        require(spec.integrity.size in 1..MAX_FILES && "index.html" in spec.integrity) { "Web 插件缺少 index.html 或文件过多" }
        require(spec.integrity.keys.all(::safeAssetPath)) { "Web 插件资源路径无效或占用 theme.css" }
    }

    fun safeAssetPath(path: String): Boolean = path.length in 1..256 && path != "theme.css" &&
        path.split('/').let { parts -> parts.size <= 16 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")) } }
}
