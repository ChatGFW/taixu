package top.wkbin.taixu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BotInboxMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun `91 to 92 creates the inbox projection table and indexes`() {
        val path = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("bot-inbox-migration.db").absolutePath
        helper.createDatabase(path, 91).close()
        helper.runMigrationsAndValidate(path, 92, true, MIGRATION_91_92).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='bot_inbox_items'").use {
                assertTrue(it.moveToFirst())
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' " +
                    "AND name='index_bot_inbox_items_botId_sourceKind_sourceId'",
            ).use { assertTrue(it.moveToFirst()) }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' " +
                    "AND name='index_bot_inbox_items_botId_state_createdAt'",
            ).use { assertTrue(it.moveToFirst()) }
        }
    }
}
