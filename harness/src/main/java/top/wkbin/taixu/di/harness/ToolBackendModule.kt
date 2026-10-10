package top.wkbin.taixu.di.harness

import org.koin.dsl.module
import top.wkbin.taixu.harness.ToolExecutor
import top.wkbin.taixu.harness.ToolExecutionRequest
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.WorkspaceFileAccess
import top.wkbin.taixu.harness.WorkspaceToolBackend
import top.wkbin.taixu.harness.WorkspaceMutationSnapshots
import top.wkbin.taixu.harness.core.ToolCheckpoints

internal val toolBackendModule = module {
    single { ToolCheckpoints<ToolExecutionRequest, ToolResult>() }
    single { WorkspaceMutationSnapshots(store = get(), events = get()) }
    single {
        val files = get<WorkspaceFileAccess>()
        WorkspaceToolBackend(
            operationsFor = { workspace -> if (workspace.isNotBlank()) files.withBase(workspace) else files },
            snapshots = get(),
        )
    }
    single<ToolExecutor> {
        ToolExecutor(
            fileAccess = get(),
            linuxRuntime = get(),
            pathResolver = get(),
            approvalPolicyEngine = get(),
            secretRedactor = get(),
            fileDownloader = get(),
            linuxEnvironmentManager = get(),
            approvalRepository = get(),
            sessionDao = get(),
            subagentOrchestrator = get(),
            mcpManager = get(),
            contextExecutor = get(),
            messageStore = get(),
            eventBus = get(),
            privilegeManager = get(),
            androidAppManager = get(),
            androidAppRepository = get(),
            shizukuApis = get(),
            hostGuiController = get(),
            virtualDisplayCoordinator = get(),
            virtualScreenToolkit = get(),
            buildScriptToolExecutor = get(),
            promptRouter = get(),
            checkpointStore = get(),
            dualAgentCoordinator = get(),
            embeddedAdbManager = get(),
            workflowSignals = get(),
            sessionApprovalGrants = get(),
            compactionManager = get(),
            providerClient = get(),
            skillRepository = get(),
            settingsDataStore = get(),
            phoneAgentServices = get(),
            toolCheckpoints = get(),
            workspaceToolBackend = get(),
        )
    }

}
