package top.wkbin.taixu.core.database

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨会话消息全文搜索的 SQL 契约。与 [HarnessDailyAggregateSqlTest] 同因：
 * Robolectric 自带 SQLite 未编 JSON1，无法执行 Room 的 json_extract 查询，
 * 这里用带 JSON1 的 sqlite-jdbc 执行与 `HarnessBranchQueries.searchMessagesAcrossSessions`
 * 同一个 SQL 常量，避免测试副本与生产查询漂移。
 */
class MessageSearchSqlTest {

    @Test
    fun `hits user and assistant text across sessions and excludes tool payloads`() {
        connection().use { conn ->
            insert(conn, "m1", "sessionA", "user", """{"type":"user","text":"太墟支持 PRoot 沙箱"}""", 10, sessionTitle = "会话甲")
            insert(conn, "m2", "sessionB", "assistant", """{"type":"assistant","text":"这里是关于 PRoot 的说明"}""", 20, sessionTitle = "会话乙")
            insert(conn, "m3", "sessionA", "tool_call", """{"type":"tool_call","args":{"cmd":"PRoot 出现在工具参数里"}}""", 30)
            val hits = search(conn, "PRoot")
            assertEquals(2, hits.size)
            // 最新优先
            assertEquals("sessionB", hits[0][0])
            assertEquals(0, (hits[0][3] as Number).toInt())
            assertEquals("会话甲", hits[1][1])
            assertEquals(1, (hits[1][3] as Number).toInt())
            // excerpt 窗口包含命中词
            assertTrue(hits.all { (it[5] as String).contains("PRoot") })
        }
    }

    @Test
    fun `like wildcards must be escaped by the caller`() {
        connection().use { conn ->
            insert(conn, "m1", "s", "user", """{"type":"user","text":"一百分之百匹配任意词"}""", 1)
            // 转义约定与 RoomHarnessRuntimeRepository.searchMessages 相同
            assertEquals(0, search(conn, "%").size)
            assertEquals(0, search(conn, "_").size)
            assertEquals(1, search(conn, "百分之百").size)
        }
    }

    @Test
    fun `long text excerpt windows around the match`() {
        connection().use { conn ->
            val text = "前".repeat(500) + "关键词出现" + "后".repeat(500)
            insert(conn, "m1", "s", "user", """{"type":"user","text":"$text"}""", 1)
            val hits = search(conn, "关键词")
            assertEquals(1, hits.size)
            val excerpt = hits[0][5] as String
            assertTrue(excerpt.contains("关键词"))
            assertTrue(excerpt.length <= 240)
        }
    }

    @Test
    fun `limit keeps newest first`() {
        connection().use { conn ->
            (1..10).forEach { i -> insert(conn, "m$i", "s", "user", """{"type":"user","text":"正文命中$i"}""", i.toLong()) }
            val hits = search(conn, "正文", limit = 3)
            assertEquals(3, hits.size)
            assertEquals(10L, (hits[0][4] as Number).toLong())
            assertEquals(8L, (hits[2][4] as Number).toLong())
        }
    }

    @Test fun `blob placeholders malformed json and non text fields do not hide valid hits`() {
        connection().use { conn ->
            insert(conn, "valid", "s", "assistant", """{"text":"valid target"}""", 1)
            for ((index, body) in listOf("@@TAIXU_BLOB@@:harness_blobs/a", "{bad", "null", "{\"text\":123}", "{\"text\":null}").withIndex()) {
                insert(conn, "bad-$index", "s", "user", body, 2L + index)
            }
            assertEquals("valid", search(conn, "target").single()[2])
            assertTrue(search(conn, "123").isEmpty())
        }
    }

    @Test fun `literal percent underscore and backslash locate excerpt around actual match`() {
        connection().use { conn ->
            val text = "前".repeat(500) + "100%_done\\path" + "后".repeat(500)
            val body = "{\"text\":\"" + text.replace("\\", "\\\\") + "\"}"
            insert(conn, "literal", "s", "user", body, 1)
            for (query in listOf("100%", "_done", "\\path")) {
                val excerpt = search(conn, query).single()[5] as String
                assertTrue(query, excerpt.contains(query))
                assertTrue(excerpt.length <= 220)
            }
        }
    }

    @Test fun `ascii case insensitive match has correctly centered excerpt`() {
        connection().use { conn ->
            insert(conn, "case", "s", "assistant", """{"text":"${"x".repeat(500)}UPPERCASE target${"y".repeat(500)}"}""", 1)
            assertTrue((search(conn, "uppercase").single()[5] as String).contains("UPPERCASE"))
        }
    }

    @Test fun `equal timestamps have stable order and zero limit returns no hits`() {
        connection().use { conn ->
            for (id in listOf("a", "c", "b")) insert(conn, id, "s", "user", """{"text":"same"}""", 1)
            assertEquals(listOf("c", "b", "a"), search(conn, "same").map { it[2] })
            assertTrue(search(conn, "same", 0).isEmpty())
        }
    }

    private fun connection() = DriverManager.getConnection("jdbc:sqlite::memory:").also { conn ->
        conn.createStatement().use { stmt ->
            SCHEMA.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach(stmt::execute)
        }
    }

    private fun insert(
        conn: java.sql.Connection,
        id: String,
        sessionId: String,
        customType: String,
        payload: String,
        createdAt: Long,
        sessionTitle: String? = null,
    ) {
        if (sessionTitle != null) {
            conn.prepareStatement("INSERT INTO harness_sessions(id, title) VALUES (?,?)").use {
                it.setString(1, sessionId); it.setString(2, sessionTitle); it.executeUpdate()
            }
        }
        conn.prepareStatement(
            "INSERT INTO harness_entries(id, sessionId, createdAt, entryType, customType, payloadJson) VALUES (?,?,?,?,?,?)",
        ).use { stmt ->
            stmt.setString(1, id); stmt.setString(2, sessionId); stmt.setLong(3, createdAt)
            stmt.setString(4, "message"); stmt.setString(5, customType); stmt.setString(6, payload)
            stmt.executeUpdate()
        }
    }

    private fun search(conn: java.sql.Connection, query: String, limit: Int = 30): List<List<Any?>> =
        conn.prepareStatement(SEARCH_SQL).use { stmt ->
            stmt.setString(1, query); stmt.setString(2, ESCAPE(query)); stmt.setInt(3, limit)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add((1..6).map { rs.getObject(it) })
                }
            }
        }

    private companion object {
        /** LIKE 转义约定与 RoomHarnessRuntimeRepository.searchMessages 逐字一致。 */
        private val ESCAPE: (String) -> String = { q ->
            q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        }

        private val SCHEMA = """
            CREATE TABLE harness_entries (
                sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                id TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                parentId TEXT,
                createdAt INTEGER NOT NULL,
                entryType TEXT NOT NULL,
                customType TEXT,
                payloadJson TEXT NOT NULL
            );
            CREATE TABLE harness_sessions (id TEXT PRIMARY KEY, title TEXT);
        """.trimIndent()

        private val SEARCH_SQL = MESSAGE_SEARCH_SQL
            .replace(":query", "?").replace(":pattern", "?").replace(":limit", "?")
    }
}
