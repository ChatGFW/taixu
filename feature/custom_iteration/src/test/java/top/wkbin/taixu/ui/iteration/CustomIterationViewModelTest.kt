package top.wkbin.taixu.ui.iteration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.iteration.engine.TaiXuDevBuildCoordinator
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.ShellCommand

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomIterationViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank branch is sent as main`() = runTest {
        val commands = mutableListOf<String>()
        val viewModel = viewModel { command ->
            commands += command.commandLine
            CommandResult(1, "", "stop", 0)
        }

        viewModel.startCloudBuild("   ")

        assertTrue(commands.first().contains("--branch 'main'"))
        assertTrue(commands.first().contains("-R 'wkbin/taixu'"))
        assertTrue(viewModel.buildState.value is TaiXuDevBuildCoordinator.BuildStatus.Failed)
    }

    @Test
    fun `injection in the branch becomes a failed state and does not run gh`() = runTest {
        val commands = mutableListOf<String>()
        val viewModel = viewModel { command ->
            commands += command.commandLine
            CommandResult(0, "", "", 0)
        }

        viewModel.startCloudBuild("main; rm -rf /")

        assertTrue(commands.isEmpty())
        val state = viewModel.buildState.value
        assertTrue(state is TaiXuDevBuildCoordinator.BuildStatus.Failed)
        assertTrue((state as TaiXuDevBuildCoordinator.BuildStatus.Failed).error.contains("分支名"))
    }

    @Test
    fun `a second trigger is ignored while the pipeline is in flight`() = runTest {
        val gate = CompletableDeferred<CommandResult>()
        val commands = mutableListOf<String>()
        val viewModel = viewModel { command ->
            commands += command.commandLine
            if (command.commandLine.startsWith("gh run list")) gate.await()
            CommandResult(1, "", "stop", 0)
        }

        viewModel.startCloudBuild("main")
        assertTrue(viewModel.buildState.value is TaiXuDevBuildCoordinator.BuildStatus.Dispatching)
        viewModel.startCloudBuild("feature/other")

        assertEquals(1, commands.count { it.startsWith("gh run list") })
        assertTrue(commands.none { it.contains("--branch 'feature/other'") })
        gate.complete(CommandResult(1, "", "stop", 0))
    }

    private fun viewModel(
        execute: suspend (ShellCommand) -> CommandResult,
    ) = CustomIterationViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        buildCoordinator = TaiXuDevBuildCoordinator(
            shellRunner = TaiXuDevBuildCoordinator.ShellRunner { execute(it) },
            sleep = {},
            clock = { 1_760_000_000_000L },
        ),
    )
}
