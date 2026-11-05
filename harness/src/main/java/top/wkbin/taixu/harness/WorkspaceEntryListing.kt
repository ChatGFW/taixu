package top.wkbin.taixu.harness

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.runtime.environment.ExecutionFileEntry

/** Browsing may omit inaccessible entries; unlike artifact observation it does not assert completeness. */
internal suspend fun listWorkspaceEntries(root: File, resolve: () -> File): AppResult<List<ExecutionFileEntry>> =
    withContext(Dispatchers.IO) {
        try {
            val directory = resolve()
            require(directory.isDirectory)
            val context = currentCoroutineContext()
            val entries = Files.newDirectoryStream(directory.toPath()).use { stream ->
                stream.asSequence().take(5_000).mapNotNull { path ->
                    context.ensureActive()
                    try {
                        if (Files.isSymbolicLink(path) || !path.toFile().canonicalFile.toPath().startsWith(root.toPath())) null
                        else ExecutionFileEntry(path.fileName.toString(), Files.isDirectory(path), Files.size(path), Files.getLastModifiedTime(path).toMillis())
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }.toList()
            }
            AppResult.Success(entries.sortedBy { it.name })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AppResult.Failure(AppError(ErrorCode.IO, "无法浏览当前工作区目录")) }
    }
