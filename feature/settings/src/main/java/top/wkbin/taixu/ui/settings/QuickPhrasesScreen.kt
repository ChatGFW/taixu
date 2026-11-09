package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.model.QuickPhrase
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeFilterChip
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
@Composable
fun QuickPhrasesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel? = previewOrNull { koinViewModel() },
) {
    val phrases by viewModel?.quickPhrases?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    // QuickPhrase 非 Parcelable：仅保存 id，旋转后从列表按 id 恢复编辑对象
    var editingPhraseId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val editingPhrase = editingPhraseId?.let { id -> phrases.firstOrNull { it.id == id } }
    var isCreating by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showResetDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var selectedFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>("ALL") }
    // 待确认删除的短语 id（破坏性操作二次确认）
    var pendingDeletePhraseId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeletePhrase = pendingDeletePhraseId?.let { id -> phrases.firstOrNull { it.id == id } }

    val filterOptions = listOf(
        "ALL" to "全部 (${phrases.size})",
        "GENERAL" to "通用",
        "ANDROID" to "Android",
        "FLUTTER" to "Flutter",
        "REVERSE" to "逆向分析",
    )

    val filteredPhrases = remember(phrases, selectedFilter) {
        when (selectedFilter) {
            "ALL" -> phrases
            "GENERAL" -> phrases.filter { it.targetProjectType == null }
            "ANDROID" -> phrases.filter { it.targetProjectType == "ANDROID" }
            "FLUTTER" -> phrases.filter { it.targetProjectType == "FLUTTER" }
            "REVERSE" -> phrases.filter { it.targetProjectType == "REVERSE" }
            else -> phrases
        }
    }

    if (showResetDialog) {
        RuntimeAlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置快捷短语预设", fontWeight = FontWeight.Medium) },
            text = { Text("确定要恢复出厂默认的快捷短语与指令吗？这将清除你自定义添加的短语。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel?.resetQuickPhrasesToDefault()
                        showResetDialog = false
                    },
                ) {
                    Text("确认重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (isCreating || editingPhrase != null) {
        QuickPhraseEditorDialog(
            phrase = editingPhrase,
            onDismiss = {
                isCreating = false
                editingPhraseId = null
            },
            onSave = { id, title, content, description, iconName, targetProjectType, isEnabled ->
                viewModel?.saveQuickPhrase(
                    id = id,
                    title = title,
                    content = content,
                    description = description,
                    iconName = iconName,
                    targetProjectType = targetProjectType,
                    isEnabled = isEnabled,
                )
                isCreating = false
                editingPhraseId = null
            },
        )
    }

    pendingDeletePhrase?.let { phrase ->
        RuntimeAlertDialog(
            onDismissRequest = { pendingDeletePhraseId = null },
            title = { Text("删除快捷短语", fontWeight = FontWeight.Medium) },
            text = { Text("确定要删除「${phrase.title}」吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel?.deleteQuickPhrase(phrase.id)
                        pendingDeletePhraseId = null
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePhraseId = null }) {
                    Text("取消")
                }
            },
        )
    }

    val scrollBehavior = top.wkbin.taixu.ui.miuix.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = RuntimeTheme.colorScheme.surface,
        topBar = {
            RuntimeTopBar(
                title = "快捷短语与常用指令",
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        contentDescription = "重置快捷短语预设",
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Refresh,
                            modifier = Modifier.size(20.dp),
                            tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { isCreating = true },
                        contentDescription = "新增快捷短语",
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Plus,
                            modifier = Modifier.size(20.dp),
                            tint = RuntimeTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(top.wkbin.taixu.ui.components.runtimeScrollViewport(padding)),
        ) {
            // 分类筛选胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filterOptions.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    RuntimeFilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = {
                            Text(
                                text = label,
                                style = RuntimeTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                ),
                            )
                        },
                    )
                }
            }

            if (filteredPhrases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Chat,
                            modifier = Modifier.size(48.dp),
                            tint = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            text = "暂无匹配的快捷短语",
                            style = RuntimeTheme.typography.bodyMedium,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { isCreating = true }) {
                            Text("添加首条快捷短语")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = top.wkbin.taixu.ui.components.runtimeScrollContentPadding(padding, top.wkbin.taixu.ui.components.runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = 96.dp)),
                    verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap),
                ) {
                    items(filteredPhrases, key = { it.id }) { phrase ->
                        QuickPhraseCard(
                            phrase = phrase,
                            onToggle = { enabled -> viewModel?.toggleQuickPhrase(phrase.id, enabled) },
                            onEdit = { editingPhraseId = phrase.id },
                            onDelete = { pendingDeletePhraseId = phrase.id },
                        )
                    }
                }
            }
        }
    }
}
