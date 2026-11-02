package top.wkbin.taixu.runtime.proot

import java.io.File
import java.nio.file.Path

/** Paths shared by argument construction and storage diagnostics. */
internal object ProotMountLayout {
    val hostSystemPaths = listOf(
        "/apex", "/data/app", "/data/dalvik-cache",
        "/data/misc/apexdata/com.android.art/dalvik-cache",
        "/system", "/system_ext", "/vendor", "/product", "/odm",
        "/linkerconfig/com.android.art/ld.config.txt", "/linkerconfig/ld.config.txt",
        "/plat_property_contexts", "/property_contexts",
    )

    /**
     * Extra host paths so Bionic ELF（如 Termux `adb`）能解析 `PT_INTERP`。
     *
     * `/system/bin/linker64` 常是指向 `/apex/com.android.runtime/bin/linker64` 的符号链接；
     * 若只绑了 `/system` 而整棵 `/apex` 因 `canRead()` 失败被跳过，guest 里解释器就会悬空，
     * `exec` 得到 ENOENT。这里把 linker 解析到的 apex 包根与常见 runtime 包单独补绑。
     */
    fun androidLinkerBindPaths(): List<String> {
        val paths = linkedSetOf<String>()
        for (linker in listOf("/system/bin/linker64", "/system/bin/linker")) {
            val link = File(linker)
            if (!link.exists()) continue
            val target = runCatching { link.canonicalFile }.getOrNull() ?: continue
            apexPackageRoot(target)?.let { paths += it.absolutePath }
            if (isBindableHostPath(target)) paths += target.absolutePath
        }
        for (candidate in listOf("/apex/com.android.runtime", "/apex/com.android.art")) {
            val dir = File(candidate)
            if (isBindableHostPath(dir)) paths += candidate
        }
        return paths.toList()
    }

    /** True when PRoot should attempt `-b` for this host path. */
    fun isBindableHostPath(path: File): Boolean {
        if (!path.exists()) return false
        if (path.canRead()) return true
        // `/apex` 顶层在部分机型上对普通应用 canRead=false，但子包仍可读。
        if (path.isDirectory) {
            val children = runCatching { path.listFiles() }.getOrNull() ?: return false
            return children.any { child -> child.canRead() || (child.isFile && child.exists()) }
        }
        return false
    }

    private fun apexPackageRoot(file: File): File? =
        generateSequence(file) { it.parentFile }
            .firstOrNull { parent -> parent.parentFile?.absolutePath == "/apex" }

    fun placeholderCandidates(rootfs: File): Set<Path> {
        val physicalRoot = rootfs.canonicalFile.toPath()
        val guestPaths = hostSystemPaths + listOf(
            "/dev", "/proc", "/sys", "/tmp", "/workspace", "/root", "/opt/taixu", "/attachments",
            "/sdcard", "/sdcard/Download", "/sdcard/Documents",
            // Guest alias for link2symlink backing store, NOT the real rootfs/.l2s store.
            File(rootfs, ".l2s").absolutePath.replace('\\', '/'),
            File(rootfs.canonicalFile, ".l2s").absolutePath.replace('\\', '/'),
        )
        return guestPaths.map { physicalRoot.resolve(it.trimStart('/')).normalize() }
            .filter { it != physicalRoot && it.startsWith(physicalRoot) }.toSet()
    }

    /** PRoot glue.c creates final guest mount placeholders with mode 000. No chmod needed. */
    fun isRestrictedPlaceholder(
        path: Path, candidates: Set<Path>, isDirectory: Boolean, permissionBits: Int, ownerUid: Int, appUid: Int,
    ): Boolean = path in candidates && isDirectory && permissionBits == 0 && ownerUid == appUid
}
