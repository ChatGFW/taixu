package top.wkbin.taixu.harness

import top.wkbin.taixu.core.common.result.AppResult

/** Scoped file operations. Implementations own path boundaries, size limits and atomic writes. */
interface WorkspaceToolOperations {
    suspend fun read(path: String, offset: Int? = null, limit: Int? = null): AppResult<String>
    suspend fun readRawBytes(path: String): AppResult<ByteArray>
    suspend fun write(path: String, content: String): AppResult<Unit>
    suspend fun editDetailed(path: String, oldText: String, newText: String): AppResult<WorkspaceEditOutcome>
    suspend fun previewOrNull(path: String): String?
    suspend fun fileSizeOrNull(path: String): Long?
}
