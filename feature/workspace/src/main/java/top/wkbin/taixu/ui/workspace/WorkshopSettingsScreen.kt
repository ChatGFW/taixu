package top.wkbin.taixu.ui.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import org.koin.compose.viewmodel.koinViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.*
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeSettingsScaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconMenu
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.core.database.BuildScriptEntity
import top.wkbin.taixu.runtime.ProjectType
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
@Composable
fun WorkshopSettingsScreen(onBack: () -> Unit, onOpenEnvironment: () -> Unit, onOpenSigning: () -> Unit = {}, onEditScript: (WorkshopScriptType) -> Unit = {}, viewModel: WorkshopSettingsViewModel? = previewOrNull { koinViewModel() }) {
    val draft by viewModel?.draft?.collectAsStateWithLifecycle() ?: previewStateOf(WorkshopEnvironmentDraft())
    val scripts by viewModel?.managedScripts?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val projects by viewModel?.projects?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val bindings by viewModel?.projectBindings?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = scripts.firstOrNull { it.id == editingId }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleteScriptTarget by remember { mutableStateOf<BuildScriptEntity?>(null) }
    RuntimeSettingsScaffold(stringResource(R.string.workshop_settings_title), onBack) {
            item { SectionHeader(stringResource(R.string.workshop_settings_env_section), stringResource(R.string.workshop_settings_env_section_desc)) }
            item {
                SettingEntry(
                    RuntimeIconName.Android,
                    stringResource(R.string.workshop_settings_env_entry),
                    stringResource(
                        R.string.workshop_settings_env_entry_desc,
                        draft.androidSdkPath, draft.ndkPath, draft.gradlePath, draft.cmakePath,
                    ),
                    onOpenEnvironment,
                )
            }
            item { SectionHeader(stringResource(R.string.workshop_settings_signing_section), stringResource(R.string.workshop_settings_signing_section_desc)) }
            item { SettingEntry(RuntimeIconName.Key, stringResource(R.string.workshop_settings_signing_entry), stringResource(R.string.workshop_settings_signing_entry_desc), onOpenSigning) }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.workshop_settings_scripts_section), stringResource(R.string.workshop_settings_scripts_section_desc)) }
                RuntimeButton(onClick = { creating = true }) { Text(stringResource(R.string.workshop_settings_new_script)) }
            } }
            scripts.forEach { script -> item(key = script.id) {
                ManagedScriptCard(
                    script = script,
                    onEdit = { editingId = script.id },
                    onClone = { viewModel?.cloneScript(script) },
                    onDelete = { deleteScriptTarget = script },
                )
            } }
            item { SectionHeader(stringResource(R.string.workshop_binding_section), stringResource(R.string.workshop_binding_section_desc)) }
            projects.filter { it.projectType == ProjectType.ANDROID || it.projectType == ProjectType.FLUTTER }.forEach { project ->
                item(key = "binding-${project.name}") {
                    ProjectScriptBindingRow(
                        projectName = project.name,
                        projectType = project.projectType,
                        scripts = scripts.filter { it.projectType == project.projectType.name && !it.isBuiltin },
                        selectedId = bindings.firstOrNull { it.projectName == project.name }?.scriptId?.takeIf { id ->
                            scripts.any { it.id == id && !it.isBuiltin }
                        },
                        onSelect = { viewModel?.bindProject(project.name, it) },
                    )
                }
            }
    }
    if (creating) ManagedScriptEditorDialog(null, onDismiss = { creating = false }) { name, description, type, content ->
        val error = viewModel?.saveManagedScript(null, name, description, type, content)
        if (error == null) creating = false
        error
    }
    editing?.let { script -> ManagedScriptEditorDialog(script, onDismiss = { editingId = null }) { name, description, type, content ->
        val error = viewModel?.saveManagedScript(script.id, name, description, type, content)
        if (error == null) editingId = null
        error
    } }
    deleteScriptTarget?.let { script ->
        RuntimeAlertDialog(
            onDismissRequest = { deleteScriptTarget = null },
            title = { Text(stringResource(R.string.workshop_script_delete_title, script.name)) },
            text = { Text(stringResource(R.string.workshop_script_delete_message)) },
            confirmButton = {
                RuntimeButton(onClick = {
                    viewModel?.deleteManagedScript(script.id)
                    deleteScriptTarget = null
                }, colors = top.wkbin.taixu.ui.miuix.ButtonDefaults.buttonColors(containerColor = RuntimeTheme.colorScheme.error, contentColor = RuntimeTheme.colorScheme.onError)) { Text(stringResource(R.string.workspace_confirm_delete)) }
            },
            dismissButton = {
                RuntimeOutlinedButton(onClick = { deleteScriptTarget = null }) { Text(stringResource(R.string.workspace_cancel)) }
            },
        )
    }
}
@Composable
private fun ManagedScriptCard(script: BuildScriptEntity, onEdit: () -> Unit, onClone: () -> Unit, onDelete: () -> Unit) {
    RuntimeCard(onClick = onEdit, shape = RuntimeTheme.shapes.extraLarge, containerColor = RuntimeTheme.colorScheme.surfaceContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(if (script.projectType == ProjectType.FLUTTER.name) RuntimeIconName.Flutter else RuntimeIconName.Android, Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(script.name, style = RuntimeTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${script.projectType} · ${if (script.isBuiltin) stringResource(R.string.workshop_script_builtin) else stringResource(R.string.workshop_script_user)}",
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.primary,
                    )
                }
                // 与文件浏览器一致的溢出菜单模式（替代裸文字 clickable，保证触摸热区）
                RuntimeIconMenu(
                    entries = listOf(
                        DropdownEntry(
                            items = buildList {
                                add(DropdownItem(text = stringResource(R.string.workshop_script_edit), onClick = onEdit))
                                add(DropdownItem(text = stringResource(R.string.workshop_script_clone), onClick = onClone))
                                if (!script.isBuiltin) {
                                    add(DropdownItem(text = stringResource(R.string.workspace_delete), onClick = onDelete))
                                }
                            },
                        ),
                    ),
                    modifier = Modifier.size(40.dp),
                ) {
                        RuntimeIcon(RuntimeIconName.More, Modifier.size(18.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (script.description.isNotBlank()) Text(script.description, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun ProjectScriptBindingRow(projectName: String, projectType: ProjectType, scripts: List<BuildScriptEntity>, selectedId: String?, onSelect: (String?) -> Unit) {
    val selected = scripts.firstOrNull { it.id == selectedId }
    val items = listOf(stringResource(R.string.workshop_binding_standard)) + scripts.map { it.name }
    val selectedIndex = selected?.let { scripts.indexOf(it) + 1 } ?: 0
    RuntimeDropdownField(
        title = projectName,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index -> onSelect(if (index == 0) null else scripts.getOrNull(index - 1)?.id) },
        summary = projectType.displayName,
    )
}
@Composable
private fun ManagedScriptEditorDialog(script: BuildScriptEntity?, onDismiss: () -> Unit, onSave: (String, String, ProjectType, String) -> String?) {
    val defaultAndroidTemplate = "#!/bin/sh\n# 太墟标准 Android 构建脚本入口\nset -eu\nPROJECT_DIR=\"\${1:-.}\"\nTASK=\"\${2:-assembleDebug}\"\nexec /bin/sh /opt/taixu/scripts/taixu-build.sh android \"\$PROJECT_DIR\" \"\$TASK\"\n"
    val defaultFlutterTemplate = "#!/bin/sh\n# 太墟标准 Flutter 构建脚本入口\nset -eu\nPROJECT_DIR=\"\${1:-.}\"\nTARGET=\"\${2:-apk --debug --target-platform android-arm64}\"\nexec /bin/sh /opt/taixu/scripts/taixu-build.sh flutter \"\$PROJECT_DIR\" \$TARGET\n"
    var name by androidx.compose.runtime.saveable.rememberSaveable(script?.id) { mutableStateOf(script?.name.orEmpty()) }
    var description by androidx.compose.runtime.saveable.rememberSaveable(script?.id) { mutableStateOf(script?.description.orEmpty()) }
    var type by androidx.compose.runtime.saveable.rememberSaveable(script?.id) { mutableStateOf(runCatching { ProjectType.valueOf(script?.projectType ?: ProjectType.ANDROID.name) }.getOrDefault(ProjectType.ANDROID)) }
    var content by androidx.compose.runtime.saveable.rememberSaveable(script?.id) { mutableStateOf(script?.content ?: if (type == ProjectType.FLUTTER) defaultFlutterTemplate else defaultAndroidTemplate) }
    var saveError by androidx.compose.runtime.saveable.rememberSaveable(script?.id) { mutableStateOf<String?>(null) }
    val scriptTypes = listOf(ProjectType.ANDROID, ProjectType.FLUTTER)
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (script == null) R.string.workshop_script_editor_new else R.string.workshop_script_editor_edit), fontWeight = FontWeight.Medium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RuntimeFilledTextField(name, { name = it },
 labelText = stringResource(R.string.workshop_script_field_name), singleLine = true, modifier = Modifier.fillMaxWidth())
                RuntimeFilledTextField(description, { description = it },
 labelText = stringResource(R.string.workshop_script_field_desc), modifier = Modifier.fillMaxWidth())
                RuntimeDropdownField(
                    title = stringResource(R.string.workshop_script_type, type.displayName),
                    items = scriptTypes.map { it.displayName },
                    selectedIndex = scriptTypes.indexOf(type).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        val candidate = scriptTypes[index]
                        if (script == null && (content == defaultAndroidTemplate || content == defaultFlutterTemplate || content.isBlank())) {
                            content = if (candidate == ProjectType.FLUTTER) defaultFlutterTemplate else defaultAndroidTemplate
                        }
                        type = candidate
                    }
                )
                CodeEditorPanel(content, { content = it }, "build.sh", Modifier.fillMaxWidth().height(260.dp))
                Text(stringResource(R.string.workshop_script_api_note, "\$1", "\$2"), style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                saveError?.let { Text(it, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.error) }
            }
        },
        confirmButton = { RuntimeButton(onClick = { saveError = onSave(name, description, type, content) }, enabled = name.isNotBlank() && content.isNotBlank()) { Text(stringResource(R.string.workshop_action_save)) } },
        dismissButton = { RuntimeOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.workspace_cancel)) } },
    )
}

