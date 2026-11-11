package top.wkbin.taixu.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.core.tools.AiProfileWriter
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.model.McpConnectionState
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.core.model.RunningSendMode
import top.wkbin.taixu.core.database.AiModelRepository
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.core.database.MessageSearchHitRow
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.database.AgentSkillRepository
import top.wkbin.taixu.core.database.McpServerRepository
import top.wkbin.taixu.core.database.AgentApprovalRepository
import top.wkbin.taixu.core.database.AgentApprovalRequestEntity
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.harness.session.InteractiveSessionControl
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.SkillSuggestion
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.PendingMessage
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.ContextWindowPolicy
import top.wkbin.taixu.harness.compaction.CompactedContext
import top.wkbin.taixu.harness.ToolCallMode
import top.wkbin.taixu.harness.prompt.SystemPromptBuilder
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.events.HarnessEventBus
import top.wkbin.taixu.harness.workflow.ProactiveWorkflowAdvisor
import top.wkbin.taixu.harness.workflow.ProactiveWorkflowSuggestion
import top.wkbin.taixu.harness.mcp.McpManager
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.harness.session.ConversationBranchKind
import top.wkbin.taixu.harness.session.LaneManager
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.core.tools.AgentModelDiscovery
import top.wkbin.taixu.core.tools.AgentProviderCatalog
import top.wkbin.taixu.core.tools.ProviderEndpointPolicy
import top.wkbin.taixu.core.tools.ProviderRepository
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.wkbin.taixu.feature.chat.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.wkbin.taixu.runtime.terminal.TerminalSessionManager
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import top.wkbin.taixu.core.common.navigation.AppNavigationTarget
import top.wkbin.taixu.core.common.navigation.GlobalNavigationBus
import top.wkbin.taixu.core.common.translation.TranslationManager
import top.wkbin.taixu.core.database.AgentContextRepository
import top.wkbin.taixu.core.database.AgentMemoryEntity
import top.wkbin.taixu.core.database.AgentPlanEntity
import top.wkbin.taixu.core.database.AgentScratchpadEntity
import top.wkbin.taixu.core.database.QuickPhraseRepository
import top.wkbin.taixu.core.database.WorkflowRepository
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.model.InstalledDistro
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.QuickPhrase
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.harness.MentionExtractor
import top.wkbin.taixu.harness.checkpoint.RewindScope
import top.wkbin.taixu.harness.compaction.CompactionManager
import top.wkbin.taixu.harness.compaction.CompactionSnapshot
import top.wkbin.taixu.harness.diagnostics.RequestDiagnosticsStore
import top.wkbin.taixu.harness.mcp.McpWorkspaceRecommender
import top.wkbin.taixu.harness.session.SessionModelSwitcher
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.harness.ContextMemoryToolBackend
import top.wkbin.taixu.harness.environment.SessionExecutionEnvironments
import top.wkbin.taixu.harness.operation.OperationCoordinator
import top.wkbin.taixu.harness.session.SessionTitleGenerator
import top.wkbin.taixu.runtime.SessionWorkspaceCatalog
import top.wkbin.taixu.runtime.SessionWorkspaceCatalogState

internal fun mergeHistoricalAndLiveEvents(
    sessionId: String,
    messages: List<HarnessMessage>,
    live: List<HarnessEvent>,
): List<HarnessEvent> {
    if (sessionId.isBlank()) return emptyList()
    if (live.isEmpty()) return synthesizeHistoricalEvents(sessionId, messages)
    if (messages.isEmpty()) return live

    val liveEntryIds = mutableSetOf<String>()
    live.forEach { event ->
        when (event) {
            is HarnessEvent.ProviderRoundSettled -> event.entryId?.let { liveEntryIds.add(it) }
            is HarnessEvent.ToolCallStarted -> liveEntryIds.add(event.toolCallId)
            is HarnessEvent.ToolCallSettled -> liveEntryIds.add(event.toolCallId)
            else -> Unit
        }
    }

    val liveMinTimestamp = live.minOfOrNull { it.timestamp } ?: Long.MAX_VALUE
    val priorMessages = messages.filter { msg ->
        msg.createdAt < liveMinTimestamp && !liveEntryIds.contains(msg.id)
    }

    if (priorMessages.isEmpty()) return live

    val priorEvents = synthesizeHistoricalEvents(sessionId, priorMessages)
    return priorEvents + live
}
private fun synthesizeHistoricalEvents(sessionId: String, messages: List<HarnessMessage>): List<HarnessEvent> {
    if (sessionId.isBlank() || messages.isEmpty()) return emptyList()
    val events = mutableListOf<HarnessEvent>()
    var currentRound = 0
    var opId = "hist-$sessionId"
    val toolCallMap = messages.filterIsInstance<ToolCall>().associateBy { it.id }
    messages.forEach { msg ->
        when (msg) {
            is UserMessage -> {
                currentRound++
                opId = "hist-${msg.id}"
                events.add(
                    HarnessEvent.OperationStarted(
                        sessionId = sessionId,
                        timestamp = msg.createdAt,
                        operationId = opId,
                        laneName = "main",
                    )
                )
            }
            is AssistantText -> {
                events.add(
                    HarnessEvent.ProviderRoundStarted(
                        sessionId = sessionId,
                        timestamp = msg.createdAt,
                        operationId = opId,
                        round = currentRound,
                        attempt = 1,
                        modelId = null,
                    )
                )
                events.add(
                    HarnessEvent.ProviderRoundSettled(
                        sessionId = sessionId,
                        timestamp = msg.createdAt,
                        operationId = opId,
                        round = currentRound,
                        entryId = msg.id,
                        inputTokens = 0L,
                        outputTokens = msg.text.length.toLong(),
                    )
                )
            }
            is ToolCall -> {
                events.add(
                    HarnessEvent.ToolCallStarted(
                        sessionId = sessionId,
                        timestamp = msg.createdAt,
                        operationId = opId,
                        toolCallId = msg.id,
                        toolName = msg.tool.name.lowercase(),
                    )
                )
            }
            is ToolResult -> {
                val call = toolCallMap[msg.toolCallId]
                val toolName = call?.tool?.name?.lowercase() ?: "tool"
                events.add(
                    HarnessEvent.ToolCallSettled(
                        sessionId = sessionId,
                        timestamp = msg.createdAt,
                        operationId = opId,
                        toolCallId = msg.toolCallId,
                        toolName = toolName,
                        success = msg.success,
                        durationMs = msg.durationMs,
                    )
                )
            }
            else -> Unit
        }
    }
    return events
}
