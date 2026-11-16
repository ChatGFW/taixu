package top.wkbin.taixu.ui.chat

import java.io.File

/**
 * 交付产物路径解析（纯函数，便于单测）。
 *
 * harness 侧的 `ToolFileArtifacts.relativePath` 已经把会话工作区根剥掉，
 * 卡片这边只负责把「会话 workspace + 相对路径」还原成宿主文件。
 */
internal object DeliveryArtifactPaths {

    fun hostFile(workspace: String, relativePath: String, workspaceRoot: File?): File? {
        if (workspaceRoot == null) return null
        val base = workspace.trim().replace('\\', '/').trimEnd('/')
        if (base.startsWith('/') && base != "/workspace" && !base.startsWith("/workspace/")) return null
        val directory = if (base == "/workspace" || base == "workspace") workspaceRoot else {
            val relative = base.removePrefix("/").removePrefix("workspace/")
            if (relative.isBlank() || relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
            File(workspaceRoot, relative)
        }
        val normalized = relativePath.trim().replace('\\', '/').removePrefix("/")
        if (normalized.isBlank()) return null
        if (normalized.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        val file = File(directory, normalized)
        return runCatching { file.takeIf { it.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath()) } }.getOrNull()
    }
}
