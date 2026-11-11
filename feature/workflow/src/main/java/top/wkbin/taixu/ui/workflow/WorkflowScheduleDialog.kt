package top.wkbin.taixu.ui.workflow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.FilterChip
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.WorkflowScheduleEntity
import top.wkbin.taixu.core.model.workflow.WorkflowDefinition
import top.wkbin.taixu.core.model.workflow.WorkflowScheduleRepeat
import top.wkbin.taixu.harness.workflow.WorkflowScheduleRepository
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeNumberPicker
/** 重复方式的展示文案（列表与对话框共用）。 */
fun scheduleRepeatLabel(entity: WorkflowScheduleEntity): String = when (WorkflowScheduleRepeat.valueOf(entity.repeatType)) {
    WorkflowScheduleRepeat.DAILY -> "每天 ${"%02d".format(entity.hour ?: 0)}:${"%02d".format(entity.minute ?: 0)}"
    WorkflowScheduleRepeat.INTERVAL -> "每 ${entity.intervalMinutes ?: WorkflowScheduleRepository.MIN_INTERVAL_MINUTES} 分钟"
    WorkflowScheduleRepeat.ONCE -> "一次性 · ${
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entity.onceAtEpochMillis ?: 0L))
    }"
}

/**
 * 定时计划新建/编辑对话框：选工作流 + 重复方式（每天 HH:mm / 每 N 分钟 / 一次性时刻）+
 * 变量（KEY=VALUE 行式）+ 工作区 + 可选模型。变量与时刻在保存前做格式校验；
 * 编辑时保留原 id 与启用状态，避免 upsert 产生孤儿行。
 */
@Composable
fun WorkflowScheduleEditDialog(
    definitions: List<WorkflowDefinition>,
    models: List<AiModelEntity>,
    existing: WorkflowScheduleEntity?,
    onDismiss: () -> Unit,
    onSave: (WorkflowScheduleEntity) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var workflowId by remember { mutableStateOf(existing?.workflowId ?: definitions.firstOrNull()?.id.orEmpty()) }
    var repeat by remember {
        mutableStateOf(existing?.let { WorkflowScheduleRepeat.valueOf(it.repeatType) } ?: WorkflowScheduleRepeat.DAILY)
    }
    var hour by remember { mutableStateOf((existing?.hour ?: 9).toString()) }
    var minute by remember { mutableStateOf((existing?.minute ?: 0).toString()) }
    var intervalMinutes by remember {
        mutableStateOf((existing?.intervalMinutes ?: 60).toString())
    }
    var onceAt by remember {
        mutableStateOf(
            existing?.onceAtEpochMillis?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
            }.orEmpty(),
        )
    }
    var variablesText by remember { mutableStateOf(formatVariables(existing?.variablesJson)) }
    var workspacePath by remember {
        mutableStateOf(existing?.workspacePath?.ifBlank { "/workspace" } ?: "/workspace")
    }
    var modelId by remember { mutableStateOf(existing?.modelId.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建定时计划" else "编辑定时计划") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                RuntimeFilledTextField(
                    value = name,
                    onValueChange = { name = it },
                    labelText = "计划名称",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val workflowOptions = definitions.map { it.id to it.name }
                RuntimeDropdownField(
                    title = "目标工作流",
                    items = workflowOptions.map { it.second }.ifEmpty { listOf("请选择") },
                    selectedIndex = workflowOptions.indexOfFirst { it.first == workflowId }.coerceAtLeast(0),
                    onSelectedIndexChange = { index -> workflowOptions.getOrNull(index)?.let { workflowId = it.first } },
                    enabled = workflowOptions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = repeat == WorkflowScheduleRepeat.DAILY, onClick = { repeat = WorkflowScheduleRepeat.DAILY }, label = { Text("每天") })
                    FilterChip(selected = repeat == WorkflowScheduleRepeat.INTERVAL, onClick = { repeat = WorkflowScheduleRepeat.INTERVAL }, label = { Text("周期") })
                    FilterChip(selected = repeat == WorkflowScheduleRepeat.ONCE, onClick = { repeat = WorkflowScheduleRepeat.ONCE }, label = { Text("一次性") })
                }
                when (repeat) {
                    WorkflowScheduleRepeat.DAILY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("时", style = RuntimeTheme.typography.labelMedium)
                            RuntimeNumberPicker(
                                value = hour.toIntOrNull()?.coerceIn(0, 23) ?: 9,
                                onValueChange = { hour = it.toString() },
                                range = 0..23,
                                label = { "%02d".format(it) },
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("分", style = RuntimeTheme.typography.labelMedium)
                            RuntimeNumberPicker(
                                value = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                                onValueChange = { minute = it.toString() },
                                range = 0..59,
                                label = { "%02d".format(it) },
                            )
                        }
                    }
                    WorkflowScheduleRepeat.INTERVAL -> RuntimeFilledTextField(
                        value = intervalMinutes, onValueChange = { intervalMinutes = it.filter(Char::isDigit).take(4) },
                        labelText = "间隔分钟（≥15）", singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    WorkflowScheduleRepeat.ONCE -> RuntimeFilledTextField(
                        value = onceAt, onValueChange = { onceAt = it },
                        labelText = "触发时间（yyyy-MM-dd HH:mm）", singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                RuntimeFilledTextField(
                    value = variablesText,
                    onValueChange = { variablesText = it },
                    labelText = "变量（每行 KEY=VALUE，可空）",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = RuntimeTheme.typography.bodySmall.fontSize),
                )
                RuntimeFilledTextField(
                    value = workspacePath,
                    onValueChange = { workspacePath = it },
                    labelText = "工作区路径",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val modelOptions = listOf("" to "全局默认") + models.map { it.id to it.name }
                RuntimeDropdownField(
                    title = "模型（可选，默认走全局）",
                    items = modelOptions.map { it.second },
                    selectedIndex = modelOptions.indexOfFirst { it.first == modelId }.coerceAtLeast(0),
                    onSelectedIndexChange = { index -> modelOptions.getOrNull(index)?.let { modelId = it.first } },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = RuntimeTheme.colorScheme.error, style = RuntimeTheme.typography.bodySmall) }
            }
        },
        dismissButton = { RuntimeTextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            RuntimeTextButton(
                onClick = {
                    val parsed = validateAndBuild(
                        existing = existing,
                        name = name, workflowId = workflowId, definitions = definitions,
                        repeat = repeat, hour = hour, minute = minute,
                        intervalMinutes = intervalMinutes, onceAt = onceAt,
                        variablesText = variablesText, workspacePath = workspacePath, modelId = modelId,
                    )
                    parsed.fold(
                        onSuccess = { onSave(it) },
                        onFailure = { error = it.message },
                    )
                },
            ) { Text("保存") }
        },
    )
}

