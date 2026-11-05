package top.wkbin.taixu.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.taixu.core.common.logging.AppLogger
import java.io.File

/**
 * 一次性把历史附件按"谁引用过"搬到会话私有目录。
 *
 * 规则（2026-11-05 拍板）：
 * - 被 1 个会话引用 → 移动过去；
 * - 被多个会话引用 → 每个会话各复制一份；
 * - 没有任何引用 → 挪到 `attachments/legacy/` 保留，不删；
 * - 目录里的 `skills/` 是全局技能根，永远不动。
 *
 * 全程幂等：目标已存在视为成功；marker 存在则整体跳过。manifest 记录每次动作，
 * 需要回滚时可以把文件搬回原位。
 */
class AttachmentMigrator(
    private val filesDir: File,
    private val logger: AppLogger,
) {
    data class Summary(val moved: Int, val copied: Int, val orphaned: Int, val skipped: Boolean)

    suspend fun migrate(references: List<Pair<String, String>>): Summary = withContext(Dispatchers.IO) {
        val global = AttachmentPaths.global(filesDir)
        val marker = File(global, MARKER_NAME)
        if (marker.exists()) return@withContext Summary(0, 0, 0, skipped = true)
        global.mkdirs()

        val owners = linkedMapOf<String, MutableSet<String>>()
        references.forEach { (sessionId, payload) ->
            referencedAttachmentNames(payload).forEach { name ->
                owners.getOrPut(name) { linkedSetOf() }.add(sessionId)
            }
        }

        val manifest = mutableListOf<Map<String, String>>()
        var moved = 0
        var copied = 0
        var orphaned = 0
        owners.forEach { (name, sessions) ->
            val source = File(global, name)
            if (!source.isFile) return@forEach
            when {
                sessions.isEmpty() -> {
                    if (moveInto(source, AttachmentPaths.orphan(filesDir))) {
                        orphaned++
                        manifest += action(name, "orphan", listOf("legacy"))
                    }
                }
                sessions.size == 1 -> {
                    val sessionId = sessions.first()
                    if (moveInto(source, AttachmentPaths.session(filesDir, sessionId))) {
                        moved++
                        manifest += action(name, "move", listOf("sessions/${AttachmentPaths.segment(sessionId)}"))
                    }
                }
                else -> {
                    sessions.forEach { sessionId -> copyInto(source, AttachmentPaths.session(filesDir, sessionId)) }
                    copied++
                    manifest += action(name, "copy", sessions.map { "sessions/${AttachmentPaths.segment(it)}" })
                }
            }
        }

        // 没有被任何消息引用的旧文件同样移出全局根：它已不再挂进 guest。
        global.listFiles().orEmpty()
            .filter { it.isFile && it.name != MARKER_NAME && it.name != MANIFEST_NAME }
            .forEach { leftover ->
                if (moveInto(leftover, AttachmentPaths.orphan(filesDir))) {
                    orphaned++
                    manifest += action(leftover.name, "orphan", listOf("legacy"))
                }
            }

        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText(buildJsonObject {
                put("version", 2)
                put("manifest", buildJsonArray { manifest.forEach { row -> add(buildJsonObject { row.forEach { (k, v) -> put(k, v) } }) } })
            }.toString())
            File(global, MANIFEST_NAME).writeText(marker.readText())
        }.onFailure { logger.w("附件迁移清单写入失败", it) }

        logger.i("附件迁移完成：move=$moved copy=$copied orphan=$orphaned")
        Summary(moved, copied, orphaned, skipped = false)
    }

    private fun action(name: String, action: String, targets: List<String>): Map<String, String> =
        mapOf("name" to name, "action" to action, "targets" to targets.joinToString(","))

    private fun moveInto(source: File, targetDir: File): Boolean {
        targetDir.mkdirs()
        val target = File(targetDir, source.name)
        if (target.exists()) return true
        if (runCatching { source.renameTo(target) }.getOrDefault(false)) return true
        return runCatching {
            source.copyTo(target, overwrite = false)
            source.delete()
            true
        }.getOrElse { false }
    }

    private fun copyInto(source: File, targetDir: File): Boolean {
        targetDir.mkdirs()
        val target = File(targetDir, source.name)
        if (target.exists()) return true
        return runCatching { source.copyTo(target, overwrite = false); true }.getOrElse { false }
    }

    companion object {
        private const val MARKER_NAME = ".attachments-v2.done"
        private const val MANIFEST_NAME = ".attachments-v2.manifest.json"

        /** `/attachments/<name>` 里的一级名字；`skills` 是全局技能根，永远不算附件。 */
        private val REFERENCE = Regex("""/attachments/([A-Za-z0-9][A-Za-z0-9._-]{0,159})""")

        fun referencedAttachmentNames(payload: String): Set<String> =
            REFERENCE.findAll(payload)
                .map { it.groupValues[1] }
                .filter { it != "skills" && it != "legacy" }
                .toSet()
    }
}
