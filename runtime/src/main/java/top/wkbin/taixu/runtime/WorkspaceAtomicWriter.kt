package top.wkbin.taixu.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFileAttributeView

/** Replace the resolved target, retaining symlinks and existing POSIX permissions. */
internal object WorkspaceAtomicWriter {
    fun write(file: File, writeContent: (File) -> Unit) {
        val target = file.canonicalFile
        val parent = requireNotNull(target.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "无法创建父目录" }
        val permissions = if (target.exists()) {
            Files.getFileAttributeView(target.toPath(), PosixFileAttributeView::class.java)
                ?.readAttributes()?.permissions()
        } else null
        val temporary = File.createTempFile(".taixu-", ".tmp", parent)
        try {
            writeContent(temporary)
            if (permissions != null && Files.getPosixFilePermissions(temporary.toPath()) != permissions) {
                Files.setPosixFilePermissions(temporary.toPath(), permissions)
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // A failed move leaves the original intact; never truncate it with copyTo.
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }
}