@Composable
fun WorkshopEnvironmentSettingsScreen(onBack: () -> Unit, viewModel: WorkshopSettingsViewModel? = previewOrNull { koinViewModel() }) {
    val draft by viewModel?.draft?.collectAsStateWithLifecycle() ?: previewStateOf(WorkshopEnvironmentDraft())
    val detected by viewModel?.detectedToolchains?.collectAsStateWithLifecycle() ?: previewStateOf(DetectedToolchains())

    RuntimeSettingsScaffold(stringResource(R.string.workshop_env_title), onBack) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionHeader(stringResource(R.string.workshop_env_paths_title), stringResource(R.string.workshop_env_paths_desc))
                    }
                    RuntimeOutlinedButton(
                        onClick = { viewModel?.rescanToolchains() },
                        enabled = !detected.isScanning,
                    ) {
                        Text(if (detected.isScanning) stringResource(R.string.workshop_env_rescanning) else stringResource(R.string.workshop_env_rescan))
                    }
                }
            }
            item {
                RuntimeCard(shape = RuntimeTheme.shapes.extraLarge) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToolchainPathDropdownField("Gradle", draft.gradlePath, detected.gradleOptions) { viewModel?.update(draft.copy(gradlePath = it)) }
                        ToolchainPathDropdownField("Java / JDK", draft.javaPath, detected.javaOptions) { viewModel?.update(draft.copy(javaPath = it)) }
                        ToolchainPathDropdownField("Android NDK", draft.ndkPath, detected.ndkOptions) { viewModel?.update(draft.copy(ndkPath = it)) }
                        ToolchainPathDropdownField("AAPT2", draft.aapt2Path, detected.aapt2Options) { viewModel?.update(draft.copy(aapt2Path = it)) }
                        PathField("Android SDK", draft.androidSdkPath) { viewModel?.update(draft.copy(androidSdkPath = it)) }
                        PathField("Flutter SDK", draft.flutterSdkPath) { viewModel?.update(draft.copy(flutterSdkPath = it)) }
                        PathField("CMake", draft.cmakePath) { viewModel?.update(draft.copy(cmakePath = it)) }
                        PathField("Ninja", draft.ninjaPath) { viewModel?.update(draft.copy(ninjaPath = it)) }
                        PathField("Gradle 缓存", draft.gradleUserHome) { viewModel?.update(draft.copy(gradleUserHome = it)) }
                        PathField("Flutter Pub 缓存", draft.pubCache) { viewModel?.update(draft.copy(pubCache = it)) }
                        PathField("Android 工具目录 / ADB", draft.toolDir) { viewModel?.update(draft.copy(toolDir = it)) }
                    }
                }
            }
            item {
                top.wkbin.taixu.ui.components.RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                    RuntimeOutlinedButton(onClick = { viewModel?.resetEnvironment() }, modifier = Modifier) { Text(stringResource(R.string.workshop_action_reset)) }
                    RuntimeButton(onClick = { viewModel?.saveEnvironment() }, modifier = Modifier) { Text(stringResource(R.string.workshop_action_save)) }
                }
            }
    }
}

