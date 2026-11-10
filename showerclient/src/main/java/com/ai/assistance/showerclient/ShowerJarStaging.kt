package com.ai.assistance.showerclient

import java.io.File
import java.io.IOException
import java.io.InputStream

/** Fresh names avoid stale files owned by another install or the shell in shared Download. */
internal fun stageShowerJar(candidates: List<File>, openJar: () -> InputStream): File {
    val failure = IOException("无法写入 shower-server.jar 过渡文件，已尝试：$candidates")
    for (directory in candidates) {
        var staged: File? = null
        var complete = false
        try {
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("无法创建目录 $directory")
            }
            val file = File.createTempFile("shower-server-", ".jar", directory)
            staged = file
            openJar().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            // The privileged shell must be able to copy this file into its working directory.
            file.setReadable(true, false)
            complete = true
            return file
        } catch (e: IOException) {
            failure.addSuppressed(e)
        } catch (e: SecurityException) {
            failure.addSuppressed(e)
        } finally {
            // Only remove our partial output. Existing files belong to their original owners.
            if (!complete) runCatching { staged?.delete() }
        }
    }
    throw failure
}
