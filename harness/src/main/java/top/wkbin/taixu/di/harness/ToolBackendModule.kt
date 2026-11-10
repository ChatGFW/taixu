package top.wkbin.taixu.di.harness

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.dsl.module
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.repository.BotGroupSessionRepository
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.datastore.hashlinePreferences
import top.wkbin.taixu.core.security.SecretRedactor
import top.wkbin.taixu.harness.AskUserToolBackend
import top.wkbin.taixu.harness.ContextMemoryToolBackend
import top.wkbin.taixu.harness.DownloadToolBackend
import top.wkbin.taixu.harness.HarnessServiceToolBackend
import top.wkbin.taixu.harness.HostCapabilityToolBackend
import top.wkbin.taixu.harness.HostToolRequest
import top.wkbin.taixu.harness.LinuxCommandToolBackend
import top.wkbin.taixu.harness.PromptAssetToolBackend
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.harness.SearchWebToolBackend
import top.wkbin.taixu.harness.ToolExecutionRequest
import top.wkbin.taixu.harness.ToolExecutor
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.WorkspaceFileAccess
import top.wkbin.taixu.harness.WorkspaceMutationSnapshots
import top.wkbin.taixu.harness.WorkspaceToolBackend
import top.wkbin.taixu.harness.bot.BotCapabilityScope
import top.wkbin.taixu.harness.bot.RepositoryBotCapabilityScope
import top.wkbin.taixu.harness.core.ToolCheckpoints
import top.wkbin.taixu.harness.directory.CapabilityToolGateway
import top.wkbin.taixu.harness.environment.LocalExecutionEnvironmentFactory
import top.wkbin.taixu.harness.environment.SelectedExecutionEnvironmentFactory
import top.wkbin.taixu.harness.environment.SessionExecutionEnvironments
import top.wkbin.taixu.harness.environment.SessionExecutionTargets
import top.wkbin.taixu.harness.plugins.HarnessPluginRegistry
import top.wkbin.taixu.harness.session.SessionTitleGenerator
import top.wkbin.taixu.harness.session.SessionTitleLog
import top.wkbin.taixu.harness.session.SessionTitleModelPort
import top.wkbin.taixu.harness.text.HashlineEditStats
import top.wkbin.taixu.harness.text.HashlineEditStatsCodec
import top.wkbin.taixu.harness.text.HashlinePolicy
import top.wkbin.taixu.harness.websearch.BingSearchProvider
import top.wkbin.taixu.harness.websearch.WebSearchProviders
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentFactory
import top.wkbin.taixu.runtime.environment.LocalDistributionSelection

