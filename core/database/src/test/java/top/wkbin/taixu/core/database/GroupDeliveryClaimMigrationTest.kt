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
class GroupDeliveryClaimMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun `88 to 89 adds nullable claim nonce without rewriting receipts`() {
        val path = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("group-delivery-claim-migration.db").absolutePath
        helper.createDatabase(path, 88).close()
        helper.runMigrationsAndValidate(path, 89, true, MIGRATION_88_89).use { db ->
            db.query("PRAGMA table_info(`group_deliveries`)").use { row ->
                val columns = mutableListOf<String>()
                while (row.moveToNext()) columns += row.getString(1)
                assertTrue(columns.contains("claimNonce"))
            }
        }
    }
}
