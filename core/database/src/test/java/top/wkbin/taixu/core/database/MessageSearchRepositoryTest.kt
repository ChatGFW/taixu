package top.wkbin.taixu.core.database

import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MessageSearchRepositoryTest {
    private var arguments: List<Any?>? = null
    private var failure: Throwable? = null
    private val dao = Proxy.newProxyInstance(HarnessRuntimeDao::class.java.classLoader,
        arrayOf(HarnessRuntimeDao::class.java)) { _, method, args ->
        check(method.name == "searchMessagesAcrossSessions") { "Unexpected DAO call: ${method.name}" }
        failure?.let { throw it }
        arguments = args!!.take(3)
        emptyList<MessageSearchHitRow>()
    } as HarnessRuntimeDao
    private val repository = RoomHarnessRuntimeRepository(dao)

    @Test fun `repository passes distinct raw term and literal SQL pattern`() = runBlocking {
        repository.searchMessages("  100%_\\path  ", 20)
        assertEquals(listOf("100%_\\path", "100\\%\\_\\\\path", 20), arguments)
    }
    @Test fun `blank query never reaches storage and negative limit is bounded`() = runBlocking {
        assertTrue(repository.searchMessages("   ").isEmpty())
        assertNull(arguments)
        repository.searchMessages("word", -1)
        assertEquals(0, arguments!!.last())
    }
    @Test fun `storage failure and cancellation propagate to the query owner`() = runBlocking {
        failure = IllegalStateException("storage")
        try { repository.searchMessages("word"); fail("must propagate") } catch (_: IllegalStateException) { }
        failure = CancellationException("cancelled")
        try { repository.searchMessages("word"); fail("must propagate") } catch (_: CancellationException) { }
    }
}
