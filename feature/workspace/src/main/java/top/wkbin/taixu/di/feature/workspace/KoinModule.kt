package top.wkbin.taixu.di.feature.workspace

import org.koin.dsl.module
import org.koin.core.qualifier.named
import top.wkbin.taixu.harness.codex.WorkspaceCodexBackend
import top.wkbin.taixu.ui.workspace.WorkshopSettingsViewModel
import top.wkbin.taixu.ui.workspace.WorkshopSigningViewModel
import top.wkbin.taixu.ui.workspace.WorkshopSdkViewModel
import top.wkbin.taixu.ui.workspace.WorkspaceBuildTaskCoordinator
import top.wkbin.taixu.ui.workspace.WorkspaceEditorViewModel
import top.wkbin.taixu.ui.workspace.WorkspaceRunController
import top.wkbin.taixu.ui.workspace.WorkspaceRunViewModel
import top.wkbin.taixu.ui.workspace.WorkspaceViewModel
import top.wkbin.taixu.ui.workspace.codex.WorkspaceCodexThreadStore
import top.wkbin.taixu.ui.workspace.codex.WorkspaceCodexViewModel
import org.koin.core.module.dsl.viewModel

/** Dependency registrations owned by the feature:workspace module. */
val featureWorkspaceModule = module {
    viewModel { WorkshopSdkViewModel(get(), get()) }
    viewModel<WorkshopSettingsViewModel> {
        WorkshopSettingsViewModel(
            context = get(),
            preferences = get(),
            linuxRuntime = get(),
            pathManager = get(),
            assetSynchronizer = get(),
            buildScripts = get(),
            workspaceManager = get(),
        )
    }

    viewModel<WorkshopSigningViewModel> { WorkshopSigningViewModel(signingManager = get()) }
    single { WorkspaceRunController(get(), get()) }
    viewModel { WorkspaceRunViewModel(get(), get()) }
    viewModel { WorkspaceEditorViewModel(get(), get()) }
    single { WorkspaceCodexBackend(get(), get(), get(named("local-execution")), get()) }
    single { WorkspaceCodexThreadStore(get()) }
    viewModel { WorkspaceCodexViewModel(get(), get()) }

    single<WorkspaceBuildTaskCoordinator> {
        WorkspaceBuildTaskCoordinator(
            context = get(),
            runner = get(),
            notifier = get(),
            backgroundTaskRegistry = get(),
            workflowSignals = get(),
        )
    }

    viewModel<WorkspaceViewModel> {
        WorkspaceViewModel(
            context = get(),
            workspaceManager = get(),
            buildCoordinator = get(),
            toolManager = get(),
            linuxRuntime = get(),
            workshopPreferences = get(),
            projectTemplateStore = get(),
            firstUseGuidePreferences = get(),
        )
    }
}
