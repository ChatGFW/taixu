package top.wkbin.taixu.ui.chat

import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.harness.session.InteractiveSessionControl

class ChatSessionStartupTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val current = MutableStateFlow("")
    private var available = emptyList<HarnessSessionEntity>()
    private var listingGate: CompletableDeferred<Unit>? = null
    private var creationGate: CompletableDeferred<Unit>? = null
    private var listingFailure = false
    private var observations = 0
    private val loads = mutableListOf<String>()
    private val creations = mutableListOf<String>()
    private val sessions = object : HarnessSessionRepository by unused(HarnessSessionRepository::class.java) {
        override fun observeAll(): Flow<List<HarnessSessionEntity>> = flow {
            observations++
            listingGate?.await()
            if (listingFailure) error("database unavailable")
            emit(available)
        }
    }
    private val control = object : InteractiveSessionControl by unused(InteractiveSessionControl::class.java) {
        override val currentSessionId = current
        override suspend fun loadSession(id: String) { loads += id; current.value = id }
        override suspend fun newSession(title: String, workspace: String, projectType: String): String {
            creations += title
            creationGate?.await()
            current.value = "created"
            return "created"
        }
    }
    private val startup = ChatSessionStartup(sessions, control)

    @After fun close() { scope.cancel() }

    private fun session(id: String, updatedAt: Long = 1, pinnedAt: Long = 0) =
        HarnessSessionEntity(id, id, 0, updatedAt, null, pinnedAt = pinnedAt)

    @Test fun `an existing foreground selection is preserved without reading storage`() = runBlocking {
        current.value = "existing"
        startup.restore("draft", "New")
        assertEquals("existing", current.value)
        assertEquals(0, observations)
        assertTrue(loads.isEmpty())
        assertTrue(creations.isEmpty())
    }

    @Test fun `saved draft owner wins over a newer pinned conversation`() = runBlocking {
        available = listOf(session("pinned", 100, 100), session("draft", 1))
        startup.restore("draft", "New")
        assertEquals(listOf("draft"), loads)
    }

    @Test fun `deleted draft owner falls back to most recently updated conversation`() = runBlocking {
        available = listOf(session("pinned", 1, 100), session("recent", 20))
        startup.restore("deleted", "New")
        assertEquals(listOf("recent"), loads)
        assertTrue(creations.isEmpty())
    }

    @Test fun `simultaneous chat surfaces create one initial conversation`() = runBlocking {
        creationGate = CompletableDeferred()
        val first = scope.launch { startup.restore("", "New") }
        val second = scope.launch { startup.restore("", "New") }
        assertEquals(listOf("New"), creations)
        creationGate!!.complete(Unit)
        joinAll(first, second)
        assertEquals(1, observations)
        assertEquals(1, creations.size)
        assertEquals("created", current.value)
    }

    @Test fun `manual selection cancels slow initialization before publication`() = runBlocking {
        listingGate = CompletableDeferred()
        available = listOf(session("automatic"))
        val restore = scope.launch { startup.restore("", "New") }
        startup.select(scope) { control.loadSession("manual") }.join()
        listingGate!!.complete(Unit)
        restore.join()
        assertTrue(restore.isCancelled)
        assertEquals(listOf("manual"), loads)
        assertEquals("manual", current.value)
    }

    @Test fun `explicit creation takes priority while foreground id is still empty`() = runBlocking {
        creationGate = CompletableDeferred()
        val manual = startup.select(scope) { control.newSession("Manual") }
        startup.restore("", "Automatic")
        assertEquals(0, observations)
        assertEquals(listOf("Manual"), creations)
        creationGate!!.complete(Unit)
        manual.join()
    }

    @Test fun `cancelled explicit action releases startup suppression`() = runBlocking {
        val manual = startup.select(scope) { awaitCancellation() }
        manual.cancelAndJoin()
        available = listOf(session("restored"))
        startup.restore("", "New")
        assertEquals(listOf("restored"), loads)
    }

    @Test fun `inactive navigation scope does not suppress later initialization`() = runBlocking {
        val inactive = CoroutineScope(Job().also { it.cancel() })
        startup.select(inactive) { error("must not run") }.join()
        available = listOf(session("restored"))
        startup.restore("", "New")
        assertEquals(listOf("restored"), loads)
    }

    @Test fun `external selection during storage read is preserved`() = runBlocking {
        listingGate = CompletableDeferred()
        available = listOf(session("automatic"))
        val restore = scope.launch { startup.restore("", "New") }
        current.value = "external"
        listingGate!!.complete(Unit)
        restore.join()
        assertTrue(loads.isEmpty())
        assertEquals("external", current.value)
    }

    @Test fun `failed storage read releases mutex and permits retry`() = runBlocking {
        listingFailure = true
        assertTrue(runCatching { startup.restore("", "New") }.isFailure)
        listingFailure = false
        available = listOf(session("retry"))
        startup.restore("", "New")
        assertEquals(listOf("retry"), loads)
    }

    @Test fun `manual navigation cancels empty database bootstrap waiting on creation`() = runBlocking {
        creationGate = CompletableDeferred()
        val restore = scope.launch { startup.restore("", "Automatic") }
        startup.select(scope) { control.loadSession("manual") }.join()
        creationGate!!.complete(Unit)
        restore.join()
        assertTrue(restore.isCancelled)
        assertEquals("manual", current.value)
        assertEquals(listOf("manual"), loads)
    }

    private fun <T : Any> unused(type: Class<T>): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            error("Unexpected call ${method.name}")
        },
    )
}