internal val toolBackendModule = module {
    single<ExecutionEnvironmentFactory>(org.koin.core.qualifier.named("local-execution")) {
        LocalExecutionEnvironmentFactory(get(), get(), get(), get(), get(), get())
    }
    single {
        val selection = get<LocalDistributionSelection>()
        SessionExecutionTargets(get(), get(), selection::capture)
    }
    single<ExecutionEnvironmentFactory> {
        SelectedExecutionEnvironmentFactory(
            get(org.koin.core.qualifier.named("local-execution")), get(), get(),
            get(),
        )
    }
    single { SessionExecutionEnvironments(get()) }
    single { get<HarnessPluginRegistry>().checkpoints() }
    single { WorkspaceMutationSnapshots(store = get(), events = get()) }
    single {
        val files = get<WorkspaceFileAccess>()
        val settings = get<SettingsDataStore>()
        val hashline = settings.hashlinePreferences()
        // 启动时水合累计计数；落盘走 IO scope，避免 edit 热路径阻塞 DataStore。
        val persistScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        runCatching {
            kotlinx.coroutines.runBlocking {
                val initial = HashlineEditStatsCodec.decode(
                    hashline.editStatsJson.first(),
                )
                HashlineEditStats.bind(initial) { snap ->
                    persistScope.launch {
                        runCatching {
                            hashline.setEditStatsJson(
                                HashlineEditStatsCodec.encode(snap),
                            )
                        }
                    }
                }
            }
        }
        WorkspaceToolBackend(
            operationsFor = { workspace -> if (workspace.isNotBlank()) files.withBase(workspace) else files },
            snapshots = get(),
            environments = get(),
            hashlinePolicy = {
                // 与 LinuxCommandToolBackend 超时读取同型：热路径同步取最新偏好，失败回落默认全开。
                runCatching {
                    kotlinx.coroutines.runBlocking {
                        HashlinePolicy(
                            readAnchorsDefault = hashline.readAnchorsDefault.first(),
                            editOpsEnabled = hashline.editOpsEnabled.first(),
                            legacyEditEnabled = hashline.legacyEditEnabled.first(),
                        )
                    }
                }.getOrDefault(HashlinePolicy.DEFAULT)
            },
        )
    }
    // 领域能力后端：各域独立持有自己的运行时依赖，ToolExecutor 只做管道编排与路由。
    single {
        HostCapabilityToolBackend(
            privilegeManager = get(),
            androidAppManager = get(),
            androidAppRepository = get(),
            shizukuApis = get(),
            hostGuiController = get(),
            virtualDisplayCoordinator = get(),
            virtualScreenToolkit = get(),
            providerClient = get(),
            settingsDataStore = get(),
            phoneAgentServices = get(),
            embeddedAdbManager = get(),
            secretRedactor = get(),
            eventBus = get(),
        )
    }
    single {
        LinuxCommandToolBackend(
            linuxRuntime = get(),
            pathResolver = get(),
            settingsDataStore = get(),
            workflowSignals = get(),
            environments = get(),
        )
    }
    single {
        DownloadToolBackend(
            fileDownloader = get(),
            fileAccess = get(),
            mutationSnapshots = get(),
            environments = get(),
        )
    }
    single {
        WebSearchProviders(
            listOf(BingSearchProvider(get<okhttp3.OkHttpClient>())),
        )
    }
    single {
        SearchWebToolBackend(providers = get())
    }
    single {
        ContextMemoryToolBackend(
            messageStore = get(),
            compactionManager = get(),
            sessionDao = get(),
            providerClient = get(),
        )
    }
    single {
        AskUserToolBackend(
            approvalPolicyEngine = get(),
            approvalRepository = get(),
        )
    }
    single {
        PromptAssetToolBackend(
            skillRepository = get(),
            promptRouter = get(),
        )
    }
    single {
        HarnessServiceToolBackend(
            contextExecutor = get(),
            subagentOrchestrator = get(),
            dualAgentCoordinator = get(),
            buildScriptToolExecutor = get(),
            blockSubagentInGroupMain = { sessionId, rawToolName ->
                val bot = get<PersonaBotRepository>().getBotBySessionId(sessionId)
                val groupId = get<BotGroupSessionRepository>().groupIdForSession(sessionId)
                if (bot?.isMainBot == true && groupId != null) {
                    "群聊主控不能用 ${rawToolName ?: "invoke_subagent"} 替代成员派单；" +
                        "请使用 bot-management 的 assign-task，将任务分别派给不同群成员（同一轮可多次调用，wait=false）。"
                } else {
                    null
                }
            },
        )
    }
    // 能力统一网关：use_capability 分发 + codemode 脚本装配 + legacy mcp__ 直连回退。
    single<BotCapabilityScope> {
        RepositoryBotCapabilityScope(bots = get(), servers = get(), assignments = get())
    }
    single {
        CapabilityToolGateway(
            mcpManager = get(),
            plugins = get(),
            argRedactor = { value: String -> get<SecretRedactor>().redact(value) },
            toolPolicy = get(),
            capabilityScope = get(),
            hostExecutor = { args, operationId, sessionId, metadata ->
                get<HostCapabilityToolBackend>().execute(HostToolRequest(args, operationId, sessionId, metadata))
            },
        )
    }
    // 自动会话标题：模型解析与补全走 ProviderClient 的窄端口（测试用 lambda 打桩）。
    single<SessionTitleModelPort> {
        val providerClient = get<ProviderClient>()
        SessionTitleModelPort { modelId, variant, messages ->
            providerClient.chat(providerClient.resolveConfigured(modelId, variant), messages).content
        }
    }
    single<SessionTitleLog> {
        val appLogger = get<AppLogger>()
        object : SessionTitleLog {
            override fun info(message: String) = appLogger.i(message)
            override fun error(message: String, failure: Throwable) = appLogger.e(message, failure)
        }
    }
    single {
        SessionTitleGenerator(
            sessions = get(),
            modelPort = get(),
            log = get(),
        )
    }
    single<ToolExecutor> {
        ToolExecutor(
            fileAccess = get(),
            pathResolver = get(),
            approvalPolicyEngine = get(),
            secretRedactor = get(),
            hostToolBackend = get(),
            linuxCommandToolBackend = get(),
            downloadToolBackend = get(),
            searchWebToolBackend = get(),
            contextMemoryToolBackend = get(),
            askUserToolBackend = get(),
            promptAssetToolBackend = get(),
            harnessServiceToolBackend = get(),
            capabilityToolGateway = get(),
            linuxEnvironmentManager = get(),
            approvalRepository = get(),
            sessionDao = get(),
            mcpManager = get(),
            mcpToolPolicy = get(),
            eventBus = get(),
            checkpointStore = get(),
            sessionApprovalGrants = get(),
            approvalAuditor = get(),
            settingsDataStore = get(),
            toolCheckpoints = get(),
            workspaceToolBackend = get(), governanceGuard = get(),
            environments = get(),
        )
    }

}