private fun validateAndBuild(
    existing: WorkflowScheduleEntity?,
    name: String,
    workflowId: String,
    definitions: List<WorkflowDefinition>,
    repeat: WorkflowScheduleRepeat,
    hour: String,
    minute: String,
    intervalMinutes: String,
    onceAt: String,
    variablesText: String,
    workspacePath: String,
    modelId: String,
): Result<WorkflowScheduleEntity> {
    if (name.isBlank()) return Result.failure(IllegalArgumentException("请填写计划名称"))
    if (definitions.none { it.id == workflowId }) return Result.failure(IllegalArgumentException("请选择目标工作流"))
    val variables = parseVariables(variablesText).fold(
        onSuccess = { it },
        onFailure = { return Result.failure(it) },
    )
    val now = System.currentTimeMillis()
    return when (repeat) {
        WorkflowScheduleRepeat.DAILY -> {
            val h = hour.toIntOrNull() ?: return Result.failure(IllegalArgumentException("时必须是数字"))
            val m = minute.toIntOrNull() ?: return Result.failure(IllegalArgumentException("分必须是数字"))
            if (h !in 0..23 || m !in 0..59) return Result.failure(IllegalArgumentException("时刻超出范围"))
            Result.success(buildEntity(existing, name, workflowId, repeat, h, m, null, null, variables, workspacePath, modelId, now))
        }
        WorkflowScheduleRepeat.INTERVAL -> {
            val minutes = intervalMinutes.toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("间隔必须是数字"))
            if (minutes < WorkflowScheduleRepository.MIN_INTERVAL_MINUTES) {
                return Result.failure(IllegalArgumentException("间隔至少 ${WorkflowScheduleRepository.MIN_INTERVAL_MINUTES} 分钟"))
            }
            Result.success(buildEntity(existing, name, workflowId, repeat, null, null, minutes, null, variables, workspacePath, modelId, now))
        }
        WorkflowScheduleRepeat.ONCE -> {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }
            val target = try {
                parser.parse(onceAt.trim())?.time
            } catch (_: ParseException) {
                null
            } ?: return Result.failure(IllegalArgumentException("触发时间格式应为 yyyy-MM-dd HH:mm"))
            if (target <= now) return Result.failure(IllegalArgumentException("触发时间必须晚于当前时刻"))
            Result.success(buildEntity(existing, name, workflowId, repeat, null, null, null, target, variables, workspacePath, modelId, now))
        }
    }
}

private fun buildEntity(
    existing: WorkflowScheduleEntity?,
    name: String,
    workflowId: String,
    repeat: WorkflowScheduleRepeat,
    hour: Int?,
    minute: Int?,
    intervalMinutes: Int?,
    onceAtEpochMillis: Long?,
    variables: Map<String, String>,
    workspacePath: String,
    modelId: String,
    now: Long,
): WorkflowScheduleEntity = WorkflowScheduleEntity(
    id = existing?.id ?: "sched_${UUID.randomUUID().toString().take(12)}",
    workflowId = workflowId,
    name = name,
    enabled = existing?.enabled ?: true,
    repeatType = repeat.name,
    hour = hour,
    minute = minute,
    intervalMinutes = intervalMinutes,
    onceAtEpochMillis = onceAtEpochMillis,
    variablesJson = Json.encodeToString(variables),
    workspacePath = workspacePath,
    modelId = modelId.ifBlank { null },
    modelVariant = existing?.modelVariant,
    lastExecutionId = existing?.lastExecutionId,
    lastRunAt = existing?.lastRunAt,
    nextRunAt = existing?.nextRunAt,
    createdAt = existing?.createdAt ?: now,
)

private fun parseVariables(text: String): Result<Map<String, String>> {
    val map = linkedMapOf<String, String>()
    text.lines().forEach { raw ->
        val line = raw.trim().takeIf { it.isNotEmpty() } ?: return@forEach
        val index = line.indexOf('=')
        if (index <= 0) return Result.failure(IllegalArgumentException("变量行格式错误：$line"))
        map[line.substring(0, index).trim()] = line.substring(index + 1).trim()
    }
    return Result.success(map)
}

private fun formatVariables(variablesJson: String?): String {
    if (variablesJson.isNullOrBlank()) return ""
    return runCatching {
        Json.decodeFromString<Map<String, String>>(variablesJson)
            .entries.joinToString("\n") { "${it.key}=${it.value}" }
    }.getOrDefault("")
}

