package top.wkbin.taixu.webchat

import android.content.Context
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.*
import top.wkbin.taixu.core.database.task.*
import top.wkbin.taixu.harness.session.SessionControl
import top.wkbin.taixu.harness.task.AgentStateMachine
import top.wkbin.taixu.runtime.webchat.WebChatTaskState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class WebChatTaskObservationTest {
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected operation: ${method.name}") } as T
    private fun contract(block: suspend (TaiXuWebChatAgentGateway, AgentTaskRepository, AgentStateMachine) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val tasks = RoomAgentTaskRepository(db.agentTaskDao())
        val gateway = TaiXuWebChatAgentGateway(unused<SessionControl>(), unused(), unused(),
            AgentApprovalRepository(db.agentApprovalDao()), tasks)
        try { withTimeout(10_000) { block(gateway, tasks, AgentStateMachine(tasks)) } } finally { db.close() }
    }
    @Test fun completedTaskIsVisibleToSubscriberCreatedAfterAllTransitions() = contract { gateway, _, state ->
        state.createQueued("task", "s", "title", "input")
        assertTrue(state.markRunning("task"))
        assertTrue(state.markCompleted("task"))
        assertEquals(WebChatTaskState.COMPLETED, gateway.observeTask("s", "task").first())
    }
    @Test fun queuedRunObservesItsOwnTransitionsWhileOtherTaskFinishes() = contract { gateway, _, state ->
        state.createQueued("other", "s", "other", "other")
        state.markRunning("other")
        state.createQueued("task", "s", "queued", "queued")
        coroutineScope {
            val updates = gateway.observeTask("s", "task").produceIn(this)
            try {
                assertEquals(WebChatTaskState.QUEUED, updates.receive())
                state.markCompleted("other")
                assertEquals(WebChatTaskState.QUEUED, gateway.observeTask("s", "task").first())
                state.markRunning("task")
                assertEquals(WebChatTaskState.RUNNING, updates.receive())
                state.markCancelled("task")
                assertEquals(WebChatTaskState.CANCELLED, updates.receive())
            } finally { updates.cancel() }
        }
    }
    @Test fun missingCrossSessionAndUnknownTaskStatesFailClosed() = contract { gateway, tasks, state ->
        state.createQueued("foreign", "other-session", "title", "input")
        assertEquals(WebChatTaskState.MISSING, gateway.observeTask("s", "foreign").first())
        assertEquals(WebChatTaskState.MISSING, gateway.observeTask("s", "absent").first())
        val task = state.createQueued("legacy", "s", "title", "input")
        tasks.upsert(task.copy(status = "unrecognized-future-state"))
        assertEquals(WebChatTaskState.UNKNOWN, gateway.observeTask("s", "legacy").first())
    }
}