@Composable
private fun ToolchainPathDropdownField(
    label: String,
    value: String,
    options: List<ToolchainOption>,
    onValueChange: (String) -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.path == value }.takeIf { it >= 0 } ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (options.isEmpty()) {
            Text(label, style = RuntimeTheme.typography.labelMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
        } else {
            RuntimeDropdownField(
                title = label,
                items = options.map { option ->
                    option.label + if (option.isDetected) " · ${stringResource(R.string.workshop_env_detected)}" else ""
                },
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index -> onValueChange(options[index].path) },
                summary = value,
            )
        }

        RuntimeFilledTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            textStyle = RuntimeTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
fun WorkshopScriptEditorScreen(type: WorkshopScriptType, onBack: () -> Unit, viewModel: WorkshopSettingsViewModel? = previewOrNull { koinViewModel() }) {
    val draft by viewModel?.draft?.collectAsStateWithLifecycle() ?: previewStateOf(WorkshopEnvironmentDraft())
    val customScripts by viewModel?.customScripts?.collectAsStateWithLifecycle() ?: previewStateOf(emptySet())
    val initialContent = remember(type, draft) { viewModel?.scriptContent(type).orEmpty() }
    var content by remember(type, draft) { mutableStateOf(initialContent) }
    val isDirty = content != initialContent
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val isCustom = type in customScripts
    // 未保存修改时拦截返回键，避免静默丢失编辑
    BackHandler(enabled = isDirty && !showUnsavedDialog) { showUnsavedDialog = true }
    Scaffold(containerColor = RuntimeTheme.colorScheme.background, topBar = { RuntimeTopBar(type.title, onBack, if (isCustom) stringResource(R.string.workshop_script_screen_custom) else stringResource(R.string.workshop_script_screen_default)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScriptPathPanel(if (isCustom) type.customPath else type.defaultPath)
            CodeEditorPanel(value = content, onValueChange = { content = it }, fileName = type.defaultPath.substringAfterLast('/'), modifier = Modifier.fillMaxWidth().weight(1f))
            top.wkbin.taixu.ui.components.RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                RuntimeOutlinedButton(onClick = { viewModel?.resetScript(type, onBack) }, modifier = Modifier) { Text(stringResource(R.string.workshop_script_reset_default)) }
                RuntimeButton(onClick = { viewModel?.saveScript(type, content, onBack) }, enabled = isDirty, modifier = Modifier) { Text(stringResource(R.string.workshop_script_save)) }
            }
        }
    }
    if (showUnsavedDialog) {
        RuntimeAlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.workshop_script_dirty_title)) },
            text = { Text(stringResource(R.string.workshop_script_dirty_message)) },
            confirmButton = {
                RuntimeButton(onClick = {
                    showUnsavedDialog = false
                    viewModel?.saveScript(type, content, onBack)
                }) { Text(stringResource(R.string.workshop_script_save_and_exit)) }
            },
            dismissButton = {
                RuntimeOutlinedButton(onClick = {
                    showUnsavedDialog = false
                    onBack()
                }) { Text(stringResource(R.string.workshop_script_discard), color = RuntimeTheme.colorScheme.error) }
            },
        )
    }
}

