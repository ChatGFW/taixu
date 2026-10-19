package top.wkbin.taixu.di.harness

import org.koin.core.qualifier.named
import org.koin.dsl.module
import top.wkbin.taixu.core.database.repository.BotScheduleRepository
import top.wkbin.taixu.core.database.repository.GroupChatRepository
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.harness.bot.BotAssignmentTracker
import top.wkbin.taixu.harness.bot.BotOrchestrator
import top.wkbin.taixu.harness.bot.archive.BotPackageService
import top.wkbin.taixu.harness.bot.diagnostics.BotDiagnosticsExporter
import top.wkbin.taixu.harness.bot.market.BotMarketRepository
import top.wkbin.taixu.harness.bot.memory.BotMemoryManager
import top.wkbin.taixu.harness.bot.schedule.BotScheduledSubmission
import top.wkbin.taixu.harness.bot.schedule.BotScheduleRunner
import top.wkbin.taixu.harness.bot.schedule.BotScheduleWakeup
import top.wkbin.taixu.harness.groupchat.GroupChatDispatcher
import top.wkbin.taixu.harness.groupchat.BotAttachmentPreparer
import top.wkbin.taixu.harness.plugins.HarnessPlugin
import top.wkbin.taixu.harness.plugins.botManagementPlugin
import top.wkbin.taixu.harness.plugins.botMemoryPlugin
import top.wkbin.taixu.harness.plugins.botSchedulePlugin

/** PersonaBot：独立会话、Git 记忆、群聊调度与定时唤醒。 */
val botHarnessModule = module {
    single { BotMemoryManager(pathManager = get(), logger = get()) }
    single { BotAttachmentPreparer(context = get(), paths = get()) }
    single { BotAssignmentTracker(repository = get()) }
    single {
        BotOrchestrator(
            botRepository = get<PersonaBotRepository>(),
            groupRepository = get<GroupChatRepository>(),
            groupSessionRepository = get(),
            sessionRepository = get(),
            sessionControl = get(),
            laneRunner = get(),
            memoryManager = get(),
            logger = get(),
        )
    }
    single {
        GroupChatDispatcher(
            groupRepository = get<GroupChatRepository>(),
            botRepository = get<PersonaBotRepository>(),
            attachmentPreparer = get(),
            orchestrator = get(),
            sessionControl = get(),
            logger = get(),
        )
    }
    single<BotScheduledSubmission> {
        BotScheduledSubmission { bot, instruction, group -> get<GroupChatDispatcher>().triggerScheduled(bot, instruction, group) }
    }
    single { BotScheduleRunner(scheduleRepository = get(), botRepository = get(), logger = get(), submit = get()) }
    single { BotPackageService(botRepository = get<PersonaBotRepository>(), orchestrator = get(), memoryManager = get(), json = get(), logger = get()) }
    single {
        BotDiagnosticsExporter(
            groupRepository = get<GroupChatRepository>(),
            assignmentRepository = get(),
            groupSessionRepository = get(),
            sessionControl = get(),
            runtimeRepository = get(),
            json = get(),
            logger = get(),
        )
    }
    single { BotMarketRepository(orchestrator = get(), subagentRepository = get()) }
    single<HarnessPlugin>(named("taixu.bot-memory")) { botMemoryPlugin(get<PersonaBotRepository>(), get()) }
    single<HarnessPlugin>(named("taixu.bot-management")) {
        botManagementPlugin(
            botRepository = get<PersonaBotRepository>(),
            groupRepository = get<GroupChatRepository>(),
            orchestrator = { get() },
            dispatcher = { get() },
            assignments = get(),
            logger = get(),
        )
    }
    single<HarnessPlugin>(named("taixu.bot-schedule")) {
        botSchedulePlugin(
            botRepository = get<PersonaBotRepository>(),
            groupRepository = get<GroupChatRepository>(),
            scheduleRepository = get<BotScheduleRepository>(),
            wakeup = { getOrNull<BotScheduleWakeup>() },
            logger = get(),
        )
    }
}
