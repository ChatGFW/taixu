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
    context: android.content.Context,
    private val logger: AppLogger,
) {
    private val filesDir: File = context.filesDir
    data class Summary(val moved: Int, val copied: Int, val orphaned: Int, val skipped: Boolean)

    suspend fun migrate(references: List<Pair<String, String>>): Summary = withContext(Dispatchers.IO) {
        val global = AttachmentPaths.global(filesDir)
        relocateLegacySessionRoots()
        sweepStagedBotInputs()
        // Repair files orphaned by the earlier migration, including blob-backed messages.
        references.forEach { (sessionId, payload) ->
            referencedAttachmentNames(payload).forEach { name ->
                val source = File(AttachmentPaths.orphan(filesDir), name)
                if (source.isFile) check(copyInto(source, AttachmentPaths.session(filesDir, sessionId))) {
                    "无法恢复会话附件：$name"
                }
            }
        }
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
                    val allCopied = sessions.all { sessionId ->
                        copyInto(source, AttachmentPaths.session(filesDir, sessionId))
                    }
                    // 全部副本就绪后删源，否则末尾的"无引用清理"会把它再挪进 legacy，
                    // 形成 N 份会话副本 + 1 份孤儿，doctor 的 legacy 计数也会误导。
                    if (allCopied) source.delete()
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

    /**
     * v2 曾把会话目录放在 `linux-runtime/sessions/<sid>/attachments`；
     * v3 起统一收进 `attachments/sessions/<sid>`，让本地备份能覆盖到。
     * 在 marker 判断之前执行，保证已经迁移过的设备也能完成这次搬迁。
     */
    private fun relocateLegacySessionRoots() {
        val legacyRoot = File(AttachmentPaths.base(filesDir), "sessions")
        if (!legacyRoot.isDirectory) return
        var moved = 0
        legacyRoot.listFiles().orEmpty().forEach { sessionDir ->
            val source = File(sessionDir, "attachments")
            if (!source.isDirectory) return@forEach
            val target = AttachmentPaths.session(filesDir, sessionDir.name)
            source.listFiles().orEmpty().forEach { file ->
                if (moveInto(file, target)) moved++
            }
            runCatching { source.delete() }
            runCatching { sessionDir.delete() }
        }
        runCatching { legacyRoot.delete() }
        if (moved > 0) logger.i("附件会话目录已迁移到 attachments/sessions：$moved 个文件")
    }

    /**
     * 群聊附件先暂存在全局 `bot-input/<uuid>/`，再由各接收方复制进自己的会话目录。
     * 暂存目录此前没有任何回收路径，每发一次带附件的群消息就永久占一份磁盘（上限 32 MiB），
     * 而全局根已不挂进 guest，问题完全不可见。这里按 24 小时保留期清扫。
     */
    private fun sweepStagedBotInputs(now: Long = System.currentTimeMillis()) {
        val root = File(AttachmentPaths.global(filesDir), "bot-input")
        if (!root.isDirectory) return
        root.listFiles().orEmpty().forEach { stage ->
            if (!stage.isDirectory) return@forEach
            if (now - stage.lastModified() > STAGED_TTL_MS) {
                runCatching { stage.deleteRecursively() }
            }
        }
        runCatching { if (root.listFiles().isNullOrEmpty()) root.delete() }
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
        private const val STAGED_TTL_MS = 24L * 60 * 60 * 1000

        /** `/attachments/<name>` 里的一级名字；`skills` 是全局技能根，永远不算附件。 */
        private val REFERENCE = Regex("""/attachments/([A-Za-z0-9][A-Za-z0-9._-]{0,254})(?![A-Za-z0-9._-])""")

        /**
         * 一行式布局摘要，供 `taixu-host doctor` 显示：迁移是否执行、文件分布在哪。
         * 只读统计，不改动任何文件。
         */
        fun inspect(filesDir: File): String {
            val global = AttachmentPaths.global(filesDir)
            val legacy = AttachmentPaths.orphan(filesDir)
            val globalFiles = global.listFiles().orEmpty().count { it.isFile && !it.name.startsWith(".") }
            val legacyFiles = legacy.listFiles().orEmpty().count { it.isFile }
            val sessions = AttachmentPaths.sessionsRoot(filesDir).listFiles().orEmpty()
                .count { it.isDirectory && !it.name.startsWith(".") }
            val migrated = File(global, MARKER_NAME).exists()
            return "global=$globalFiles legacy=$legacyFiles sessions=$sessions migrated=${if (migrated) "yes" else "no"}"
        }

        fun referencedAttachmentNames(payload: String): Set<String> =
            REFERENCE.findAll(payload)
                .map { it.groupValues[1] }
                .filter { it != "skills" && it != "legacy" }
                .toSet()
    }
}
