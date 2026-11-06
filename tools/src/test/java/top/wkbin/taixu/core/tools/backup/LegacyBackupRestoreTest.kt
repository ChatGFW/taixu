package top.wkbin.taixu.core.tools.backup

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.BackupRecordPolicy
import top.wkbin.taixu.core.database.DatabaseBackupRepository
import top.wkbin.taixu.core.database.HarnessBlobStore
import top.wkbin.taixu.core.datastore.BackupPreferenceStore
import top.wkbin.taixu.core.model.*
import top.wkbin.taixu.runtime.AttachmentPaths
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LegacyBackupRestoreTest {
    @Test fun `old ZIP previews and restores with an unbound history marker`() = runBlocking {
        val temp = Files.createTempDirectory("legacy-backup").toFile()
        var prepared: PreparedBackup? = null
        val locations = BackupLocations(File(temp, "files"), File(temp, "files/attachments"),
            File(temp, "files/workspace"), File(temp, "cache"))
        val repository = MemoryRepository()
        val service = LocalBackupService(repository, EmptyPreferences, locations)
        try {
            val records = repository.records.copy(tables = repository.records.tables + ("harness_sessions" to listOf(
                buildJsonObject { put("id", "old-session"); put("title", "旧会话"); put("approvalMode", "assisted") }
            )))
            val archive = File(temp, "legacy.zip")
            BackupArchive.write(archive, LocalBackupManifest(createdAt = 1L, records = records,
                preferences = JsonObject(emptyMap()), assets = emptyList(), sourceFiles = locations.files.absolutePath,
                sourceAttachments = locations.attachments.absolutePath, sourceWorkspace = locations.workspace.absolutePath),
                File(temp, "assets"))
            val preview = archive.inputStream().use { service.preview(it) }
            prepared = preview
            assertEquals(1, preview.preview.counts["harness_sessions"])
            assertEquals(1, service.restore(preview, restorePreferences = false))
            val restored = repository.records.tables.getValue("harness_sessions").single()
            assertEquals("restore-required", restored.getValue("executionTargetJson").jsonPrimitive.content)
            assertEquals("旧会话", restored.getValue("title").jsonPrimitive.content)
            assertNotNull(repository.committedOperation)
            assertFalse(File(locations.files, "backup-state/pending.json").exists())
        } finally {
            service.discard(prepared)
            temp.deleteRecursively()
        }
    }

    @Test fun `legacy flat attachments land in the referencing session directory`() = runBlocking {
        val temp = Files.createTempDirectory("legacy-attach").toFile()
        var prepared: PreparedBackup? = null
        val filesDir = File(temp, "files")
        val locations = BackupLocations(filesDir, AttachmentPaths.global(filesDir),
            File(filesDir, "workspace"), File(temp, "cache"))
        val repository = MemoryRepository()
        val service = LocalBackupService(repository, EmptyPreferences, locations)
        try {
            val records = repository.records.copy(tables = repository.records.tables + mapOf(
                "harness_sessions" to listOf(buildJsonObject {
                    put("id", "s1"); put("title", "会话"); put("approvalMode", "assisted")
                }),
                "harness_entries" to listOf(buildJsonObject {
                    put("id", "e1"); put("sessionId", "s1"); put("parentId", JsonNull)
                    put("createdAt", 1L); put("entryType", "message"); put("customType", "user")
                    put("payloadJson", """{"text":"see /attachments/a.png"}""")
                }),
            ))
            val assetsDir = File(temp, "assets").apply { mkdirs() }
            val source = File(assetsDir, "attachments/a.png").apply {
                parentFile?.mkdirs()
                writeText("img")
            }
            val archive = File(temp, "legacy-attach.zip")
            BackupArchive.write(
                archive,
                LocalBackupManifest(
                    createdAt = 1L, records = records, preferences = JsonObject(emptyMap()),
                    assets = listOf(BackupAsset("attachments/a.png", source.length(), BackupArchive.digest(source))),
                    sourceFiles = locations.files.absolutePath,
                    sourceAttachments = locations.attachments.absolutePath,
                    sourceWorkspace = locations.workspace.absolutePath,
                ),
                assetsDir,
            )
            val preview = archive.inputStream().use { service.preview(it) }
            prepared = preview
            service.restore(preview, restorePreferences = false)

            assertTrue(File(AttachmentPaths.session(filesDir, "s1"), "a.png").isFile)
        } finally {
            service.discard(prepared)
            temp.deleteRecursively()
        }
    }

    @Test fun `blob attachment references and shared skill resources survive restore`() = runBlocking {
        val temp = Files.createTempDirectory("blob-skill-restore").toFile()
        val files = File(temp, "files")
        val locations = BackupLocations(files, AttachmentPaths.global(files), File(files, "workspace"), File(temp, "cache"))
        val repository = MemoryRepository()
        val service = LocalBackupService(repository, EmptyPreferences, locations)
        try {
            val records = repository.records.copy(tables = repository.records.tables + mapOf(
                "harness_sessions" to listOf(buildJsonObject { put("id", "s1"); put("title", "session") }),
                "harness_entries" to listOf(buildJsonObject {
                    put("id", "e1"); put("sessionId", "s1")
                    put("payloadJson", HarnessBlobStore.BLOB_PREFIX + "backup:harness_blobs/e1.json")
                }),
                "agent_skills" to listOf(buildJsonObject {
                    put("id", "skill1"); put("isBuiltin", 0)
                    put("resourcePath", "backup:skills/skill1")
                    put("systemPrompt", "run /attachments/__backup__/skills/skill1/run.sh")
                }),
            ))
            val archive = archive(temp, locations, records, mapOf(
                "attachments/photo.png" to "image",
                "harness_blobs/e1.json" to """{"text":"/attachments/photo.png"}""",
                "skills/skill1/run.sh" to "echo skill",
            ))
            service.restore(archive.inputStream().use { service.preview(it) }, false)
            assertEquals("image", File(AttachmentPaths.session(files, "s1"), "photo.png").readText())
            val skill = repository.records.tables.getValue("agent_skills").single()
            val host = File(skill.getValue("resourcePath").jsonPrimitive.content, "run.sh")
            assertEquals("echo skill", host.readText())
            val guest = skill.getValue("systemPrompt").jsonPrimitive.content.removePrefix("run /attachments/skills/")
            assertEquals(host.canonicalFile, File(AttachmentPaths.skills(files), guest).canonicalFile)
        } finally { temp.deleteRecursively() }
    }

    @Test fun `failed restore rolls back published attachments and retry restores complete bytes`() = runBlocking {
        val temp = Files.createTempDirectory("restore-rollback").toFile()
        val files = File(temp, "files")
        val locations = BackupLocations(files, AttachmentPaths.global(files), File(files, "workspace"), File(temp, "cache"))
        val repository = MemoryRepository()
        val service = LocalBackupService(repository, EmptyPreferences, locations)
        try {
            val archive = archive(temp, locations, repository.records, mapOf("attachments/sessions/s1/file.txt" to "complete"))
            repository.failAfterPrepare = true
            val first = archive.inputStream().use { service.preview(it) }
            assertTrue(runCatching { service.restore(first, false) }.isFailure)
            val target = File(AttachmentPaths.session(files, "s1"), "file.txt")
            assertFalse(target.exists())
            repository.failAfterPrepare = false
            service.restore(archive.inputStream().use { service.preview(it) }, false)
            assertEquals("complete", target.readText())
            target.writeText("damaged")
            assertTrue(runCatching { service.restore(archive.inputStream().use { service.preview(it) }, false) }.isFailure)
            assertEquals("damaged", target.readText())
        } finally { temp.deleteRecursively() }
    }

    private fun archive(temp: File, locations: BackupLocations, records: BackupRecords, contents: Map<String, String>): File {
        val assets = File(temp, "archive-assets").apply { mkdirs() }
        val manifestAssets = contents.map { (path, text) ->
            val source = File(assets, path).apply { parentFile!!.mkdirs(); writeText(text) }
            BackupAsset(path, source.length(), BackupArchive.digest(source))
        }
        return File(temp, "backup.zip").also { archive ->
            BackupArchive.write(archive, LocalBackupManifest(createdAt = 1L, records = records,
                preferences = JsonObject(emptyMap()), assets = manifestAssets, sourceFiles = locations.files.absolutePath,
                sourceAttachments = locations.attachments.absolutePath, sourceWorkspace = locations.workspace.absolutePath), assets)
        }
    }

    private class MemoryRepository : DatabaseBackupRepository {
        var failAfterPrepare = false
        var records = BackupRecords(BackupRecordPolicy.tables.associateWith { emptyList<JsonObject>() })
        var committedOperation: String? = null
        override suspend fun snapshot() = records
        override suspend fun validate(records: BackupRecords) {
            records.tables.getValue("harness_sessions").forEach {
                // The real Room validator requires the current schema before preview can continue.
                require("executionTargetJson" in it) { "备份字段与当前版本不兼容" }
            }
        }
        override suspend fun merge(expected: BackupRecords, records: BackupRecords, operationId: String,
            prepare: suspend () -> Unit): Int {
            check(this.records == expected)
            validate(records)
            prepare()
            check(!failAfterPrepare) { "simulated database commit failure" }
            this.records = records
            committedOperation = operationId
            return records.tables.values.sumOf { it.size }
        }
        override suspend fun committed(operationId: String) = committedOperation == operationId
    }

    private object EmptyPreferences : BackupPreferenceStore {
        override suspend fun snapshot() = JsonObject(emptyMap())
        override fun validate(values: JsonObject) = Unit
        override suspend fun replace(expected: JsonObject, values: JsonObject, operationId: String) = Unit
        override suspend fun rollback(operationId: String, applied: JsonObject, previous: JsonObject) = Unit
    }
}
