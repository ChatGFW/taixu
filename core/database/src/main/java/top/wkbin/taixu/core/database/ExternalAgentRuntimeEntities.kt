package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "external_agent_runtime_tokens",
    foreignKeys = [
        ForeignKey(
            entity = BotEntity::class,
            parentColumns = ["id"],
            childColumns = ["botId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["botId"]), Index(value = ["tokenHash"], unique = true)],
)
data class ExternalAgentRuntimeTokenEntity(
    @PrimaryKey val id: String,
    val botId: String,
    val label: String,
    val tokenHash: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val revokedAt: Long? = null,
)

@Dao
interface ExternalAgentRuntimeTokenDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(token: ExternalAgentRuntimeTokenEntity)

    @Query("SELECT * FROM external_agent_runtime_tokens WHERE tokenHash = :tokenHash LIMIT 1")
    suspend fun findByHash(tokenHash: String): ExternalAgentRuntimeTokenEntity?

    @Query("SELECT * FROM external_agent_runtime_tokens WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ExternalAgentRuntimeTokenEntity?

    @Query("SELECT * FROM external_agent_runtime_tokens WHERE botId = :botId ORDER BY createdAt DESC")
    suspend fun listForBot(botId: String): List<ExternalAgentRuntimeTokenEntity>

    @Query("UPDATE external_agent_runtime_tokens SET revokedAt = :revokedAt WHERE id = :id AND revokedAt IS NULL")
    suspend fun revoke(id: String, revokedAt: Long): Int

    @Query("UPDATE external_agent_runtime_tokens SET revokedAt = :revokedAt WHERE botId = :botId AND revokedAt IS NULL")
    suspend fun revokeAllForBot(botId: String, revokedAt: Long): Int
}

val MIGRATION_89_90 = object : Migration(89, 90) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE persona_bots ADD COLUMN runtimeMode TEXT NOT NULL DEFAULT 'LOCAL'")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `external_agent_runtime_tokens` (
                `id` TEXT NOT NULL,
                `botId` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `tokenHash` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `expiresAt` INTEGER,
                `revokedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`botId`) REFERENCES `persona_bots`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_external_agent_runtime_tokens_botId` ON `external_agent_runtime_tokens` (`botId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_external_agent_runtime_tokens_tokenHash` ON `external_agent_runtime_tokens` (`tokenHash`)")
    }
}
