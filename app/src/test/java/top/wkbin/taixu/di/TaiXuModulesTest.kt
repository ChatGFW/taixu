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
import top.wkbin.taixu.harness.directory.CapabilityToolGateway
import top.wkbin.taixu.harness.subagent.SubagentLaneRunner
import top.wkbin.taixu.runtime.browser.tools.BrowserMcpTools
import top.wkbin.taixu.runtime.browser.tools.BrowserMcpResources
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import kotlinx.coroutines.Job
import top.wkbin.taixu.core.browser.BrowserPreferences
import top.wkbin.taixu.core.security.SecretRedactor
import top.wkbin.taixu.core.tools.backup.BackupLocations
import top.wkbin.taixu.harness.projection.CurrentSessionTracker
import top.wkbin.taixu.harness.diagnostics.RequestDiagnosticsStore
import top.wkbin.taixu.harness.workflow.VirtualScreenWorkflowRuns
import top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskRegistry
import top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskState

@OptIn(KoinExperimentalAPI::class)
class TaiXuModulesTest {
    @Test
    fun completeApplicationGraphHasAllConstructorDependencies() {
        taiXuModule.verify(
            // These arguments are assembled explicitly by provider lambdas, not container lookups.
            injections = injectedParameters(
                definition<WorkspaceFileAccess>(File::class),
                definition<WorkspaceToolBackend>(Function1::class),
                definition<CapabilityToolGateway>(Function5::class),
                definition<ToolCheckpoints<*, *>>(List::class),
                definition<BackupLocations>(File::class),
                definition<top.wkbin.taixu.core.tools.PluginReceiptStore>(File::class),
                definition<top.wkbin.taixu.harness.plugins.HarnessPluginRegistry>(List::class, Function0::class),
                // Bot plugin factories construct their immutable descriptors; these are not DI services.
                definition<top.wkbin.taixu.harness.plugins.HarnessPlugin>(top.wkbin.taixu.plugin.api.PluginDescriptor::class),
                definition<top.wkbin.taixu.harness.plugins.ExternalHarnessPlugins>(Function2::class, top.wkbin.taixu.harness.mcp.McpTransport::class),
                definition<top.wkbin.taixu.core.tools.InstalledPluginCatalog>(Function0::class, kotlinx.coroutines.CoroutineScope::class),
                definition<HttpClient>(HttpClientEngine::class),
                definition<OkHttpClient>(OkHttpClient.Builder::class),
                definition<SubagentLaneRunner>(Function0::class),
                definition<BrowserMcpTools>(List::class, Function1::class, BrowserPreferences::class),
                definition<BrowserMcpResources>(Function0::class),
                definition<top.wkbin.taixu.runtime.bridge.FullAccessGrant>(kotlinx.coroutines.flow.Flow::class, kotlinx.coroutines.CoroutineScope::class, Function0::class),
                // These callbacks/files are built by explicit provider lambdas or secondary constructors.
                definition<top.wkbin.taixu.runtime.SessionWorkspaceCatalog>(Function0::class, Function2::class),
                definition<top.wkbin.taixu.harness.environment.SessionExecutionTargets>(Function1::class),
                definition<top.wkbin.taixu.runtime.environment.LocalDistributionSelection>(kotlinx.coroutines.flow.Flow::class, kotlinx.coroutines.flow.StateFlow::class, kotlinx.coroutines.CoroutineScope::class),
                definition<top.wkbin.taixu.harness.mcp.StandaloneMcpScope>(Function0::class, kotlinx.coroutines.flow.Flow::class),
                definition<top.wkbin.taixu.harness.mcp.McpStdioTransport>(kotlinx.coroutines.flow.Flow::class, Function0::class),
                definition<top.wkbin.taixu.core.datastore.McpClientPreferences>(androidx.datastore.core.DataStore::class),
                definition<top.wkbin.taixu.core.datastore.McpToolPolicyPreferences>(androidx.datastore.core.DataStore::class),
                definition<top.wkbin.taixu.core.datastore.ChatBackgroundPreferences>(androidx.datastore.core.DataStore::class),
                definition<top.wkbin.taixu.core.datastore.ChatBackgroundImageStore>(File::class, Function1::class, Long::class),
                definition<top.wkbin.taixu.core.datastore.PluginPreferences>(androidx.datastore.core.DataStore::class),
                definition<top.wkbin.taixu.core.datastore.ContainerBackendPreferences>(androidx.datastore.core.DataStore::class),
                definition<top.wkbin.taixu.core.datastore.EncryptedRemoteServerRepository>(android.util.AtomicFile::class, Function1::class),
                // The build coordinator's adapter and default callbacks are constructed by its lambda.
                definition<top.wkbin.taixu.iteration.engine.TaiXuDevBuildCoordinator>(
                    top.wkbin.taixu.iteration.engine.TaiXuDevBuildCoordinator.ShellRunner::class, Function2::class, Function0::class, String::class,
                ),
                definition<top.wkbin.taixu.ui.iteration.CustomIterationViewModel>(
                    Function0::class, Function1::class, top.wkbin.taixu.iteration.engine.AssetReader::class,
                ),
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
            val job = Job()
            runs.begin("test", job)
            runs.end("test", PhoneTaskState.COMPLETED)
            assertSame(registry, application.koin.get<PhoneTaskRegistry>())
            job.cancel()
        } finally { application.close() }
    }
}
