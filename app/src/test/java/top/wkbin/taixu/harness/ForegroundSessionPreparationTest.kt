package top.wkbin.taixu.harness

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.database.RoomHarnessSessionRepository
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.di.taiXuModule
import top.wkbin.taixu.harness.projection.SessionMessageProjector
import top.wkbin.taixu.runtime.FakeLinuxRuntime
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentFactory
import top.wkbin.taixu.testing.Aarch64AwareRobolectricTestRunner

@RunWith(Aarch64AwareRobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class ForegroundSessionPreparationTest {
    private lateinit var application: KoinApplication
    private lateinit var loop: HarnessLoop
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val persistenceStarted = CompletableDeferred<Unit>()
    private val persistenceGate = CompletableDeferred<Unit>()
    private var persistenceFailure = false

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.applicationInfo.nativeLibraryDir = context.filesDir.path
        application = koinApplication {
            androidContext(context)
            modules(taiXuModule, module {
                single {
                    Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                        .allowMainThreadQueries().build()
                }
                single<LinuxRuntime> { FakeLinuxRuntime() }
                single<AgentForegroundLauncher> {
                    object : AgentForegroundLauncher { override fun start(sessionId: String?) = Unit }
                }
                single { OkHttpClient.Builder().addInterceptor { error("Network is forbidden") }.build() }
                single<ExecutionEnvironmentFactory> {
                    ExecutionEnvironmentFactory { _, _, _ -> error("No environment should be opened") }
                }
                single<HarnessSessionRepository> {
                    val backing = RoomHarnessSessionRepository(get<AppDatabase>().harnessSessionDao())
                    object : HarnessSessionRepository by backing {
                        override suspend fun upsert(session: HarnessSessionEntity) {
                            if (session.title == "pending") {
                                persistenceStarted.complete(Unit)
                                persistenceGate.await()
                                if (persistenceFailure) error("Persistence failed")
                            }
                            backing.upsert(session)
                        }
                    }
                }
            })
        }
        loop = application.koin.get()
    }

    @After fun close() {
        scope.cancel()
        application.koin.get<AppDatabase>().close()
        application.close()
    }

    @Test fun `pending persistence keeps previous foreground and cannot override newer creation`() = runBlocking {
        val previous = loop.newSession("previous", "/previous")
        val pending = scope.async { loop.newSession("pending", "/pending") }
        withTimeout(5_000) { persistenceStarted.await() }
        assertEquals(previous, loop.currentSessionId.value)
        assertEquals("/previous", loop.workspace.value)
        val chosen = loop.newSession("chosen", "/chosen")
        persistenceGate.complete(Unit)
        val background = pending.await()
        assertNotNull(application.koin.get<HarnessSessionRepository>().findById(background))
        assertEquals(chosen, loop.currentSessionId.value)
        assertEquals("/chosen", loop.workspace.value)
    }

    @Test fun `cancelled initial creation never publishes an unpersisted id`() = runBlocking {
        val pending = scope.async { loop.newSession("pending") }
        withTimeout(5_000) { persistenceStarted.await() }
        assertEquals("", loop.currentSessionId.value)
        pending.cancelAndJoin()
        assertEquals("", loop.currentSessionId.value)
        assertTrue(application.koin.get<HarnessSessionRepository>().listAll().isEmpty())
    }

    @Test fun `failed creation retains previous foreground and its projection`() = runBlocking {
        val previous = loop.newSession("previous", "/previous")
        persistenceFailure = true
        persistenceGate.complete(Unit)
        assertTrue(runCatching { loop.newSession("pending", "/pending") }.isFailure)
        assertEquals(previous, loop.currentSessionId.value)
        assertEquals("/previous", loop.workspace.value)
    }

    @Test fun `editing a missing message cannot turn into a new send`() = runBlocking {
        val sessionId = loop.newSession("edit")
        loop.truncateAndResend("removed-message", "must not be sent", sessionId)
        withTimeout(5_000) {
            loop.sessionRunStates.first { it[sessionId] in setOf(SessionRunState.COMPLETED, SessionRunState.FAILED) }
        }
        assertEquals(SessionRunState.COMPLETED, loop.sessionRunStates.value[sessionId])
        assertTrue(loop.persistedMessages(sessionId).isEmpty())
    }

    @Test fun `editing an assistant message cannot truncate the conversation`() = runBlocking {
        val sessionId = loop.newSession("edit")
        val projector = application.koin.get<SessionMessageProjector>()
        projector.append(sessionId, UserMessage("user", 0, "original question"))
        projector.append(sessionId, AssistantText("assistant", 1, "existing answer"))
        loop.truncateAndResend("assistant", "must not be sent", sessionId)
        withTimeout(5_000) {
            loop.sessionRunStates.first { it[sessionId] in setOf(SessionRunState.COMPLETED, SessionRunState.FAILED) }
        }
        assertEquals(SessionRunState.COMPLETED, loop.sessionRunStates.value[sessionId])
        assertEquals(listOf("user", "assistant"), loop.persistedMessages(sessionId).map { it.id })
    }
}