@Composable private fun SettingEntry(icon: RuntimeIconName, title: String, subtitle: String, onClick: () -> Unit) {
    RuntimeCard(onClick = onClick, shape = RuntimeTheme.shapes.extraLarge, containerColor = RuntimeTheme.colorScheme.surfaceContainer) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RuntimeTheme.shapes.largeIncreased, color = RuntimeTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) { RuntimeIcon(icon, Modifier.padding(9.dp), RuntimeTheme.colorScheme.onSecondaryContainer) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = RuntimeTheme.typography.titleMedium); Text(subtitle, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(20.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
    } }
}

@Composable private fun ScriptPathPanel(path: String) { RuntimeCard(containerColor = RuntimeTheme.colorScheme.surfaceContainerLow) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(stringResource(R.string.workshop_script_path_title), style = RuntimeTheme.typography.labelMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant); Text(path, style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)) } } }

@Composable
private fun PathField(label: String, value: String, onValueChange: (String) -> Unit) {
    RuntimeFilledTextField(
        value = value, onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
 labelText = label, singleLine = true,
        textStyle = RuntimeTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun CodeEditorPanel(value: String, onValueChange: (String) -> Unit, fileName: String, modifier: Modifier = Modifier) {
    val shape = RuntimeTheme.shapes.medium
    Column(modifier.background(RuntimeTheme.colorScheme.surfaceContainerLowest, shape)) {
        Row(Modifier.fillMaxWidth().background(RuntimeTheme.colorScheme.surfaceContainerLow, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuntimeIcon(RuntimeIconName.Code, Modifier.size(15.dp), RuntimeTheme.colorScheme.primary)
            Text(fileName, style = RuntimeTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = RuntimeTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = RuntimeTheme.typography.bodySmall.copy(color = RuntimeTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
            cursorBrush = SolidColor(RuntimeTheme.colorScheme.primary),
        )
    }
}
