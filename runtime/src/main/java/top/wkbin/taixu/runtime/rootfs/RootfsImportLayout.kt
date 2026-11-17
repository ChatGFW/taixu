package top.wkbin.taixu.runtime.rootfs

import java.io.File

/** 导入归档若只有单一顶层目录且含发行版标记，则把该目录当作根。 */
internal fun File.normalizedImportRoot(): File {
    val children = listFiles().orEmpty().filterNot { it.name == ".taixu" }
    if (children.size == 1 && children[0].isDirectory) {
        val nested = children[0]
        val hasRootMarker = File(nested, "etc/os-release").isFile || File(nested, "usr/lib/os-release").isFile
        if (hasRootMarker) return nested
    }
    return this
}
