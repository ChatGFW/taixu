package top.wkbin.taixu.harness.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.RoomHarnessRuntimeRepository
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.UserMessage
import android.content.Context

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaneBranchProjectionTest {
    @Test
    fun resumedSubagentPreservesTaskTitleAndToolCount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = RoomHarnessRuntimeRepository(database.harnessRuntimeDao())
            val store = SessionTreeStore(repository, Json, AppLogger(context, SensitiveDataRedactor { it }))
            val manager = LaneManager(repository, store)
            val lane = "subagent:research:resume"
            store.append("session", UserMessage("parent", 1L, "父任务"))
            manager.create("session", lane, "parent")
            store.append("session", UserMessage("task", 2L, "【子智能体任务指派】\n任务目标：调查问题"), lane)
            store.append("session", top.wkbin.taixu.harness.ToolCall("read", 3L,
                top.wkbin.taixu.harness.HarnessTool.READ, kotlinx.serialization.json.JsonObject(emptyMap())), lane)
            store.append("session", top.wkbin.taixu.harness.ToolResult("result", 4L, "read", true, "已读取"), lane)
            store.append("session", UserMessage("resume", 5L, "继续"), lane)

            val child = manager.branches("session").single { it.laneName == lane }
            assertEquals("调查问题 · research", child.name)
            assertEquals(1, child.toolCallCount)
            assertEquals(4, child.depth)
            assertEquals(listOf("task", "read", "result", "resume"), manager.subagentTranscript("session", lane).map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun sharedAncestorsPreserveNearestNamedLaneAndBranchPreviews() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = RoomHarnessRuntimeRepository(database.harnessRuntimeDao())
            val store = SessionTreeStore(repository, Json, AppLogger(context, SensitiveDataRedactor { it }))
            val manager = LaneManager(repository, store)
            store.append("session", UserMessage("root", 1L, "common context"))
            manager.create("session", "branch:outer:outer", "root")
            store.append("session", AssistantText("middle", 2L, "shared reply"), "branch:outer:outer")
            manager.create("session", "branch:inner:inner", "middle")
            store.append("session", AssistantText("inner-leaf", 3L, "inner preview"), "branch:inner:inner")
            manager.create("session", "subagent:research:one", "middle")
            store.append("session", UserMessage("task", 4L, "任务目标：调查问题"), "subagent:research:one")
            store.append("session", AssistantText("child-leaf", 5L, "child preview"), "subagent:research:one")

            val branches = manager.branches("session")
            assertTrue(branches.first().isCurrent)
            val inner = branches.single { it.leafId == "inner-leaf" }
            assertEquals("branch:inner:inner", inner.laneName)
            assertEquals("inner preview", inner.preview)
            assertEquals(3, inner.depth)
            val child = branches.single { it.leafId == "child-leaf" }
            assertEquals(ConversationBranchKind.SUBAGENT, child.kind)
            assertEquals("调查问题 · research", child.name)
            assertEquals("child preview", child.preview)
            assertEquals(2, child.depth)
        } finally {
            database.close()
        }
    }
}
