package top.wkbin.taixu.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.components.runtimeScrollViewport
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.wkbin.taixu.ui.miuix.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.Text
/**
 * 模型档案管理全屏独立页面
 */
@Composable
fun ModelProfilesScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SettingsViewModel? = previewOrNull { koinViewModel() },
) {
    val models by viewModel?.models?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showImportDialog by remember { mutableStateOf(false) }
    var showExportAllDialog by remember { mutableStateOf(false) }
    var singleExportModel by remember { mutableStateOf<AiModelEntity?>(null) }
    var modelPendingDelete by remember { mutableStateOf<AiModelEntity?>(null) }
    var importing by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = RuntimeTheme.colorScheme.surface,
        topBar = {
            RuntimeTopBar(
                title = "模型档案",
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    RuntimeIconButton(onClick = onCreate, contentDescription = "新增模型档案") {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp))
                    }
                    // 📥 导入按钮
                    RuntimeIconButton(
                        onClick = { showImportDialog = true },
                        contentDescription = "导入模型配置",
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Download,
                            modifier = Modifier.size(20.dp),
                            tint = RuntimeTheme.colorScheme.onSurface,
                        )
                    }
                    // 📤 导出全部按钮
                    RuntimeIconButton(
                        onClick = {
                            if (models.isEmpty()) {
                                Toast.makeText(context, "暂无模型档案可导出", Toast.LENGTH_SHORT).show()
                            } else {
                                showExportAllDialog = true
                            }
                        },
                        contentDescription = "导出全部模型配置",
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.OpenInNew,
                            modifier = Modifier.size(20.dp),
                            tint = RuntimeTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        ModelProfilesContent(
            modifier = runtimeScrollViewport(padding), bottomPadding = padding.calculateBottomPadding(),
            models = models,
            onCreate = onCreate,
            onEdit = { model -> onEdit(model.id) },
            onActivate = { arg0 -> viewModel?.setActiveModel(arg0) },
            onDelete = { model -> modelPendingDelete = model },
            onExportSingle = { model -> singleExportModel = model },
        )
    }

    // 导入弹窗
    if (showImportDialog) {
        ModelImportDialog(
            onDismiss = { showImportDialog = false },
            importing = importing,
            models = models,
            parseProfiles = { raw -> viewModel?.parseProfilesFromJson(raw) ?: Result.failure(IllegalStateException()) },
            onImportJson = { jsonStr, mode ->
                if (importing) return@ModelImportDialog
                importing = true
                coroutineScope.launch {
                    val result = viewModel?.importProfilesFromJson(jsonStr, mode)
                    importing = false
                    result?.fold(
                        onSuccess = { count ->
                            Toast.makeText(context, "成功导入 $count 个模型档案", Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                        },
                        onFailure = { _ ->
                            Toast.makeText(context, "导入失败：JSON 无法解析或配置无效，请检查后重试", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            },
        )
    }

    // 批量导出弹窗
    if (showExportAllDialog) {
        ModelExportDialog(
            title = "批量导出模型档案",
            subtitle = "准备导出 ${models.size} 个模型档案配置",
            onDismiss = { showExportAllDialog = false },
            onGenerateJson = { includeKeys ->
                viewModel?.exportAllProfilesJson(includeKeys).orEmpty()
            },
        )
    }

    // 单项导出弹窗
    singleExportModel?.let { targetModel ->
        ModelExportDialog(
            title = "导出模型档案配置",
            subtitle = "模型: ${targetModel.name} (${targetModel.provider})",
            defaultFileName = "taixu_model_${targetModel.name.replace(" ", "_")}.json",
            onDismiss = { singleExportModel = null },
            onGenerateJson = { includeKeys ->
                viewModel?.exportSingleProfileJson(targetModel.id, includeKeys) ?: ""
            },
        )
    }

    // 删除确认弹窗
    modelPendingDelete?.let { target ->
        RuntimeAlertDialog(
            onDismissRequest = { modelPendingDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Alert, Modifier.size(20.dp), RuntimeTheme.colorScheme.error)
                    Text("删除模型档案")
                }
            },
            text = { Text("确定要删除模型档案「${target.name}」吗？此操作将同时清理该模型保存的 API Key。") },
            confirmButton = {
                RuntimeTextButton(
                    onClick = {
                        viewModel?.deleteModel(target.id)
                        modelPendingDelete = null
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                RuntimeTextButton(onClick = { modelPendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}
