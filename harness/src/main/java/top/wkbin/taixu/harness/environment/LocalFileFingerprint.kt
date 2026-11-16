package top.wkbin.taixu.harness.environment

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.taixu.runtime.environment.FileFingerprint
import top.wkbin.taixu.runtime.environment.fingerprintStream
import top.wkbin.taixu.runtime.environment.MAX_FILE_FINGERPRINT_BYTES

internal suspend fun localFileExists(resolve: () -> File): Boolean? = withContext(Dispatchers.IO) {
    try {
        Files.readAttributes(resolve().toPath(), BasicFileAttributes::class.java)
        true
    } catch (_: java.nio.file.NoSuchFileException) { false }
    catch (_: Exception) { null }
}

internal suspend fun localFileSize(resolve: () -> File): Long? = withContext(Dispatchers.IO) {
    try { resolve().takeIf { it.isFile }?.length() }
    catch (_: Throwable) { null }
}

/** Caller resolves confinement first. No final symlink is followed when opening the stream. */
internal suspend fun fingerprintLocalFile(file: File, maxBytes: Long, afterRead: () -> Unit = {}): FileFingerprint {
    require(maxBytes in 1..MAX_FILE_FINGERPRINT_BYTES)
    fun attributes() = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    val before = attributes()
    require(before.isRegularFile && before.size() in 0..maxBytes) { "文件不存在或超过指纹大小限制" }
    val fingerprint = Files.newInputStream(file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use {
        fingerprintStream(it, maxBytes)
    }
    afterRead()
    val after = attributes()
    check(before.fileKey() == after.fileKey() && before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime() &&
        fingerprint.bytes == after.size()) { "读取期间文件发生变化，不能登记指纹" }
    return fingerprint
}
