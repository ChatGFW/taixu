package top.wkbin.taixu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueuedPromptEditMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun `86 to 87 preserves queued payload and starts without edit reservations`() {
        val path = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("queue-edit-migration.db").absolutePath
        helper.createDatabase(path, 86).use { db ->
            db.execSQL("INSERT INTO harness_queue_items (id, sessionId, laneName, operationId, queueType, createdAt, payloadJson) VALUES ('q', 's', 'main', NULL, 'next_run', 42, 'original')")
        }
        helper.runMigrationsAndValidate(path, 87, true, MIGRATION_86_87).use { db ->
            db.query("SELECT id, createdAt, payloadJson, editingToken FROM harness_queue_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("q", cursor.getString(0))
                assertEquals(42L, cursor.getLong(1))
                assertEquals("original", cursor.getString(2))
                assertTrue(cursor.isNull(3))
            }
        }
    }
}
