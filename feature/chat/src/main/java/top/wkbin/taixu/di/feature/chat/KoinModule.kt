package top.wkbin.taixu.di.feature.chat

import org.koin.dsl.module
import top.wkbin.taixu.ui.chat.ChatViewModel
import top.wkbin.taixu.ui.chat.ToolchainViewModel
import top.wkbin.taixu.runtime.doctor.ToolchainInspector
import top.wkbin.taixu.runtime.doctor.ToolchainInstaller
import org.koin.core.module.dsl.viewModel

/** Dependency registrations owned by the feature:chat module. */
val featureChatModule = module {
    // 沙箱工具链面板：单例 ViewModel，避免重复触发沙箱探测
    viewModel<ToolchainViewModel> {
        ToolchainViewModel(inspector = get(), installer = get())
    }

    viewModel<ChatViewModel> {
        ChatViewModel(
            context = get(),
            savedStateHandle = get(),
            harnessLoop = get(),
            requestDiagnostics = get(),
            systemPromptBuilder = get(),
            sessionDao = get(),
            aiModelDao = get(),
            workspaceManager = get(),
            settingsDataStore = get(),
            linuxRuntime = get(),
            terminalSessionManager = get(),
            mcpManager = get(),
            agentSkillRepository = get(),
            mcpServerRepository = get(),
            approvalRepository = get(),
            agentContextDao = get(),
            compactionManager = get(),
            sessionModelSwitcher = get(),
            quickPhraseRepository = get(),
            laneManager = get(),
            eventBus = get(),
            proactiveWorkflowAdvisor = get(),
            modelDiscovery = get(),
            providerCatalog = get(),
            providerRepository = get(),
            profileWriter = get(),
            privilegeManager = get(),
            pathManager = get(),
            workflowRepository = get(),
            translationManager = getOrNull(),
            globalNavigationBus = getOrNull(),
        )
    }
}
