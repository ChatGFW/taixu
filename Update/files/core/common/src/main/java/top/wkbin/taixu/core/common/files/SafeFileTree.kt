package top.wkbin.taixu.core.common.files

import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/** File operations that never follow symbolic links while traversing a tree. */
object SafeFileTree {
    fun copy(source: File, target: File) {
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        if (Files.isSymbolicLink(sourcePath)) {
            target.parentFile?.mkdirs()
            Files.deleteIfExists(targetPath)
            Files.createSymbolicLink(targetPath, Files.readSymbolicLink(sourcePath))
            return
        }
        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destination = targetPath.resolve(sourcePath.relativize(file))
                Files.createDirectories(destination.parent)
                if (Files.isSymbolicLink(file)) {
                    Files.deleteIfExists(destination)
                    Files.createSymbolicLink(destination, Files.readSymbolicLink(file))
                } else {
                    runCatching {
                        Files.copy(
                            file,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES,
                            LinkOption.NOFOLLOW_LINKS,
                        )
                    }.getOrElse {
                        Files.copy(
                            file,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            LinkOption.NOFOLLOW_LINKS,
                        )
                    }
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun delete(file: File) {
        val path = file.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path)
            return
        }
        // 安装回滚时，刚被强杀的 npm/node 子进程可能仍在向目录写入文件，导致
        // walkFileTree 走到 postVisitDirectory 时目录又非空。短暂等待后重试整棵
        // 目录树，消除这类瞬时竞争。
        // 另外 PRoot glue.c 会把绑定目标做成 mode 000 的占位目录（典型是 .l2s 的宿主
        // 绝对路径绑定链），父目录可写但自身不可读，遍历会抛 AccessDeniedException。
        // 这里先恢复报错目录的属主权限，再整树重试，避免更新事务卡死在占位目录上。
        var lastError: IOException? = null
        var attempt = 0
        while (attempt < DELETE_TOTAL_ATTEMPTS) {
            attempt++
            try {
                walkDelete(path)
                return
            } catch (notEmpty: DirectoryNotEmptyException) {
                lastError = notEmpty
            } catch (denied: AccessDeniedException) {
                lastError = denied
                val blocked = denied.file?.let { File(it) }
                if (blocked == null || !unlockPermissions(blocked)) throw denied
            }
            if (attempt < DELETE_TOTAL_ATTEMPTS) {
                Thread.sleep(DELETE_RETRY_DELAY_MS)
            }
        }
        throw lastError ?: IllegalStateException("无法删除目录：$file")
    }

    /** 恢复 mode 000 占位目录的属主读写执行位；无权限改动时返回 false。 */
    private fun unlockPermissions(dir: File): Boolean {
        val readable = dir.setReadable(true, true)
        val executable = dir.setExecutable(true, true)
        dir.setWritable(true, true)
        return readable || executable || dir.canRead()
    }

    private fun walkDelete(path: Path) {
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                error?.let { throw it }
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private const val DELETE_TOTAL_ATTEMPTS = 40
    private const val DELETE_RETRY_DELAY_MS = 200L
}
