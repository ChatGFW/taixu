package top.wkbin.taixu.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import java.io.File
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import okhttp3.OkHttpClient
import top.wkbin.taixu.harness.WorkspaceFileAccess
import top.wkbin.taixu.harness.WorkspaceToolBackend
import top.wkbin.taixu.harness.core.ToolCheckpoints
import top.wkbin.taixu.harness.subagent.SubagentLaneRunner
import top.wkbin.taixu.runtime.browser.tools.BrowserMcpTools
import top.wkbin.taixu.runtime.browser.tools.BrowserMcpResources
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import top.wkbin.taixu.core.security.SecretRedactor
import top.wkbin.taixu.harness.projection.CurrentSessionTracker
import top.wkbin.taixu.harness.diagnostics.RequestDiagnosticsStore
import top.wkbin.taixu.harness.workflow.VirtualScreenWorkflowRuns
import top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskRegistry

@OptIn(KoinExperimentalAPI::class)
class TaiXuModulesTest {
    @Test
    fun completeApplicationGraphHasAllConstructorDependencies() {
        taiXuModule.verify(
            // These arguments are assembled explicitly by provider lambdas, not container lookups.
            injections = injectedParameters(
                definition<WorkspaceFileAccess>(File::class),
                definition<WorkspaceToolBackend>(Function1::class),
                definition<ToolCheckpoints<*, *>>(List::class),
                definition<top.wkbin.taixu.core.tools.backup.BackupLocations>(File::class),
                definition<HttpClient>(HttpClientEngine::class),
                definition<OkHttpClient>(OkHttpClient.Builder::class),
                definition<SubagentLaneRunner>(Function0::class),
                definition<BrowserMcpTools>(List::class, Function1::class, top.wkbin.taixu.core.browser.BrowserPreferences::class),
                definition<BrowserMcpResources>(Function0::class),
            ),
            extraTypes = listOf(Context::class, Application::class, SavedStateHandle::class, WorkerParameters::class),
        )
    }

    @Test
    fun graphLoadsWithoutOverridesAndPreservesSharedSingletons() {
        val application = koinApplication {
            allowOverride(false)
            modules(taiXuModule)
        }
        try {
            val koin = application.koin
            assertSame(koin.get<SecretRedactor>(), koin.get<SensitiveDataRedactor>())
            assertSame(koin.get<CurrentSessionTracker>(), koin.get<CurrentSessionTracker>())
            assertSame(koin.get<RequestDiagnosticsStore>(), koin.get<RequestDiagnosticsStore>())
        } finally {
            application.close()
        }
    }

    @Test
    fun phoneWorkflowControlsResolveTheSharedRegistry() {
        val registry = PhoneTaskRegistry()
        val application = koinApplication {
            modules(taiXuModule, org.koin.dsl.module { single<PhoneTaskRegistry> { registry } })
        }
        try {
            val runs = application.koin.get<VirtualScreenWorkflowRuns>()
            assertSame(runs, application.koin.get<VirtualScreenWorkflowRuns>())
            val job = kotlinx.coroutines.Job()
            runs.begin("test", job)
            runs.end("test", top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskState.COMPLETED)
            assertSame(registry, application.koin.get<PhoneTaskRegistry>())
            job.cancel()
        } finally { application.close() }
    }
}
