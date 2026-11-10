package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.AgentSubagent
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.settings.LocalizedText as Text

@Composable
internal fun SubagentEditorDialog(
    profile: AgentSubagent?,
    models: List<AiModelEntity>,
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (
        roleId: String,
        name: String,
        description: String,
        systemPrompt: String,
        defaultModelId: String?,
        defaultModelVariant: String?,
    ) -> Unit,
) {
    var roleId by rememberSaveable(profile?.id) { mutableStateOf(profile?.id.orEmpty()) }
    var name by rememberSaveable(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var description by rememberSaveable(profile?.id) { mutableStateOf(profile?.description.orEmpty()) }
    var prompt by rememberSaveable(profile?.id) { mutableStateOf(profile?.systemPrompt.orEmpty()) }
    var defaultModelId by rememberSaveable(profile?.id) { mutableStateOf(profile?.defaultModelId) }
    var defaultModelVariant by rememberSaveable(profile?.id) { mutableStateOf(profile?.defaultModelVariant) }
    val normalizedId = roleId.trim().lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .trim('_')
    val duplicateId = normalizedId.isNotBlank() && normalizedId != profile?.id && normalizedId in existingIds
    val canSave = normalizedId.isNotBlank() && name.isNotBlank() && prompt.isNotBlank() && !duplicateId

    RuntimeSelectionSheet(
        title = if (profile == null) "新增子智能体角色" else "编辑子智能体角色",
        onDismissRequest = onDismiss,
        actions = {
            RuntimeActionRow(modifier = Modifier.fillMaxWidth(), spacing = RuntimeSpacing.dialogActionGap, scrollable = true) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = {
                    onConfirm(normalizedId, name, description, prompt, defaultModelId, defaultModelVariant)
                }, enabled = canSave) { Text(if (profile == null) "添加并启用" else "保存") }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap)) {
                RuntimeFilledTextField(
                    value = roleId,
                    onValueChange = { roleId = it },
                    labelText = resolveLegacyString("角色标识"),
                    supportingText = {
                        Text(if (duplicateId) "该角色标识已存在" else "仅支持英文、数字、下划线和连字符")
                    },
                    isError = duplicateId,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                RuntimeFilledTextField(
                    value = name,
                    onValueChange = { name = it },
                    labelText = resolveLegacyString("显示名称"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                RuntimeFilledTextField(
                    value = description,
                    onValueChange = { description = it },
                    labelText = resolveLegacyString("职责简介"),
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                RuntimeFilledTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    labelText = resolveLegacyString("角色指导词"),
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val modelOptions = buildList {
                        add(Triple(null, null, "跟随主智能体"))
                        models.forEach { model ->
                            model.model.split(',')
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .forEach { variant ->
                                    add(Triple(model.id, variant, "${model.name} · ${model.provider} · $variant"))
                                }
                        }
                        if (defaultModelId != null && models.none { it.id == defaultModelId }) {
                            add(Triple(defaultModelId, defaultModelVariant, "模型档案已删除"))
                        }
                    }
                    RuntimeDropdownField(
                        title = "默认模型",
                        items = modelOptions.map { it.third },
                        selectedIndex = modelOptions.indexOfFirst { (modelId, variant, _) ->
                            modelId == defaultModelId && variant == defaultModelVariant
                        }.coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            modelOptions.getOrNull(index)?.let { (modelId, variant, _) ->
                                defaultModelId = modelId
                                defaultModelVariant = variant
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "任务派发时显式指定的模型仍会优先于此设置",
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
    }
}
