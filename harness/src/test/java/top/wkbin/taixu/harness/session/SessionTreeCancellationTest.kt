package top.wkbin.taixu.harness.session

import java.lang.reflect.Proxy
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import top.wkbin.taixu.core.database.HarnessRuntimeRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionTreeCancellationTest {
    @Test
    fun cancelledRepositoryReadsDoNotBecomeEmptyHistory() = runBlocking {
        val cancellation = CancellationException("repository read cancelled")
        val repository = Proxy.newProxyInstance(
            HarnessRuntimeRepository::class.java.classLoader,
            arrayOf(HarnessRuntimeRepository::class.java),
        ) { _, _, _ -> throw cancellation } as HarnessRuntimeRepository
        val logger = AppLogger(ApplicationProvider.getApplicationContext(), SensitiveDataRedactor { it })
        val store = SessionTreeStore(repository, Json, logger)
        val reads: List<suspend () -> Any?> = listOf(
            { store.load("session") },
            { store.loadAt("session", "leaf") },
            { store.loadStrict("session") },
            { store.laneLeafId("session") },
            { store.appendRecallBlock("session", "user", "recall") },
        )
        for (read in reads) {
            try {
                read()
                fail("Cancellation was converted to a fallback result")
            } catch (actual: CancellationException) {
                assertSame(cancellation, actual)
            }
        }
    }

    @Test fun strictHistoryPropagatesStorageFailureThroughTheMessageProjector() = runBlocking {
        val failure = java.io.IOException("storage read failed")
        for (failedRead in listOf("findLane", "branchTail")) {
            val unused = Proxy.newProxyInstance(HarnessRuntimeRepository::class.java.classLoader,
                arrayOf(HarnessRuntimeRepository::class.java)) { _, method, _ ->
                error("Unexpected storage call: ${method.name}")
            } as HarnessRuntimeRepository
            val repository = object : HarnessRuntimeRepository by unused {
                override suspend fun findLane(sessionId: String, laneName: String): top.wkbin.taixu.core.database.HarnessLaneEntity? {
                    if (failedRead == "findLane") throw failure
                    return top.wkbin.taixu.core.database.HarnessLaneEntity("session", "main", "leaf", updatedAt = 1)
                }
                override suspend fun branchTail(sessionId: String, leafId: String?, limit: Int): List<top.wkbin.taixu.core.database.HarnessEntryEntity> =
                    throw failure
            }
            val logger = AppLogger(ApplicationProvider.getApplicationContext(), SensitiveDataRedactor { it })
            val store = SessionTreeStore(repository, Json, logger)
            val projector = top.wkbin.taixu.harness.projection.SessionMessageProjector(store,
                top.wkbin.taixu.harness.projection.CurrentSessionTracker())
            try { projector.loadPersistedHistory("session"); fail("must propagate") }
            catch (caught: java.io.IOException) { org.junit.Assert.assertEquals(failure.message, caught.message) }
        }
    }

    @Test fun strictHistoryOfMissingSessionIsReadOnlyAndEmpty() = runBlocking {
        val repository = Proxy.newProxyInstance(HarnessRuntimeRepository::class.java.classLoader,
            arrayOf(HarnessRuntimeRepository::class.java)) { _, method, _ ->
            org.junit.Assert.assertEquals("findLane", method.name)
            null
        } as HarnessRuntimeRepository
        val logger = AppLogger(ApplicationProvider.getApplicationContext(), SensitiveDataRedactor { it })
        org.junit.Assert.assertTrue(SessionTreeStore(repository, Json, logger).loadStrict("missing").isEmpty())
    }

    @Test fun strictHistoryDoesNotDropCorruptOrUnreadableBlobMessages() = runBlocking {
        for (payload in listOf("invalid-json", "@@TAIXU_BLOB@@:missing.json")) {
            val repository = Proxy.newProxyInstance(HarnessRuntimeRepository::class.java.classLoader,
                arrayOf(HarnessRuntimeRepository::class.java)) { _, method, _ -> when (method.name) {
                "findLane" -> top.wkbin.taixu.core.database.HarnessLaneEntity("session", "main", "leaf", updatedAt = 1)
                "branchTail" -> listOf(
                    top.wkbin.taixu.core.database.HarnessEntryEntity(id = "state", sessionId = "session", parentId = null,
                        createdAt = 1, entryType = "custom", payloadJson = "state-is-not-a-message"),
                    top.wkbin.taixu.core.database.HarnessEntryEntity(id = "leaf", sessionId = "session", parentId = "state",
                        createdAt = 2, entryType = "message", payloadJson = payload))
                else -> error("Unexpected storage call: ${method.name}")
            } } as HarnessRuntimeRepository
            val logger = AppLogger(ApplicationProvider.getApplicationContext(), SensitiveDataRedactor { it })
            try { SessionTreeStore(repository, Json, logger).loadStrict("session"); fail("must not drop unreadable message") }
            catch (_: kotlinx.serialization.SerializationException) { }
        }
    }

    @Test fun strictHistoryCannotTurnLateStorageFailureIntoSuccessAfterCancellation() = runBlocking {
        val request = async {
            val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]!!
            val unused = Proxy.newProxyInstance(
                HarnessRuntimeRepository::class.java.classLoader, arrayOf(HarnessRuntimeRepository::class.java)) {
                    _, _, _ -> error("unused")
                } as HarnessRuntimeRepository
            val repository = object : HarnessRuntimeRepository by unused {
                override suspend fun findLane(sessionId: String, laneName: String): top.wkbin.taixu.core.database.HarnessLaneEntity? =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        job.cancel(); throw java.io.IOException("late failure")
                    }
            }
            val logger = AppLogger(ApplicationProvider.getApplicationContext(), SensitiveDataRedactor { it })
            SessionTreeStore(repository, Json, logger).loadStrict("session")
        }
        try { request.await(); fail("must cancel") } catch (_: CancellationException) { }
    }
}
