package top.wkbin.taixu.harness

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.harness.core.ToolBackend
import top.wkbin.taixu.harness.environment.SessionExecutionEnvironments
import top.wkbin.taixu.harness.text.HashlineEditArgs
import top.wkbin.taixu.harness.text.HashlineEditStats
import top.wkbin.taixu.harness.text.HashlinePolicy
import top.wkbin.taixu.harness.text.UnifiedDiffGenerator
import top.wkbin.taixu.runtime.environment.FileFingerprint
import top.wkbin.taixu.runtime.environment.HashlineAnchors
import top.wkbin.taixu.runtime.environment.WorkspaceEditOutcome

data class WorkspaceToolRequest(
    val tool: HarnessTool,
    val args: JsonObject,
    val sessionId: String,
    val workspace: String,
    /** Trusted pipeline callback, applied to whole matching lines before excerpts are cut. */
    val redactSearchLine: (String) -> String = { it },
)

data class WorkspaceToolOutcome(
    val success: Boolean,
    val output: String,
    val metadata: Map<String, String> = emptyMap(),
)

/** File-tool semantics independent of the concrete filesystem, still inside ToolExecutor policy. */
class WorkspaceToolBackend(
    private val operationsFor: (String) -> WorkspaceToolOperations,
    private val snapshots: WorkspaceMutationSnapshots = WorkspaceMutationSnapshots(),
    private val imagePayload: (String) -> String = ImagePayloadCompressor::downscaleDataUrl,
    private val environments: SessionExecutionEnvironments? = null,
    private val hashlinePolicy: () -> HashlinePolicy = { HashlinePolicy.DEFAULT },
) : ToolBackend<WorkspaceToolRequest, WorkspaceToolOutcome> {
    override suspend fun execute(request: WorkspaceToolRequest): WorkspaceToolOutcome {
        currentCoroutineContext().ensureActive()
        val operations = environments?.environment(request.sessionId, request.workspace)?.files ?: operationsFor(request.workspace)
        val path = JsonArgs.requireString(request.args, "path")
        val outcome = when (request.tool) {
            HarnessTool.READ -> if ("expected_sha256" in request.args) ArtifactReadVerifier.read(operations, path, request.args) {
                read(operations, path, request.args, request.workspace, request.redactSearchLine)
            } else read(operations, path, request.args, request.workspace, request.redactSearchLine)
            HarnessTool.WRITE -> {
                val content = JsonArgs.requireString(request.args, "content")
                snapshots.before(request.sessionId, operations, path)
                currentCoroutineContext().ensureActive()
                val written = operations.write(path, content)
                currentCoroutineContext().ensureActive()
                if (written is AppResult.Success) snapshots.after(request.sessionId, operations, path, content)
                written.output("已写入 $path\nDIFF_STAT: +${content.lines().size} -0", "write")
            }
            HarnessTool.EDIT -> edit(operations, path, request)
            else -> throw IllegalArgumentException("Unsupported workspace tool: ${request.tool}")
        }
        currentCoroutineContext().ensureActive()
        return outcome
    }

    private suspend fun read(operations: WorkspaceToolOperations, path: String, args: JsonObject, workspace: String, redactSearchLine: (String) -> String): WorkspaceToolOutcome {
        if ("query" in args) {
            val query = JsonArgs.requireString(args, "query")
            require("offset" !in args && "limit" !in args) { "检索 query 不能与读取分页 offset/limit 混用" }
            val maxResults = JsonArgs.optionalLong(args, "max_results", 50, 1, 100).toInt()
            return operations.search(path, query, maxResults, redactSearchLine).output(actionName = "search")
        }
        require("max_results" !in args) { "max_results 仅适用于 query 检索" }
        val mime = imageMime(path)
        if (mime == null) {
            val offset = args["offset"]?.jsonPrimitive?.content?.trim()?.toIntOrNull()
            val limit = args["limit"]?.jsonPrimitive?.content?.trim()?.toIntOrNull()
            val anchors = args["anchors"]?.jsonPrimitive?.content?.trim()?.toBooleanStrictOrNull()
                ?: hashlinePolicy().readAnchorsDefault
            return operations.read(path, offset, limit, anchors).output(actionName = "read")
        }
        return when (val bytes = operations.readRawBytes(path)) {
            is AppResult.Success -> {
                currentCoroutineContext().ensureActive()
                val base64 = Base64.getEncoder().encodeToString(bytes.data)
                val relative = ToolFileArtifacts.relativePath(path, workspace)
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes.data).joinToString("") { "%02x".format(it) }
                val snapshots = relative?.let { listOf(ToolFileArtifacts.observation(it, FileFingerprint(digest, bytes.data.size.toLong()),
                    FileArtifactOrigin.IMAGE_READ, System.currentTimeMillis())) }.orEmpty()
                WorkspaceToolOutcome(
                    true, "已读取图片文件 $path（${bytes.data.size} 字节，$mime）。" +
                        "图像已作为多模态附件随本次工具结果提供。" +
                        "若这是虚拟屏截图，点击坐标用 0–1000 相对位置，不要用图上的像素。" +
                        "若当前模型不支持视觉，请改用文字/脚本方式描述图片内容。",
                    mapOf("image_payload" to imagePayload("data:$mime;base64,$base64"),
                        ToolFileArtifacts.METADATA_KEY to ToolFileArtifacts.encode(snapshots, snapshots.isNotEmpty() && bytes.data.isNotEmpty())),
                )
            }
            is AppResult.Failure -> bytes.output(actionName = "read")
        }
    }

    private suspend fun edit(
        operations: WorkspaceToolOperations,
        path: String,
        request: WorkspaceToolRequest,
    ): WorkspaceToolOutcome {
        val args = request.args
        val hashline = HashlineEditArgs.hasOps(args)
        val legacy = HashlineEditArgs.hasLegacy(args)
        if (hashline && legacy) {
            return WorkspaceToolOutcome(
                false,
                "edit 不能同时使用 ops 与 oldText/newText（HASHLINE_MIXED_MODE）。请只保留一种编辑形态。",
                mapOf("hashline_error" to "HASHLINE_MIXED_MODE"),
            )
        }
        if (!hashline && !legacy) {
            return WorkspaceToolOutcome(
                false,
                "edit 需要 ops 或 oldText/newText。",
                mapOf("hashline_error" to "HASHLINE_MISSING_ARGS"),
            )
        }
        if (hashline && HashlineEditArgs.opCount(args) <= 0) {
            return WorkspaceToolOutcome(
                false,
                "ops 不能为空（HASHLINE_EMPTY_OPS）。",
                mapOf("hashline_error" to "HASHLINE_EMPTY_OPS"),
            )
        }
        val policy = hashlinePolicy()
        if (hashline && !policy.editOpsEnabled) {
            HashlineEditStats.recordOpsDisabled()
            return WorkspaceToolOutcome(
                false,
                "Hashline ops 编辑已关闭（agent.hashline.editOpsEnabled=false）。请改用 oldText/newText，或在设置中开启。",
                mapOf("hashline_error" to "HASHLINE_OPS_DISABLED"),
            )
        }
        if (legacy && !policy.legacyEditEnabled) {
            HashlineEditStats.recordLegacyDisabled()
            return WorkspaceToolOutcome(
                false,
                "legacy oldText/newText 编辑已关闭（agent.hashline.legacyEditEnabled=false）。请先 read 获取 n:h 锚点后用 ops 编辑。",
                mapOf("hashline_error" to "HASHLINE_LEGACY_DISABLED"),
            )
        }
        snapshots.before(request.sessionId, operations, path)
        currentCoroutineContext().ensureActive()
        val edited = if (hashline) {
            editHashline(operations, path, args)
        } else {
            operations.editDetailed(
                path,
                JsonArgs.requireString(args, "oldText"),
                JsonArgs.requireString(args, "newText"),
            )
        }
        currentCoroutineContext().ensureActive()
        return when (edited) {
            is AppResult.Success -> {
                snapshots.after(request.sessionId, operations, path, null)
                if (hashline) HashlineEditStats.recordOpsSuccess() else HashlineEditStats.recordLegacySuccess()
                val diffStat = if (hashline) {
                    "DIFF_STAT: +${edited.data.linesAdded} -${edited.data.linesRemoved}"
                } else {
                    val oldText = JsonArgs.requireString(args, "oldText")
                    val newText = JsonArgs.requireString(args, "newText")
                    "DIFF_STAT: +${newText.lines().size} -${oldText.lines().size}"
                }
                val summary = if (hashline) {
                    "已修改 $path（匹配策略：${edited.data.strategy}，操作 ${edited.data.replacements}，替换区间 ${edited.data.linesRemoved} 行）"
                } else {
                    "已修改 $path（匹配策略：${edited.data.strategy}，替换 ${edited.data.replacements} 处）"
                }
                val metadata = buildMap {
                    put("strategy", edited.data.strategy)
                    if (hashline) put("hashline_ops", edited.data.replacements.toString())
                    edited.data.diff?.let { put("diff", it) }
                }
                WorkspaceToolOutcome(true, "$summary\n$diffStat", metadata)
            }
            is AppResult.Failure -> {
                if (hashline) {
                    val cause = edited.error.cause
                    if (cause is HashlineAnchors.ApplyException.Stale) HashlineEditStats.recordOpsStale()
                    else HashlineEditStats.recordOpsOtherFailure()
                    val code = when (cause) {
                        is HashlineAnchors.ApplyException.Stale -> "HASHLINE_STALE"
                        is HashlineAnchors.ApplyException.BadAnchor -> "HASHLINE_BAD_ANCHOR"
                        is HashlineAnchors.ApplyException.Overlap -> "HASHLINE_OVERLAP"
                        is HashlineAnchors.ApplyException.Invalid -> "HASHLINE_INVALID"
                        else -> "HASHLINE_FAILURE"
                    }
                    val base = edited.output(actionName = "hashline")
                    val withPathHint = if (cause is HashlineAnchors.ApplyException.Stale) {
                        base.copy(
                            output = base.output.trimEnd() +
                                "\n请重新 read(\"$path\", offset=1, limit=80) 获取新锚点后再 edit；禁止改用猜测的 oldText 盲试同一处。",
                        )
                    } else {
                        base
                    }
                    withPathHint.copy(metadata = withPathHint.metadata + ("hashline_error" to code))
                } else {
                    HashlineEditStats.recordLegacyFailure()
                    edited.output(actionName = "edit")
                }
            }
        }
    }

    private suspend fun editHashline(
        operations: WorkspaceToolOperations,
        path: String,
        args: JsonObject,
    ): AppResult<WorkspaceEditOutcome> {
        val ops = try {
            HashlineEditArgs.parseOps(args)
        } catch (error: IllegalArgumentException) {
            return AppResult.Failure(
                AppError(
                    ErrorCode.IO,
                    error.message ?: "ops 参数非法",
                    HashlineAnchors.ApplyException.Invalid(error.message ?: "ops 参数非法"),
                ),
            )
        }
        return when (val before = operations.readTextForEdit(path)) {
            is AppResult.Failure -> before
            is AppResult.Success -> try {
                val applied = HashlineAnchors.apply(before.data, ops)
                when (val written = operations.write(path, applied.updated)) {
                    is AppResult.Failure -> written
                    is AppResult.Success -> AppResult.Success(
                        WorkspaceEditOutcome(
                            strategy = "hashline",
                            replacements = applied.operations,
                            diff = UnifiedDiffGenerator.diff(
                                before.data,
                                applied.updated,
                                path.trim().trimStart('/'),
                            ),
                            linesAdded = applied.linesAdded,
                            linesRemoved = applied.linesRemoved,
                        ),
                    )
                }
            } catch (error: HashlineAnchors.ApplyException) {
                AppResult.Failure(AppError(ErrorCode.IO, error.message ?: "hashline 编辑失败", error))
            }
        }
    }

    private fun AppResult<*>.output(successMessage: String = "", actionName: String): WorkspaceToolOutcome = when (this) {
        is AppResult.Success -> WorkspaceToolOutcome(true, successMessage.ifBlank { data.toString() })
        is AppResult.Failure -> {
            val hint = when (actionName) {
                "edit" -> "\n\n【文本替换失败反思与纠错要求】\n未在目标文件中找到唯一匹配的 oldText。请立即调用 read 查看该文件的最新真实内容与行号，获取精确匹配的内容后再发起 edit，严禁盲目猜测或重复相同内容！"
                "hashline" -> "\n\n【Hashline 编辑失败反思与纠错要求】\n锚点可能已过期或 ops 非法。请重新 read 获取最新 n:h 锚点后再 edit；不要改用猜测的 oldText 盲试同一处。"
                "read" -> "\n\n【文件读取失败反思与纠错要求】\n无法读取指定路径文件。请调用 base 执行 ls 或 find 确定文件的真实存在路径，切勿盲目重复错误路径！"
                else -> ""
            }
            WorkspaceToolOutcome(false, error.message + hint)
        }
    }

    private fun imageMime(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "ico" -> "image/x-icon"
        else -> null
    }
}
