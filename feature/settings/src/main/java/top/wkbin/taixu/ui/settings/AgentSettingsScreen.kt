package top.wkbin.taixu.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.common.translation.TranslationModelStatus
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.model.AgentDepartments
import top.wkbin.taixu.core.model.AgentPlugin
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.model.AgentSubagent
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.core.model.RunningSendMode
import top.wkbin.taixu.core.model.skill.ClawHubMarketItem
import top.wkbin.taixu.harness.ContextWindowPolicy
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollViewport
import top.wkbin.taixu.ui.miuix.*
import top.wkbin.taixu.ui.miuix.TopAppBarDefaults
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import top.wkbin.taixu.ui.settings.skill.SkillSecurityAuditDialog

enum class AgentSettingsCategory { EXECUTION, SUBAGENTS, SKILLS }

@Composable
fun AgentSettingsScreen(
    onBack: () -> Unit,
    category: AgentSettingsCategory = AgentSettingsCategory.EXECUTION,
    viewModel: SettingsViewModel? = previewOrNull { koinViewModel() },
) {
    val thinkingExpanded by viewModel?.thinkingExpanded?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val translationModelStatus by viewModel?.translationModelStatus?.collectAsStateWithLifecycle() ?: previewStateOf(TranslationModelStatus.NotDownloaded)
    val thinkingAutoTranslate by viewModel?.thinkingAutoTranslate?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val chatRoundCollapse by viewModel?.chatRoundCollapse?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val customSystemPromptEnabled by viewModel?.customSystemPromptEnabled?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val customSystemPrompt by viewModel?.customSystemPrompt?.collectAsStateWithLifecycle() ?: previewStateOf("")
    // 自动标题偏好直连 AgentPreferences（不经 SettingsViewModel，避免顶格文件再增长）
    val agentPrefs = koinInject<AgentPreferences>()
    val autoTitleScope = rememberCoroutineScope()
    val autoTitleEnabled by agentPrefs.autoTitleEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoTitlePrompt by agentPrefs.autoTitlePrompt.collectAsStateWithLifecycle(initialValue = "")
    val autoTitleModelId by agentPrefs.autoTitleModelId.collectAsStateWithLifecycle(initialValue = "")
    val agentCharName by viewModel?.agentCharName?.collectAsStateWithLifecycle() ?: previewStateOf("")
    val agentUserName by viewModel?.agentUserName?.collectAsStateWithLifecycle() ?: previewStateOf("")
    val compactionEnabled by viewModel?.contextCompactionEnabled?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val maxToolRounds by viewModel?.maxToolRounds?.collectAsStateWithLifecycle() ?: previewStateOf(0)
    val roundLimitAutoContinuations by viewModel?.roundLimitAutoContinuations?.collectAsStateWithLifecycle() ?: previewStateOf(0)
    val autoWorkspaceCwd by viewModel?.autoWorkspaceCwd?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val commandOutputCompressionEnabled by viewModel?.commandOutputCompressionEnabled?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val baseCommandTimeoutSeconds by viewModel?.baseCommandTimeoutSeconds?.collectAsStateWithLifecycle() ?: previewStateOf(0)
    val approvalMode by viewModel?.approvalMode?.collectAsStateWithLifecycle() ?: previewStateOf(ApprovalMode.ASSISTED)
    val runMode by viewModel?.runMode?.collectAsStateWithLifecycle() ?: previewStateOf(RunMode.BUILD)
    val runningSendMode by viewModel?.runningSendMode?.collectAsStateWithLifecycle() ?: previewStateOf(RunningSendMode.QUEUE)
    val maxToolsPerRound by viewModel?.maxToolsPerRound?.collectAsStateWithLifecycle() ?: previewStateOf(0)
    val maxConsecutiveFailures by viewModel?.maxConsecutiveFailures?.collectAsStateWithLifecycle() ?: previewStateOf(0)
    val contextBudgetTokens by viewModel?.contextBudgetTokens?.collectAsStateWithLifecycle() ?: previewStateOf(0)
    val effectiveContextBudget by viewModel?.effectiveContextBudget?.collectAsStateWithLifecycle() ?: previewStateOf(0)
    val activeModelDeclaredTokens by viewModel?.activeModelDeclaredTokens?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val contextFoldingRatioPercent by viewModel?.contextFoldingRatioPercent?.collectAsStateWithLifecycle() ?: previewStateOf(0)
    val skills by viewModel?.allSkills?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val subagents by viewModel?.allSubagents?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val autoSubagentDelegation by viewModel?.autoSubagentDelegationEnabled?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val models by viewModel?.models?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val plugins by viewModel?.allPlugins?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val skillArchiveMessage by viewModel?.skillArchiveMessage?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val skillArchiveMessageIsError by viewModel?.skillArchiveMessageIsError?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val skillEvolutionSuggestions by viewModel?.skillEvolutionSuggestions?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val marketSkills by viewModel?.clawHubMarketSkills?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val isMarketLoading by viewModel?.isMarketLoading?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val isMarketOfflinePreset by viewModel?.isMarketOfflinePreset?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val pendingSkillInspection by viewModel?.pendingSkillInspection?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val preparingSkillId by viewModel?.preparingSkillId?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val isCommittingInstallation by viewModel?.isCommittingInstallation?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    var showAddSkillDialog by remember { mutableStateOf(false) }
    var viewingSkillPrompt by remember { mutableStateOf<AgentSkill?>(null) }
    var editingSubagent by remember { mutableStateOf<AgentSubagent?>(null) }
    var showSubagentDialog by remember { mutableStateOf(false) }
    var deletingSubagent by remember { mutableStateOf<AgentSubagent?>(null) }
    var deletingSkill by remember { mutableStateOf<AgentSkill?>(null) }
    var subagentQuery by rememberSaveable { mutableStateOf("") }
    var skillQuery by rememberSaveable { mutableStateOf("") }
    var expandedDepartmentIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val visibleSubagentGroups = remember(subagents, subagentQuery) {
        val query = subagentQuery.trim().lowercase()
        subagents.asSequence()
            .filter { profile ->
                query.isBlank() || profile.name.lowercase().contains(query) ||
                    profile.id.lowercase().contains(query) || profile.description.lowercase().contains(query)
            }
            .groupBy { it.departmentId }
            .entries
            .sortedBy { AgentDepartments.find(it.key).sortOrder }
    }
    val visibleSkills = remember(skills, skillQuery) {
        val query = skillQuery.trim().lowercase()
        if (query.isBlank()) {
            skills
        } else {
            skills.filter { skill ->
                skill.name.lowercase().contains(query) || skill.id.lowercase().contains(query) ||
                    skill.description.lowercase().contains(query) || skill.category.lowercase().contains(query)
            }
        }
    }
    val skillArchivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.size == 1) {
            viewModel?.inspectLocalSkillZip(uris.first())
        } else if (uris.isNotEmpty()) {
            viewModel?.importSkillArchives(uris)
        }
    }
    val skillDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) viewModel?.importSkillsFromTree(uri) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = RuntimeTheme.colorScheme.surface,
        topBar = {
            RuntimeTopBar(
                title = when (category) {
                    AgentSettingsCategory.EXECUTION -> "Agent 执行与上下文"
                    AgentSettingsCategory.SUBAGENTS -> "子智能体角色"
                    AgentSettingsCategory.SKILLS -> "Skills 与插件"
                },
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(runtimeScrollViewport(padding)),
            contentPadding = runtimeScrollContentPadding(padding, runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom)),
            verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap),
        ) {
            if (category == AgentSettingsCategory.EXECUTION) {
            // ---- 模块 1：模型思考与交互表现 ----
            item {
                SectionHeader(
                    title = "思考流与执行表现",
                    subtitle = "控制 DeepSeek 等推理模型的思考过程呈现与工具执行上限",
                )
            }
            item {
                AgentBlockTitle("思考呈现与翻译")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Brain,
                        title = "默认展开模型思考过程",
                        subtitle = if (thinkingExpanded) "聊天界面中新生成的思考过程将默认展开呈现" else "思考过程（包括生成中内容）默认折叠，点击可展开查看",
                        checked = thinkingExpanded,
                        onCheckedChange = { arg0 -> viewModel?.setThinkingExpanded(arg0) },
                    )
                    AgentToggleRow(
                        icon = RuntimeIconName.Globe,
                        title = "展开时自动翻译英文思考为中文",
                        subtitle = if (thinkingAutoTranslate) "展开思考块时若检测为英文将自动调用本地离线模型翻译呈现" else "仅在点击思考块上的翻译按钮时进行翻译",
                        checked = thinkingAutoTranslate,
                        onCheckedChange = { arg0 -> viewModel?.setThinkingAutoTranslate(arg0) },
                    )
                    AgentToggleRow(
                        icon = RuntimeIconName.Compress,
                        title = "自动收拢历史轮次的思考与工具过程",
                        subtitle = if (chatRoundCollapse) "已结束的历史轮次中，超过 2 步的思考与工具调用将收拢为「展开更多」摘要，点击即可展开；当前进行中的轮次始终完整呈现" else "关闭时保持自然单行流：全部思考过程与工具调用逐条平铺呈现",
                        checked = chatRoundCollapse,
                        onCheckedChange = { arg0 -> viewModel?.setChatRoundCollapse(arg0) },
                    )
                }
            }
            item {
                AgentBlockTitle("思考流离线翻译语种模型")
            }
            item {
                AgentSettingsGroup {
                    TranslationModelCard(
                        status = translationModelStatus,
                        onDownload = { viewModel?.downloadTranslationModel() },
                        onDelete = { viewModel?.deleteTranslationModel() },
                        onRetry = { viewModel?.downloadTranslationModel() },
                    )
                }
            }
            item {
                AgentBlockTitle("执行上下文")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.FolderOpen,
                        title = "自动注入关联工作区路径",
                        subtitle = "当会话关联了工作区时，执行 base 命令默认以该目录为工作路径 (cwd)",
                        checked = autoWorkspaceCwd,
                        onCheckedChange = { arg0 -> viewModel?.setAutoWorkspaceCwd(arg0) },
                    )
                    AgentToggleRow(
                        icon = RuntimeIconName.Compress,
                        title = "智能压缩 Agent 命令输出",
                        subtitle = "减少 Agent 读取 git、搜索、测试与构建日志时的上下文消耗；终端与原始文件不受影响",
                        checked = commandOutputCompressionEnabled,
                        onCheckedChange = { arg0 -> viewModel?.setCommandOutputCompressionEnabled(arg0) },
                    )
                    ApprovalModeSelectorRow(
                        mode = approvalMode,
                        onModeChange = { arg0 -> viewModel?.setApprovalMode(arg0) },
                    )
                    RunModeSelectorRow(
                        mode = runMode,
                        onModeChange = { arg0 -> viewModel?.setRunMode(arg0) },
                    )
                    RunningSendModeSelectorRow(
                        mode = runningSendMode,
                        onModeChange = { arg0 -> viewModel?.setRunningSendMode(arg0) },
                    )
                }
            }
            hashlineSettingsItems(agentPrefs)
            item {
                AgentBlockTitle("工具调用限制")
            }
            item {
                AgentSettingsGroup {
                    BaseCommandTimeoutSliderRow(
                        currentValue = baseCommandTimeoutSeconds,
                        onValueChange = { arg0 -> viewModel?.setBaseCommandTimeoutSeconds(arg0) },
                    )
                    FirstResponseTimeoutRow()
                    RoundsSliderRow(
                        currentValue = maxToolRounds,
                        onValueChange = { arg0 -> viewModel?.setMaxToolRounds(arg0) },
                    )
                    AutoContinuationSliderRow(
                        currentValue = roundLimitAutoContinuations,
                        roundsPerSegment = maxToolRounds,
                        onValueChange = { arg0 -> viewModel?.setRoundLimitAutoContinuations(arg0) },
                    )
                    ToolsPerRoundSliderRow(
                        currentValue = maxToolsPerRound,
                        onValueChange = { arg0 -> viewModel?.setMaxToolsPerRound(arg0) },
                    )
                    ConsecutiveFailuresSliderRow(
                        currentValue = maxConsecutiveFailures,
                        onValueChange = { arg0 -> viewModel?.setMaxConsecutiveFailures(arg0) },
                    )
                }
            }

            // ---- 模块 1.5：自动会话标题 ----
            item {
                SectionHeader(
                    title = "自动会话标题",
                    subtitle = "回合结束后用模型为默认标题的会话生成语义标题",
                )
            }
            item {
                AutoTitleSettingsCard(
                    enabled = autoTitleEnabled,
                    onEnabledChange = { arg0 -> autoTitleScope.launch { agentPrefs.setAutoTitleEnabled(arg0) } },
                    prompt = autoTitlePrompt,
                    onPromptChange = { arg0 -> autoTitleScope.launch { agentPrefs.setAutoTitlePrompt(arg0) } },
                    modelId = autoTitleModelId,
                    onModelIdChange = { arg0 -> autoTitleScope.launch { agentPrefs.setAutoTitleModelId(arg0) } },
                    models = models,
                )
            }
            // ---- 模块 2：系统提示词与人设自定义 ----
            item {
                SectionHeader(
                    title = "系统提示词与人设自定义",
                    subtitle = "定制 Agent 初始人设、环境说明与动态宏变量注入",
                )
            }
            item {
                AgentBlockTitle("提示词编辑")
            }
            item {
                SystemPromptCustomCard(
                    enabled = customSystemPromptEnabled,
                    onEnabledChange = { arg0 -> viewModel?.setCustomSystemPromptEnabled(arg0) },
                    prompt = customSystemPrompt,
                    onPromptChange = { arg0 -> viewModel?.setCustomSystemPrompt(arg0) },
                    charName = agentCharName,
                    onCharNameChange = { arg0 -> viewModel?.setAgentCharName(arg0) },
                    userName = agentUserName,
                    onUserNameChange = { arg0 -> viewModel?.setAgentUserName(arg0) },
                )
            }

            // ---- 模块 2：上下文记忆与智能压缩 ----
            item {
                SectionHeader(
                    title = "上下文记忆与智能压缩",
                    subtitle = "优化长任务 Token 消耗，防止触及模型上下文窗口上限",
                )
            }
            item {
                AgentBlockTitle("压缩策略")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Compress,
                        title = "开启上下文智能压缩 (Context Compaction)",
                        subtitle = "历史超过 Token 折叠线时自动生成结构化摘要，保留近期原文、任务状态与关键文件足迹",
                        checked = compactionEnabled,
                        onCheckedChange = { arg0 -> viewModel?.setContextCompactionEnabled(arg0) },
                    )
                    if (compactionEnabled) {
                        ContextBudgetSliderRow(
                            currentValue = contextBudgetTokens,
                            declaredTokens = activeModelDeclaredTokens,
                            onValueChange = { arg0 -> viewModel?.setContextBudgetTokens(arg0) },
                        )
                        ContextFoldingRatioSliderRow(
                            currentValue = contextFoldingRatioPercent,
                            budget = effectiveContextBudget,
                            declaredTokens = activeModelDeclaredTokens,
                            onValueChange = { arg0 -> viewModel?.setContextFoldingRatioPercent(arg0) },
                        )
                    }
                }
            }

            }

            if (category == AgentSettingsCategory.SUBAGENTS) {
            // ---- 模块 3：内置子智能体 ----
            item {
                SectionHeader(
                    title = "软件研发 Agent (${subagents.count { it.isEnabled }}/${subagents.size} 已启用)",
                    subtitle = "Agency Agents · 9 个研发部门 · MIT 许可 · 完整提示词离线内置",
                )
            }
            item {
                AgentBlockTitle("自动委派策略")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Bot,
                        title = "自动判断并拆分任务",
                        subtitle = if (autoSubagentDelegation) {
                            "复杂且可并行的任务将由主智能体自行决定是否派发"
                        } else {
                            "仅在用户明确要求并行或子智能体协同时派发"
                        },
                        checked = autoSubagentDelegation,
                        onCheckedChange = { arg0 -> viewModel?.setAutoSubagentDelegationEnabled(arg0) },
                    )
                }
            }
            item {
                AgentBlockTitle("角色列表")
            }
            item {
                RuntimeFilledTextField(
                    value = subagentQuery,
                    onValueChange = { subagentQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    labelText = resolveLegacyString("搜索 Agent"),
                    placeholder = { Text("名称、角色标识或职责") },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Search, Modifier.size(18.dp)) },
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = {
                        editingSubagent = null
                        showSubagentDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RuntimeTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = RuntimeTheme.colorScheme.primaryContainer),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp), RuntimeTheme.colorScheme.onPrimaryContainer)
                        Text("新增子智能体角色", color = RuntimeTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                    }
                }
            }
            visibleSubagentGroups.forEach { (departmentId, profiles) ->
                val expanded = subagentQuery.isNotBlank() || departmentId in expandedDepartmentIds
                item(key = "department:$departmentId") {
                    AgentDepartmentCard(
                        departmentId = departmentId,
                        enabledCount = profiles.count { it.isEnabled },
                        agentCount = profiles.size,
                        expanded = expanded,
                        onClick = {
                            if (subagentQuery.isBlank()) {
                                expandedDepartmentIds = if (expanded) {
                                    expandedDepartmentIds - departmentId
                                } else {
                                    expandedDepartmentIds + departmentId
                                }
                            }
                        },
                    )
                }
                if (expanded) {
                    items(profiles, key = { "subagent:${it.id}" }) { profile ->
                        SubagentProfileCard(
                            profile = profile,
                            modelLabel = profile.defaultModelId?.let { modelId ->
                                models.firstOrNull { it.id == modelId }?.let { model ->
                                    "${model.name} · ${profile.defaultModelVariant ?: model.model.substringBefore(',').trim()}"
                                } ?: "模型档案已删除"
                            } ?: "跟随主智能体",
                            onToggle = { enabled -> viewModel?.toggleSubagent(profile.id, enabled) },
                            onEdit = {
                                editingSubagent = profile
                                showSubagentDialog = true
                            },
                            onDelete = { deletingSubagent = profile },
                        )
                    }
                }
            }

            }

            if (category == AgentSettingsCategory.SKILLS) {
            // ---- 模块 4：Skill 专精技能库 ----
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "Skill 专精技能库 (${skills.count { it.isEnabled }}/${skills.size} 已启用)",
                        subtitle = "向 Agent 系统提示词注入领域专业规范与操作指导",
                    )
                }
            }
            item {
                AgentBlockTitle("技能进化")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Sparkles,
                        title = "对话后建议沉淀或修复技能",
                        subtitle = if (skillEvolutionSuggestions) {
                            "成功完成一轮含工具调用的工作后，可建议创建新技能或修复既有自定义技能"
                        } else {
                            "已关闭：对话结束后不再弹出技能进化建议卡片"
                        },
                        checked = skillEvolutionSuggestions,
                        onCheckedChange = { arg0 -> viewModel?.setSkillEvolutionSuggestions(arg0) },
                    )
                }
            }
            item {
                AgentBlockTitle("技能管理")
            }
            item {
                OutlinedButton(onClick = { skillArchivePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.FolderDownload, Modifier.size(16.dp))
                        Text("从 ZIP 导入 Skill（支持批量多选）")
                    }
                }
            }
            item {
                OutlinedButton(onClick = { viewModel?.scanSkillDirectories() }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.FolderOpen, Modifier.size(16.dp))
                        Text("扫描 Skill 目录批量导入")
                    }
                }
                Text(
                    text = "可将 rikkahub、aicode 等工具的 skills 目录整体复制到 attachments/skills 或工作区 skills 目录，重启应用即自动批量导入；运行中复制可点击上方按钮立即扫描，已导入的不会重复注册",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            item {
                OutlinedButton(onClick = { skillDirPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.Search, Modifier.size(16.dp))
                        Text("从自选目录导入（任意位置）")
                    }
                }
                Text(
                    text = "可选择单个 Skill 或技能集合目录。包含 SKILL.md 的目录作为一个完整 Skill 导入，内部子文档和脚本保留为资源，不单独列出",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            item {
                Button(
                    onClick = { showAddSkillDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RuntimeTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = RuntimeTheme.colorScheme.primaryContainer),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(16.dp), RuntimeTheme.colorScheme.onPrimaryContainer)
                        Text("新增自定义 Skill 技能", color = RuntimeTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                    }
                }
            }

            item {
                RuntimeFilledTextField(
                    value = skillQuery,
                    onValueChange = { skillQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    labelText = resolveLegacyString("搜索 Skill"),
                    placeholder = { Text("名称、标识、分类或描述") },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Search, Modifier.size(18.dp)) },
                    singleLine = true,
                )
            }

            items(visibleSkills, key = { it.id }) { skill ->
                SkillCard(
                    skill = skill,
                    onToggle = { enabled -> viewModel?.toggleSkill(skill.id, enabled) },
                    onViewPrompt = { viewingSkillPrompt = skill },
                    onDelete = if (!skill.isBuiltin) { { deletingSkill = skill } } else null,
                )
            }
            if (visibleSkills.isEmpty()) {
                item {
                    Text(
                        text = "没有匹配 \"${skillQuery.trim()}\" 的技能",
                        style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ---- 模块 4.5：ClawHub 技能生态市场 ----
            item {
                SectionHeader(
                    title = "ClawHub 技能生态市场",
                    subtitle = if (isMarketOfflinePreset) {
                        "离线精选模式：以下为内置精选生态包（远端市场暂未接入）；安装前仍由本地引擎执行静态安全与兼容性审查"
                    } else {
                        "精选社区标准生态技能包，下载前由本地引擎执行静态安全与兼容性审查"
                    },
                )
            }

            if (isMarketLoading && marketSkills.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                items(marketSkills, key = { "clawhub_" + it.id }) { marketItem ->
                    ClawHubMarketSkillCard(
                        item = marketItem,
                        isPreparing = preparingSkillId == marketItem.id,
                        onInstall = { viewModel?.prepareInstallMarketSkill(marketItem.id) },
                    )
                }
            }

            // ---- 模块 5：Plugin 插件生态管理 ----
            item {
                SectionHeader(
                    title = "Plugin 插件生态管理 (${plugins.count { it.isEnabled }}/${plugins.size} 运行中)",
                    subtitle = "沙箱运行时探测、高危操作安全拦截与扩展能力",
                )
            }

            item {
                AgentBlockTitle("已安装插件")
            }
            items(plugins, key = { it.id }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    onToggle = { enabled -> viewModel?.togglePlugin(plugin.id, enabled) },
                )
            }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    pendingSkillInspection?.let { inspection ->
        SkillSecurityAuditDialog(
            inspection = inspection,
            isCommitting = isCommittingInstallation,
            onConfirmInstall = { viewModel?.confirmSkillInstallation() },
            onDismiss = { viewModel?.dismissSkillInspection() },
        )
    }

    skillArchiveMessage?.let { message ->
        RuntimeAlertDialog(
            onDismissRequest = { viewModel?.clearSkillArchiveMessage() },
            title = { Text(if (!skillArchiveMessageIsError) "Skill 导入完成" else "Skill 导入失败", fontWeight = FontWeight.Medium) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel?.clearSkillArchiveMessage() }) { Text("知道了") } },
        )
    }

    // 查看 Skill 完整提示词弹窗
    viewingSkillPrompt?.let { skill ->
        RuntimeAlertDialog(
            onDismissRequest = { viewingSkillPrompt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(skill.name, fontWeight = FontWeight.Medium)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(skill.description, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                    Text("注入系统的指导提示词 (System Prompt)：", style = RuntimeTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    Surface(
                        color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                        shape = RuntimeTheme.shapes.chip,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            skill.systemPrompt,
                            modifier = Modifier.padding(10.dp),
                            style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingSkillPrompt = null }) {
                    Text("关闭")
                }
            },
        )
    }

    // 新增自定义 Skill 弹窗
    if (showAddSkillDialog) {
        AddSkillDialog(
            onDismiss = { showAddSkillDialog = false },
            onConfirm = { name, desc, prompt, cmd ->
                viewModel?.addCustomSkill(name, desc, prompt, cmd)
                showAddSkillDialog = false
            },
        )
    }

    if (showSubagentDialog) {
        SubagentEditorDialog(
            profile = editingSubagent,
            models = models,
            existingIds = subagents.mapTo(mutableSetOf()) { it.id },
            onDismiss = {
                showSubagentDialog = false
                editingSubagent = null
            },
            onConfirm = { roleId, name, description, prompt, defaultModelId, defaultModelVariant ->
                viewModel?.saveSubagent(
                    editingSubagent,
                    roleId,
                    name,
                    description,
                    prompt,
                    defaultModelId,
                    defaultModelVariant,
                )
                showSubagentDialog = false
                editingSubagent = null
            },
        )
    }

    deletingSkill?.let { skill ->
        RuntimeAlertDialog(
            onDismissRequest = { deletingSkill = null },
            title = { Text("删除 Skill", fontWeight = FontWeight.Medium) },
            text = { Text("确定删除“${skill.name}”吗？其脚本资源目录将一并清理，删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel?.deleteCustomSkill(skill.id)
                        deletingSkill = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSkill = null }) { Text("取消") }
            },
        )
    }

    deletingSubagent?.let { profile ->
        RuntimeAlertDialog(
            onDismissRequest = { deletingSubagent = null },
            title = { Text("删除子智能体", fontWeight = FontWeight.Medium) },
            text = { Text("确定删除“${profile.name}”吗？删除后该角色不会再参与任务委派。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel?.deleteSubagent(profile.id)
                        deletingSubagent = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSubagent = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AutoContinuationSliderRow(
    currentValue: Int,
    roundsPerSegment: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    val continuations = sliderVal.toInt()
    val summary = if (continuations == 0) {
        "轮次用尽即停下等待用户确认；适合希望逐段把关的场景"
    } else {
        "轮次用尽时先让模型收束并记录进度，再自动续跑，无需用户点击继续；总预算上限 ${roundsPerSegment * (continuations + 1)} 轮"
    }
    top.wkbin.taixu.ui.components.RuntimeSliderPreference(
        value = sliderVal,
        onValueChange = { sliderVal = it },
        modifier = Modifier.fillMaxWidth(),
        title = "轮次用尽后自动续跑",
        summary = summary,
        valueText = if (continuations == 0) "关闭" else "$continuations 次",
        valueRange = 0f..10f,
        steps = 9,
        onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
    )
}

@Composable
private fun ContextBudgetSliderRow(
    currentValue: Int,
    declaredTokens: Int?,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    val summary = if (declaredTokens != null) {
        "当前激活模型已适配上下文上限 ${declaredTokens / 1000}K（显式配置或自动识别），此滑块暂不生效；仅在模型无法识别且未配置 contextTokens 时，本值才作为兜底预算。"
    } else {
        "当前未识别到激活模型上下文上限，本值即实际生效预算；长会话历史超出预算将自动折叠早期内容。"
    }
    top.wkbin.taixu.ui.components.RuntimeSliderPreference(
        value = sliderVal,
        onValueChange = { sliderVal = it },
        modifier = Modifier.fillMaxWidth(),
        title = "上下文 Token 预算上限",
        summary = summary,
        valueText = "${sliderVal.toInt()} tok",
        valueRange = 8000f..2000000f,
        steps = 49,
        onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
    )
}

/**
 * 「历史折叠线比例」滑块。
 *
 * 让历史在预算的一部分处就开始折叠，而不是等到预算减预留的硬线才动手——
 * 长会话可借此显著降低单次请求的 input token 量（省费用、降首字延迟）。
 */
@Composable
private fun ContextFoldingRatioSliderRow(
    currentValue: Int,
    budget: Int,
    declaredTokens: Int?,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    // 实时预览：按当前比例算出的折叠线，让用户直观看到「拖到多少就按多少折叠」。
    // 关键：budget 必须是**实际生效预算**（模型档案 contextTokens 优先，再钳到 MAX_CONTEXT_BUDGET），
    // 并且扣减引擎同款 system/输出/工具 schema 预留，否则设置页会高于真实折叠触发线。
    val previewSystemTokens = ContextWindowPolicy.estimateReservedPromptTokens(
        pureChat = false,
        toolDisabled = false,
    )
    val previewLimit = remember(sliderVal, budget, previewSystemTokens) {
        ContextWindowPolicy.foldingLimitFor(
            budget = budget,
            ratioPercent = sliderVal.toInt(),
            systemTokens = previewSystemTokens,
        ).coerceAtLeast(0)
    }
    val budgetHint = if (declaredTokens != null) {
        "以上按当前生效模型窗口 ${budget / 1000}K 计算（显式配置或自动适配）" +
            (if (declaredTokens > ContextWindowPolicy.MAX_CONTEXT_BUDGET) {
                "，引擎钳制到 ${ContextWindowPolicy.MAX_CONTEXT_BUDGET / 1000}K"
            } else {
                ""
            }) +
            "。预览已扣除系统提示/输出/工具 schema 预留。"
    } else {
        "以上按上方「上下文预算上限」滑块的值计算（当前未识别到模型上下文上限）。预览已扣除系统提示/输出/工具 schema 预留。"
    }
    val summary =
        "历史在「预算 × 比例」处开始折叠；默认 100%。调低比例可让历史更早进入摘要、节省 input token。" +
            "按当前设置，折叠线约为 ${previewLimit / 1000}K tok。" + budgetHint
    top.wkbin.taixu.ui.components.RuntimeSliderPreference(
        value = sliderVal,
        onValueChange = { sliderVal = it },
        modifier = Modifier.fillMaxWidth(),
        title = "历史折叠线比例",
        summary = summary,
        valueText = "${sliderVal.toInt()}%",
        valueRange = 10f..100f,
        steps = 8,
        onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
    )
}

@Composable
private fun ToolsPerRoundSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    top.wkbin.taixu.ui.components.RuntimeSliderPreference(
        value = sliderVal,
        onValueChange = { sliderVal = it },
        modifier = Modifier.fillMaxWidth(),
        title = "单轮最大工具调用数",
        summary = "防止模型一次性爆发大量工具调用耗尽上下文或失控循环",
        valueText = "${sliderVal.toInt()} 个",
        valueRange = 1f..30f,
        steps = 29,
        onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
    )
}

@Composable
private fun ConsecutiveFailuresSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    top.wkbin.taixu.ui.components.RuntimeSliderPreference(
        value = sliderVal,
        onValueChange = { sliderVal = it },
        modifier = Modifier.fillMaxWidth(),
        title = "连续失败熔断阈值",
        summary = "连续多轮工具调用全部失败时主动终止，避免陷入死循环空转",
        valueText = "${sliderVal.toInt()} 轮",
        valueRange = 2f..30f,
        steps = 28,
        onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillCard(
    skill: AgentSkill,
    onToggle: (Boolean) -> Unit,
    onViewPrompt: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        borderColor = if (skill.isEnabled) RuntimeTheme.colorScheme.primary else Color.Transparent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    skill.name,
                    style = RuntimeTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(
                    color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                    shape = RuntimeTheme.shapes.tiny,
                ) {
                    Text(
                        skill.category,
                        style = RuntimeTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val cmd = skill.triggerCommand
                if (cmd != null) {
                    Surface(
                        color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RuntimeTheme.shapes.tiny,
                    ) {
                        Text(
                            cmd,
                            style = RuntimeTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = RuntimeTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Text(skill.description, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onViewPrompt, contentPadding = PaddingValues(0.dp)) {
                    Text("查看指导词 (Prompt)", style = RuntimeTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                if (skill.isImmutable) {
                    Surface(
                        color = RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RuntimeTheme.shapes.tiny,
                    ) {
                        Text(
                            "系统核心常驻",
                            style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = RuntimeTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(32.dp),
                            contentDescription = "删除 Skill",
                        ) {
                            RuntimeIcon(RuntimeIconName.Trash, Modifier.size(16.dp), RuntimeTheme.colorScheme.error)
                        }
                    }
                    Switch(
                        checked = skill.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RuntimeTheme.colorScheme.onPrimary,
                            checkedTrackColor = RuntimeTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClawHubMarketSkillCard(
    item: ClawHubMarketItem,
    isPreparing: Boolean,
    onInstall: () -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        borderColor = Color.Transparent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    item.name,
                    style = RuntimeTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(
                    color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                    shape = RuntimeTheme.shapes.tiny,
                ) {
                    Text(
                        item.category,
                        style = RuntimeTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RuntimeTheme.shapes.tiny,
                ) {
                    Text(
                        "v${item.version}",
                        style = RuntimeTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = RuntimeTheme.colorScheme.primary,
                    )
                }
            }
            Text(item.description, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "作者: ${item.author} · ★ ${item.stars}",
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    isPreparing -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RuntimeTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Text("审查中…", style = RuntimeTheme.typography.labelSmall)
                            }
                        }
                    }
                    item.isInstalled -> {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            shape = RuntimeTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text("已安装", style = RuntimeTheme.typography.labelSmall)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onInstall,
                            enabled = !isPreparing,
                            shape = RuntimeTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = RuntimeTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(name = RuntimeIconName.Shield, modifier = Modifier.size(14.dp))
                                Text("审查并安装", style = RuntimeTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginCard(
    plugin: AgentPlugin,
    onToggle: (Boolean) -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        borderColor = if (plugin.isEnabled) RuntimeTheme.colorScheme.primary else Color.Transparent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            plugin.name,
                            style = RuntimeTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("v${plugin.version}", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "作者: ${plugin.author}",
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = RuntimeTheme.colorScheme.onPrimary,
                        checkedTrackColor = RuntimeTheme.colorScheme.primary,
                    ),
                )
            }

            Text(plugin.description, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)

            if (plugin.permissions.isNotEmpty()) {
                val visiblePermissions = plugin.permissions.take(4)
                val hiddenCount = plugin.permissions.size - visiblePermissions.size
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "权限:",
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                    visiblePermissions.forEach { perm ->
                        Surface(
                            color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                            shape = RuntimeTheme.shapes.tiny,
                        ) {
                            Text(
                                perm,
                                style = RuntimeTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp).padding(horizontal = 4.dp, vertical = 2.dp),
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hiddenCount > 0) {
                        Surface(
                            color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                            shape = RuntimeTheme.shapes.tiny,
                        ) {
                            Text(
                                "等 ${plugin.permissions.size} 项权限",
                                style = RuntimeTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
