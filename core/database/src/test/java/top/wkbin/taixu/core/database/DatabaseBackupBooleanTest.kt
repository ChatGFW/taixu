package top.wkbin.taixu.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that JsonPrimitive(boolean) values in backup data are correctly
 * validated and stored as SQLite integers (1/0) rather than being silently
 * written as NULL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseBackupBooleanTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomDatabaseBackupRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = RoomDatabaseBackupRepository(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun `boolean JsonPrimitive passes INTEGER column validation`() = runBlocking {
        // Create a minimal backup with a harness_operations row using JsonPrimitive(true)
        val rows = listOf(buildJsonObject {
            put("id", JsonPrimitive(1))
            put("operationId", JsonPrimitive("op-bool"))
            put("table", JsonPrimitive("harness_sessions"))
            put("recordKey", JsonPrimitive("hk-bool"))
            put("direction", JsonPrimitive(1))
            put("status", JsonPrimitive(0))
            put("faulted", JsonPrimitive(false))  // boolean, not integer!
            put("payload", JsonPrimitive("{}"))
            put("startedAt", JsonPrimitive(0L))
            put("finishedAt", JsonPrimitive(0L))
            put("sequence", JsonPrimitive(1L))
        })
        val backup = BackupRecords(mapOf("harness_operations" to rows))

        // Should NOT throw "备份字段类型无效"
        assertDoesNotThrow { repo.validate(backup) }
    }

    @Test
    fun `boolean true is stored as 1 in SQLite`() = runBlocking {
        // Insert a session first so harness_operations row is valid
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO harness_sessions(id, title, createdAt, updatedAt) VALUES('s1', 'test', 0, 0)"
        )
        val rows = listOf(buildJsonObject {
            put("id", JsonPrimitive(1))
            put("operationId", JsonPrimitive("op-bool-2"))
            put("table", JsonPrimitive("harness_sessions"))
            put("recordKey", JsonPrimitive("hk-bool-2"))
            put("direction", JsonPrimitive(1))
            put("status", JsonPrimitive(0))
            put("faulted", JsonPrimitive(true))  // boolean true
            put("payload", JsonPrimitive("{}"))
            put("startedAt", JsonPrimitive(0L))
            put("finishedAt", JsonPrimitive(0L))
            put("sequence", JsonPrimitive(1L))
        })
        val expected = repo.snapshot()
        val records = BackupRecords(mapOf("harness_operations" to rows))
        repo.merge(expected, records, "op-bool-2") {}

        // Verify the faulted column was stored as 1, not NULL
        db.openHelper.writableDatabase.rawQuery(
            "SELECT faulted FROM harness_operations WHERE operationId='op-bool-2'", null
        ).use { cursor ->
            assertTrue("Row not found", cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))  // true → 1, NOT NULL
        }
    }

    @Test
    fun `boolean false is stored as 0 in SQLite`() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO harness_sessions(id, title, createdAt, updatedAt) VALUES('s2', 'test2', 0, 0)"
        )
        val rows = listOf(buildJsonObject {
            put("id", JsonPrimitive(2))
            put("operationId", JsonPrimitive("op-bool-3"))
            put("table", JsonPrimitive("harness_sessions"))
            put("recordKey", JsonPrimitive("hk-bool-3"))
            put("direction", JsonPrimitive(1))
            put("status", JsonPrimitive(0))
            put("faulted", JsonPrimitive(false))  // boolean false
            put("payload", JsonPrimitive("{}"))
            put("startedAt", JsonPrimitive(0L))
            put("finishedAt", JsonPrimitive(0L))
            put("sequence", JsonPrimitive(2L))
        })
        val expected = repo.snapshot()
        val records = BackupRecords(mapOf("harness_operations" to rows))
        repo.merge(expected, records, "op-bool-3") {}

        db.openHelper.writableDatabase.rawQuery(
            "SELECT faulted FROM harness_operations WHERE operationId='op-bool-3'", null
        ).use { cursor ->
            assertTrue("Row not found", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))  // false → 0
        }
    }
}
