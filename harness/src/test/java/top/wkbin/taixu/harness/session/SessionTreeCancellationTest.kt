package top.wkbin.taixu.harness.session

import java.lang.reflect.Proxy
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
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
}
