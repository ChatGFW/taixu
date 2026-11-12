package top.wkbin.taixu.ui.chat

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.database.AgentApprovalRequestEntity
import top.wkbin.taixu.core.database.AgentPlanEntity
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.BtwQuery
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.a2uipoc.LocalA2uiSessionId
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.SkillSuggestion
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.checkpoint.RewindScope
import top.wkbin.taixu.harness.compaction.CompactionSnapshot
import top.wkbin.taixu.harness.mcp.McpWorkspaceRecommender
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.harness.workflow.ProactiveWorkflowSuggestion
import top.wkbin.taixu.runtime.DistributionCatalog
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.SessionWorkspaceCatalogState
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.miuix.*
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

@Composable
internal fun ChatPaneContent(
    modifier: Modifier = Modifier,
    messages: List<HarnessMessage>,
    onboardingPrivilege: OnboardingPrivilege? = null,
    listState: LazyListState,
    running: Boolean,
    status: String?,
    thinkingExpanded: Boolean,
    thinkingAutoTranslate: Boolean = false,
    chatRoundCollapse: Boolean = false,
    thinkingLive: Boolean,
    liveThinkingMessageId: String?,
    onNavigateToSettings: (() -> Unit)? = null,
    toolResults: Map<String, ToolResult>,
    workspace: String,
    workspaceProject: WorkspaceProject? = null,
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)?,
    onEditMessage: (UserMessage) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRewindMessage: (String, RewindScope) -> Unit = { _, _ -> },
    error: String?,
    onClearError: () -> Unit,
    matchingCommands: List<SlashCommandItem>,
    matchingMentions: List<MentionItem> = emptyList(),
    attachedMentions: List<MentionItem> = emptyList(),
    knownMentionNames: List<String> = emptyList(),
    queuedPrompts: List<QueuedPrompt>,
    onEditQueuedPrompt: (QueuedPrompt) -> Unit,
    onRemoveQueuedPrompt: (QueuedPrompt) -> Unit,
    onConvertToSteer: (QueuedPrompt) -> Unit = {},
    sendMode: ComposerSendMode,
    input: String,
    shareActions: ShareQuickActionsState? = null,
    onInputChanged: (String) -> Unit,
    onApplyCommand: (SlashCommandItem) -> Unit,
    onApplyMention: (MentionItem) -> Unit = {},
    onRemoveMention: (MentionItem) -> Unit = {},
    attachments: List<ChatAttachment> = emptyList(),
    attachmentsProcessing: Boolean = false,
    onAttachmentsPicked: (List<Uri>, Boolean) -> Unit = { _, _ -> },
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
    onSend: () -> Unit, onSendWithoutAnswer: () -> Unit, onPasteLongText: (String) -> Unit,
    onStop: () -> Unit,
    lastAssistantMessageId: String?,
    onRegenerate: () -> Unit,
    onCreateBranch: (String) -> Unit,
    onRetryTool: (String) -> Unit,
    initializing: Boolean = false,
    pinnedCapabilities: List<MentionItem> = emptyList(),
    onOpenSkillsMcp: () -> Unit = {},
    canCompactContext: Boolean = false,
    compactingContext: Boolean = false,
    onCompactContext: (onFinished: () -> Unit) -> Unit = { it() },
    onUnpinMention: (String) -> Unit = {},
    activeModel: AiModelEntity? = null,
    onOpenModels: () -> Unit, approvalMode: ApprovalMode, onOpenApprovalModes: () -> Unit,
    onUpdateReasoning: (mode: String?, effort: String?) -> Unit = { _, _ -> },
    pendingApprovals: List<AgentApprovalRequestEntity> = emptyList(),
    activePlan: AgentPlanEntity? = null,
    activeCompaction: CompactionSnapshot? = null,
    mcpRecommendations: List<McpWorkspaceRecommender.Recommendation> = emptyList(),
    onEnableMcpRecommendation: (String) -> Unit = {},
    onInstallMcpRecommendation: () -> Unit = {},
    onDismissMcpRecommendation: (String) -> Unit = {},
    workflowSuggestions: List<ProactiveWorkflowSuggestion> = emptyList(),
    onLaunchWorkflowSuggestion: (ProactiveWorkflowSuggestion) -> Unit = {},
    onDismissWorkflowSuggestion: (String) -> Unit = {},
    btwQueries: List<BtwQuery> = emptyList(),
    btwStreaming: String = "",
    onCancelBtw: () -> Unit = {},
    onResolveApproval: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onResolveQuestion: (String, String) -> Unit = { _, _ -> },
    contextUsage: ContextUsage = ContextUsage(),
    onViewSubagentLanes: () -> Unit = {},
    subagentBranches: List<ConversationBranch> = emptyList(),
    onOpenSubagentBranch: (ConversationBranch) -> Unit = {},
    hiddenSkillSuggestions: Set<String> = emptySet(),
    onApplySkillSuggestion: (SkillSuggestion, Boolean) -> Unit = { _, _ -> },
    onDismissSkillSuggestion: (String) -> Unit = {},
) {
    val density = LocalDensity.current
    var bottomChromePx by remember { mutableIntStateOf(0) }
    var composerPx by remember { mutableIntStateOf(0) }
    val bottomChrome = with(density) { bottomChromePx.toDp() }
    val composerHeight = with(density) { composerPx.toDp() }
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            // 顶/底渐隐：顶=状态栏+工具栏；底=输入框高+导航栏，不越过输入框上方正文。
            val topContentFade = chatTopEdgeFadeHeight()
            val bottomContentFade = composerHeight + chatBottomNavFadeHeight()
            ChatMessageViewport(listState, Modifier.fillMaxSize(), bottomInset = bottomChrome) {
                ChatMessageList(
                    modifier = Modifier
                        .fillMaxSize()
                        .chatTopContentFade(topContentFade)
                        .chatBottomContentFade(bottomContentFade),
                    listState = listState,
                    messages = messages,
                    toolResults = toolResults,
                    initializing = initializing,
                    running = running,
                    status = status,
                    workspace = workspace,
                    workspaceProject = workspaceProject,
                    onboardingPrivilege = onboardingPrivilege,
                    thinkingExpanded = thinkingExpanded,
                    thinkingAutoTranslate = thinkingAutoTranslate,
                    chatRoundCollapse = chatRoundCollapse,
                    thinkingLive = thinkingLive,
                    liveThinkingMessageId = liveThinkingMessageId,
                    lastAssistantMessageId = lastAssistantMessageId,
                    onNavigateToSettings = onNavigateToSettings,
                    knownMentionNames = knownMentionNames,
                    onSelectCommand = onApplyCommand,
                    onEditMessage = onEditMessage,
                    onDeleteMessage = onDeleteMessage,
                    onRewindMessage = onRewindMessage,
                    onCreateBranch = onCreateBranch,
                    onRegenerate = onRegenerate,
                    onRetryTool = onRetryTool,
                    onOpenFile = onOpenFile,
                    activeCompaction = activeCompaction,
                    activePlan = activePlan,
                    pendingApprovals = pendingApprovals,
                    onResolveApproval = onResolveApproval,
                    onResolveQuestion = onResolveQuestion,
                    onViewSubagentLanes = onViewSubagentLanes,
                    subagentBranches = subagentBranches,
                    onOpenSubagent = onOpenSubagentBranch,
                    hiddenSkillSuggestions = hiddenSkillSuggestions,
                    onApplySkillSuggestion = onApplySkillSuggestion,
                    onDismissSkillSuggestion = onDismissSkillSuggestion,
                    bottomContentPadding = bottomChrome,
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomChromePx = it.height }
                    .windowInsetsPadding(chatBottomInsets()),
            ) {
                activePlan?.let { plan ->
                    val steps = remember(plan.stepsJson) { PlanStepParser.parse(plan.stepsJson) }
                    if (steps.isNotEmpty()) {
                        StickyPlanBar(
                            goal = plan.goal,
                            steps = steps,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
                error?.let { ChatErrorBanner(error = it, onClear = onClearError) }
                McpRecommendationBanner(
                    recommendations = mcpRecommendations,
                    onEnable = onEnableMcpRecommendation,
                    onInstall = onInstallMcpRecommendation,
                    onDismiss = onDismissMcpRecommendation,
                )
                ProactiveWorkflowBanner(
                    suggestions = workflowSuggestions,
                    onLaunch = onLaunchWorkflowSuggestion,
                    onDismiss = onDismissWorkflowSuggestion,
                )
                BtwPanel(queries = btwQueries, streaming = btwStreaming, onCancel = onCancelBtw)
                shareActions?.let { share ->
                    ShareQuickActionsRow(state = share, modifier = Modifier.padding(vertical = 4.dp))
                }
                // 与 Plan 同级：排队条在输入框上方独立悬浮，不计入输入框渐隐高度。
                QueuedPromptStack(
                    prompts = queuedPrompts,
                    onEdit = onEditQueuedPrompt,
                    onRemove = onRemoveQueuedPrompt,
                    onConvertToSteer = onConvertToSteer,
                )
                Box(Modifier.onSizeChanged { composerPx = it.height }) {
                    ChatComposer(
                        listState = listState,
                        running = running,
                        initializing = initializing,
                        status = status,
                        messages = messages,
                        toolResults = toolResults,
                        workspace = workspace,
                        input = input,
                        onInputChanged = onInputChanged,
                        onSend = onSend,
                        onSendWithoutAnswer = onSendWithoutAnswer,
                        onPasteLongText = onPasteLongText,
                        onStop = onStop,
                        sendMode = sendMode,
                        matchingCommands = matchingCommands,
                        onApplyCommand = onApplyCommand,
                        matchingMentions = matchingMentions,
                        onApplyMention = onApplyMention,
                        attachments = attachments,
                        attachmentsProcessing = attachmentsProcessing,
                        onAttachmentsPicked = onAttachmentsPicked,
                        onRemoveAttachment = onRemoveAttachment,
                        knownMentionNames = knownMentionNames,
                        attachedMentions = attachedMentions,
                        onRemoveMention = onRemoveMention,
                        pinnedCapabilities = pinnedCapabilities,
                        onUnpinMention = onUnpinMention,
                        onOpenSkillsMcp = onOpenSkillsMcp,
                        canCompactContext = canCompactContext,
                        compactingContext = compactingContext,
                        onCompactContext = onCompactContext,
                        activeModel = activeModel,
                        onOpenModels = onOpenModels,
                        approvalMode = approvalMode,
                        onOpenApprovalModes = onOpenApprovalModes,
                        onUpdateReasoning = onUpdateReasoning,
                        contextUsage = contextUsage,
                    )
                }
            }
        }
    }
}
