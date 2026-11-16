package top.wkbin.taixu.harness.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.harness.ApiMessage
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.core.model.ChunkedText

/** 自动会话标题：占位符判定、每会话一次尝试、标题清洗的契约测试（lambda 打桩，无网络）。 */
class SessionTitleGeneratorTest {

    private class RecordingSessions : HarnessSessionRepository {
        val renamed = mutableListOf<Pair<String, String>>()
        var title = "新会话"
        var modelId: String? = "profile-1"
        override suspend fun findById(id: String): HarnessSessionEntity = HarnessSessionEntity(
            id = id, title = title, createdAt = 1, updatedAt = 1, modelId = modelId,
        )
        override suspend fun rename(id: String, newTitle: String, updatedAt: Long) {
            renamed += id to newTitle; title = newTitle
        }
        override fun observeAll() = throw UnsupportedOperationException()
        override suspend fun upsert(session: HarnessSessionEntity) = throw UnsupportedOperationException()
        override suspend fun touch(id: String, updatedAt: Long) = throw UnsupportedOperationException()
        override suspend fun setPinned(id: String, pinnedAt: Long) = Unit
        override suspend fun setApprovalMode(id: String, approvalMode: String, updatedAt: Long) = throw UnsupportedOperationException()
        override suspend fun setApprovalModeForAll(approvalMode: String, updatedAt: Long) = throw UnsupportedOperationException()
        override suspend fun setRunMode(id: String, runMode: String, updatedAt: Long) = throw UnsupportedOperationException()
        override suspend fun setModelSelection(id: String, modelId: String?, modelVariant: String?, updatedAt: Long) = throw UnsupportedOperationException()
        override suspend fun deleteSession(id: String) = throw UnsupportedOperationException()
        override suspend fun countInRange(start: Long?, end: Long?): Int = throw UnsupportedOperationException()
        override suspend fun listAll(): List<HarnessSessionEntity> = throw UnsupportedOperationException()
    }

    private fun conversation(
        userText: String = "帮我写一个 PRoot 沙箱检测脚本",
        assistantText: String = "好的，脚本如下……",
    ) = listOf(
        UserMessage(id = "u1", createdAt = 1, text = userText),
        AssistantText(id = "a1", createdAt = 2, textContent = ChunkedText.of(assistantText)),
    )

    private fun generator(
        sessions: RecordingSessions,
        resolve: (modelId: String?) -> String? = { it },
        reply: String = "沙箱检测脚本编写",
        fail: Boolean = false,
    ): Pair<SessionTitleGenerator, MutableList<List<ApiMessage>>> {
        val calls = mutableListOf<List<ApiMessage>>()
        val gen = SessionTitleGenerator(
            sessions = sessions,
            modelPort = SessionTitleModelPort { modelId, _, messages, _ ->
                checkNotNull(resolve(modelId)) { "no model" }
                calls += messages
                if (fail) throw IllegalStateException("boom")
                reply
            },
            log = object : SessionTitleLog {
                override fun info(message: String) = Unit
                override fun error(message: String, failure: Throwable) = Unit
            },
        )
        return gen to calls
    }

    @Test
    fun `renames placeholder title after first turn`() = runTest {
        val sessions = RecordingSessions()
        val (gen, calls) = generator(sessions)
        gen.maybeGenerate("s1", conversation())
        assertEquals(listOf("s1" to "沙箱检测脚本编写"), sessions.renamed)
        assertEquals(1, calls.size)
        assertTrue(calls[0][0].content.orEmpty().contains("标题生成器"))
        assertTrue(calls[0][1].content.orEmpty().contains("PRoot"))
    }

    @Test
    fun `attempts only once per session even after failure`() = runTest {
        val sessions = RecordingSessions()
        val (gen, calls) = generator(sessions, fail = true)
        gen.maybeGenerate("s1", conversation())
        gen.maybeGenerate("s1", conversation())
        assertEquals(1, calls.size)
        assertEquals(0, sessions.renamed.size)
    }

    @Test
    fun `never touches sessions with a real title`() = runTest {
        val sessions = RecordingSessions().apply { title = "我的项目会话" }
        val (gen, calls) = generator(sessions)
        gen.maybeGenerate("s1", conversation())
        assertEquals(0, calls.size)
        assertEquals(0, sessions.renamed.size)
    }

    @Test
    fun `skips when model unbound or turn incomplete`() = runTest {
        val unbound = RecordingSessions().apply { modelId = null }
        val (genUnbound, callsUnbound) = generator(unbound, resolve = { null })
        genUnbound.maybeGenerate("s1", conversation())
        assertEquals(0, callsUnbound.size)

        val noReply = RecordingSessions()
        val (genNoReply, callsNoReply) = generator(noReply)
        genNoReply.maybeGenerate("s2", listOf(UserMessage(id = "u1", createdAt = 1, text = "只有用户消息")))
        assertEquals(0, callsNoReply.size)
    }

    @Test
    fun `sanitizes quotes periods and truncates long replies`() = runTest {
        val sessions = RecordingSessions()
        val (gen, _) = generator(sessions, reply = "「这是一个非常非常非常非常非常非常非常长的标题远远超过二十四个字的限制」。")
        gen.maybeGenerate("s1", conversation())
        val title = sessions.renamed.single().second
        assertTrue(title.length <= SessionTitleGenerator.MAX_TITLE_CHARS)
        assertTrue(title.startsWith("这是一个"))
    }

    @Test
    fun `prompt and model overrides reach the port`() = runTest {
        val sessions = RecordingSessions()
        val calls = mutableListOf<List<ApiMessage>>()
        val gen = SessionTitleGenerator(
            sessions = sessions,
            modelPort = SessionTitleModelPort { modelId, variant, messages, sessionId ->
                assertEquals("title-model", modelId)
                assertEquals(null, variant)
                assertEquals("s1", sessionId)
                calls += messages
                "自定义提示词标题"
            },
            log = object : SessionTitleLog {
                override fun info(message: String) = Unit
                override fun error(message: String, failure: Throwable) = Unit
            },
        )
        gen.maybeGenerate("s1", conversation(), promptOverride = "用一句话概括主题。", modelIdOverride = "title-model")
        assertEquals(listOf("s1" to "自定义提示词标题"), sessions.renamed)
        val userPrompt = calls[0][1].content.orEmpty()
        assertTrue(userPrompt.contains("用一句话概括主题。"))
        assertTrue(userPrompt.contains("根据下面这轮对话").not())
    }

    @Test
    fun `defaults keep builtin prompt and session model`() = runTest {
        val sessions = RecordingSessions()
        val (gen, calls) = generator(sessions)
        gen.maybeGenerate("s1", conversation())
        assertTrue(calls[0][1].content.orEmpty().contains("根据下面这轮对话"))
    }
}
