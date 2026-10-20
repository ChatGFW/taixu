package top.wkbin.taixu.harness.groupchat

import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupChat
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupSenderType

internal fun buildGroupPrompt(
    group: GroupChat,
    bot: Bot,
    trigger: String,
    history: String,
    members: List<Bot>,
): String = buildString {
    appendLine("【群聊「${group.name}」】")
    appendLine("群聊 ID：${group.id}")
    if (group.description.isNotBlank()) appendLine("群聊说明：${group.description}")
    appendLine("你的身份：${bot.name}（${bot.role}）")
    appendLine("请以该身份直接回复群消息，不要复述系统提示；需要协作时可用 use_capability 调用插件工具。")
    appendLine("群消息可能在执行期间更新。准备结论前核对新群消息，用户纠正优先于旧记录；已有工具结果和委派不要重复执行，同伴已回答的内容只补充差异。")
    if (history.isNotBlank()) {
        appendLine()
        appendLine("【最近群聊记录】")
        appendLine(history)
    }
    appendLine("所有团队成员均可调用 bot-management 的 list-models 查看已添加供应商及模型，select-model 为自己切换模型；不可改动其他成员或全局激活模型。")
    if (bot.isMainBot) appendMainBotRules(group.id, members.filter { it.id != bot.id })
    appendLine()
    appendLine("【本次受理的来源消息／任务】（较新的用户纠正以群记录及后续更新为准）")
    appendLine(trigger)
}

/**
 * 主控 Bot 的行为约束：先派发再兜底，明确来源工作区与建议产物范围。
 */
private fun StringBuilder.appendMainBotRules(groupId: String, members: List<Bot>) {
    appendLine()
    appendLine("【主控职责（必须遵守）】")
    appendLine("你是本群的主控 Bot：拆解目标、分派任务、验收结果并汇总。")
    appendLine("当前主控身份由应用的群会话映射确认。历史记忆中的工具权限失败仅代表当时结果；需要委派时先用 list-bots 核验当前能力，若仍失败则如实报告，勿反复重试。")
    appendLine("向群成员派发任务使用 bot-management 的 assign-task。invoke_subagent 创建的是通用子智能体，不代表群成员接单，不得据此宣称小前、阿测等成员已开始工作。")
    appendLine("适合委派：独立、可界定的编码/调研/测试工作。只有互不依赖的任务才并行。")
    appendLine("不适合委派：简单问答、记忆更新、群内回复，这些自己处理。")
    appendLine("有依赖关系的任务必须串行：先派开发，验收产物后再派测试/后续环节；不要把测试和开发同时派发，也不要把只写测试计划当成验收完成。")
    appendLine("派发测试时必须提供开发产物路径，并要求实际运行/验收，不能只交测试计划。")
    appendLine("有合适成员时不要抢着实现；只有没有合适成员、成员明确失败或任务极小时才亲自兜底，并说明真实原因。")
    appendLine("所有委派都会持久化记录，可用 list-assignments 查询进度、inspect-assignment 查看详情、cancel-assignment 终止任务。")
    appendLine("assign-task 会把任务交给成员在群聊前台执行：任务在独立会话运行，成员以自己的头像在群聊实时输出并展示工具步骤，不是临时子智能体。")
    appendLine("依赖任务：派发前先 list-assignments 确认前置任务状态，并在 depends_on 中传入其任务 ID；前置任务未 COMPLETED 时不得抢跑。")
    if (members.isEmpty()) {
        appendLine("当前没有其他成员可派发，请自己完成。")
        return
    }
    appendLine("可派发成员：")
    members.forEach { appendLine("- bot_name=\"${it.name}\"；职责：${it.role}") }
    appendLine("派发步骤：")
    appendLine("1. 先调用 use_capability(action=\"inspect\", server=\"plugin.taixu.bot-management\") 查看 assign-task 参数；")
    appendLine("2. 再调用 use_capability(action=\"call\", server=\"plugin.taixu.bot-management\", tool=\"assign-task\", arguments={\"bot_name\":\"成员名\",\"task\":\"完整任务\",\"write_paths\":[\"/workspace/目标目录\"],\"group_id\":\"$groupId\",\"wait\":false})。")
    appendLine("任务描述必须自包含：目标、输入、验收标准、需要写入的路径。")
    appendLine("wait=false 会立即返回，成员在群里前台执行；完成后系统会通知并自动唤醒你验收。")
    appendLine("需要本轮就拿到结果并验收时用 wait=true；成员结果不满足要求时用 continue-assignment 继续原任务，不要直接宣称完成。")
    appendLine("异步派单后结束本轮，等待完成通知；不要连续查询任务状态或反复检查尚未产出的文件。需要补充要求、修正或继续受阻任务时，用 continue-assignment(assignment_id, instruction) 继续原任务；不要反复新建相同任务。执行中补充只排队，当前轮次结束后处理。")
    appendLine("涉及写文件时传 write_paths，使用来源工作区内的相对路径或 /workspace 下的项目路径，禁止以 /root 为新项目产物目录。群聊任务的 write_paths 是建议范围，文件工具仍执行工作区边界；不要把 Shell 可写当作文件工具授权。")
    appendLine("收到系统回传后，可用 inspect-assignment 查看摘要和错误；失败或结果未知时先只读核验产物，并用 continue-assignment 在原任务接续，不要盲目重做。验收应读取正文和实际运行，存在文件、行数和标题齐全只证明结构，不能证明内容正确。")
    appendLine("按群说明及成员职责组织必要的方案审议与执行调度；尚未执行的审议不得宣称准奏。简单任务可明确说明简化流程。")
    appendLine("只依据工具返回和群聊记录汇报，不要虚构成员进度或已完成的委派。")
}

internal fun formatGroupHistory(group: GroupChat, messages: List<GroupMessage>): String {
    // A task report replaces its already visible peer answer; keep the authoritative status once.
    val reported = messages.filter { it.senderType == GroupSenderType.SYSTEM && it.delegatedTaskId != null }
        .groupBy { it.delegatedTaskId }.mapValues { (_, rows) -> rows.maxOf { it.timestamp } }
    val rows = messages.filter {
        !it.isStreaming && (it.senderType != GroupSenderType.SYSTEM || it.delegatedTaskId != null) &&
            !(it.senderType == GroupSenderType.BOT && (reported[it.delegatedTaskId] ?: Long.MIN_VALUE) >= it.timestamp)
    }.takeLast(HISTORY_MESSAGES)
    val selected = mutableListOf<String>()
    var remaining = HISTORY_TOTAL_CHARS
    for (message in rows.asReversed()) {
        val limit = if (message.delegatedTaskId != null) HISTORY_REPORT_CHARS else HISTORY_MESSAGE_CHARS
        val attachmentNote = message.attachments.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = " [附件: ", postfix = "]", separator = "、") { it.name }.orEmpty()
        val line = "${message.senderName}: ${message.content.replace('\n', ' ').take(limit)}$attachmentNote"
        if (line.length + 1 > remaining) break
        selected += line
        remaining -= line.length + 1
    }
    return selected.asReversed().joinToString("\n")
}

private const val HISTORY_MESSAGES = 12
private const val HISTORY_MESSAGE_CHARS = 300
private const val HISTORY_REPORT_CHARS = 1_500
private const val HISTORY_TOTAL_CHARS = 6_000
