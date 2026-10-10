package top.wkbin.taixu.di

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.viewmodel.factory.KoinViewModelFactory
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.WorkflowRepository
import top.wkbin.taixu.core.database.WorkflowScheduleStore
import top.wkbin.taixu.harness.workflow.WorkflowRunManager
import top.wkbin.taixu.harness.workflow.WorkflowScheduler
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.ui.iteration.CustomIterationViewModel
import top.wkbin.taixu.workflow.WorkflowScheduleWorker

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class KoinAndroidIntegrationTest {
    @After
    fun tearDown() = stopKoin()

    @OptIn(KoinInternalApi::class)
    @Test
    fun viewModelIdentityFollowsItsOwnerAndSurvivesProviderRecreation() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val application = startKoin {
            allowOverride(false)
            androidContext(context)
            modules(taiXuModule)
        }
        val firstOwner = ViewModelStore()
        val secondOwner = ViewModelStore()
        fun resolve(store: ViewModelStore): CustomIterationViewModel {
            val factory = KoinViewModelFactory(
                CustomIterationViewModel::class,
                application.koin.scopeRegistry.rootScope,
            )
            return ViewModelProvider.create(store, factory)[CustomIterationViewModel::class]
        }
        try {
            val first = resolve(firstOwner)
            assertSame(first, resolve(firstOwner))
            assertNotSame(first, resolve(secondOwner))
            assertSame(context, first.getApplication<Application>())
            firstOwner.clear()
            assertNotSame(first, resolve(firstOwner))
        } finally {
            firstOwner.clear()
            secondOwner.clear()
        }
    }

    @Test
    fun systemFactoryCreatesFreshWorkersSharingTheProcessManager() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val application = startKoin {
            androidContext(context)
            modules(taiXuModule, module {
                // Exercise the real worker and manager registrations without starting Linux or Room.
                single<LinuxRuntime> { unusedPort() }
                single<WorkflowRepository> { unusedPort() }
                single<WorkflowScheduleStore> { unusedPort() }
                single { WorkflowScheduler(emptySet(), get()) }
            })
        }
        val manager = application.koin.get<WorkflowRunManager>()
        val factory = KoinWorkerFactory()
        fun worker() = TestListenableWorkerBuilder<WorkflowScheduleWorker>(context)
            .setWorkerFactory(factory)
            .build()

        val first = worker()
        val second = worker()
        assertNotSame(first, second)
        assertSame(context, first.applicationContext)
        val managerField = WorkflowScheduleWorker::class.java.getDeclaredField("runManager").apply {
            isAccessible = true
        }
        assertSame(manager, managerField.get(first))
        assertSame(manager, managerField.get(second))
        // No schedule id: validates the actual worker is usable without dispatching any work.
        assertEquals(ListenableWorker.Result.failure(), first.doWork())
    }

    private inline fun <reified T> unusedPort(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("Unexpected external operation: ${method.name}") } as T

    @Test
    fun sessionEntryPointsResolveOneRuntimeAndRejectMissingRemoteSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.applicationInfo.nativeLibraryDir = context.filesDir.absolutePath
        val application = startKoin { androidContext(context); modules(taiXuModule) }
        val koin = application.koin
        try {
            val loop = koin.get<top.wkbin.taixu.harness.HarnessLoop>()
            val control = koin.get<top.wkbin.taixu.harness.session.SessionControl>()
            assertSame(loop, control)
            assertSame(loop, koin.get<top.wkbin.taixu.harness.session.InteractiveSessionControl>())
            val foreground = control.currentSessionId.value
            assertEquals(top.wkbin.taixu.harness.session.PromptSubmission.Rejected(
                top.wkbin.taixu.harness.session.PromptSubmission.Rejection.SESSION_NOT_FOUND),
                control.submit("missing-remote", "hello"))
            assertEquals(foreground, control.currentSessionId.value)
        } finally {
            koin.get<top.wkbin.taixu.core.database.AppDatabase>().close()
        }
    }
}
