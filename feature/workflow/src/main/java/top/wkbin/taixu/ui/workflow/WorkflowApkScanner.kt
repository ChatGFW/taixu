package top.wkbin.taixu.ui.workflow

import java.io.File

internal fun scanApksInWorkspace(workspaceDir: File, projectName: String): List<DiscoveredApk> {
    val safeProject = projectName.trim().trim('/').takeIf { it.isNotEmpty() }
    val projectDir = if (safeProject != null) File(workspaceDir, safeProject) else null

    // 优先在当前工程目录搜索，如果当前工程下未找到，再扩大至整个工作区根目录
    val targetDirs = listOfNotNull(
        projectDir?.takeIf { it.isDirectory },
        workspaceDir.takeIf { it.isDirectory },
    ).distinct()

    val results = mutableListOf<DiscoveredApk>()
    val seenPaths = mutableSetOf<String>()

    for (dir in targetDirs) {
        val isProjectScope = dir == projectDir
        runCatching {
            dir.walkTopDown()
                .maxDepth(8)
                .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                .forEach { file ->
                    val relPath = file.relativeTo(dir).path.replace('\\', '/')
                    val sandboxPath = if (isProjectScope && safeProject != null) {
                        "/workspace/$safeProject/$relPath"
                    } else {
                        val relToWorkspace = file.relativeTo(workspaceDir).path.replace('\\', '/')
                        "/workspace/$relToWorkspace"
                    }
                    if (seenPaths.add(file.absolutePath)) {
                        results.add(
                            DiscoveredApk(
                                file = file,
                                name = file.name,
                                relativePath = if (isProjectScope) relPath else file.relativeTo(workspaceDir).path.replace('\\', '/'),
                                sandboxPath = sandboxPath,
                                sizeBytes = file.length(),
                                lastModified = file.lastModified(),
                            ),
                        )
                    }
                }
        }
        if (results.isNotEmpty()) break
    }

    return results.sortedByDescending { it.lastModified }
}
