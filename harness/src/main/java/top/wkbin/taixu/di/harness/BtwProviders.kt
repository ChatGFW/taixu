package top.wkbin.taixu.di.harness

import org.koin.dsl.module
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.harness.btw.BtwModelCaller
import top.wkbin.taixu.harness.btw.BtwModelResolver
import top.wkbin.taixu.harness.btw.BtwService
import top.wkbin.taixu.harness.btw.BtwSessionLookup
import top.wkbin.taixu.harness.btw.BtwTranscriptSource
import top.wkbin.taixu.harness.btw.BtwUsageSink
import top.wkbin.taixu.harness.compaction.CompactionSteward
import top.wkbin.taixu.harness.compaction.StewardJudgeCaller
import top.wkbin.taixu.harness.projection.SessionStateMirrors
import top.wkbin.taixu.harness.session.ApiContextAssembler

/** /btw 旁路问答与上下文管家的 harness 装配（从 KoinModule 抽出以控制其文件尺寸）。 */
val harnessStewardProviders = module {
    single<StewardJudgeCaller> {
        StewardJudgeCaller { judgeModel, messages ->
            get<ProviderClient>().chat(judgeModel, messages).content.orEmpty()
        }
    }
    single<CompactionSteward> { CompactionSteward(caller = get()) }

    single<BtwSessionLookup> {
        BtwSessionLookup { sessionId -> get<HarnessSessionRepository>().findById(sessionId) }
    }
    single<BtwModelResolver> {
        BtwModelResolver { session -> get<ProviderClient>().resolveConfigured(session.modelId, session.modelVariant) }
    }
    // transcript 端口按调用时解析 ApiContextAssembler（factory：每次旁路问答取新实例，
    // 与主循环组装同口径，共享字节稳定契约）。
    single<BtwTranscriptSource> {
        BtwTranscriptSource { session, model ->
            get<ApiContextAssembler>().assemble(
                sessId = session.id,
                model = model,
                workspacePath = session.workspace,
                projectTypeOverride = session.projectType,
                thinkingMode = get<SessionStateMirrors>().requestThinkingMode(session.id),
                sessionRunMode = session.runMode,
            )
        }
    }
    single<BtwModelCaller> {
        BtwModelCaller { model, messages, sessionKey, text ->
            get<ProviderClient>().chatStream(model, messages, onReasoning = {}, sessionKey = sessionKey) { text.append(it) }
        }
    }
    single<BtwUsageSink> {
        BtwUsageSink { usage -> get<HarnessRuntimeRepository>().recordUsage(usage) }
    }
    factory<BtwService> {
        BtwService(
            sessions = get(),
            modelResolver = get(),
            transcriptSource = get(),
            modelCaller = get(),
            usageSink = get(),
            repository = get(),
            json = get(),
        )
    }
}
