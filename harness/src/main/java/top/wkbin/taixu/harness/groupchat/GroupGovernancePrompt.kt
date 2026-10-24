package top.wkbin.taixu.harness.groupchat

import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupGovernance

internal fun formatGovernance(policy: GroupGovernance, members: List<Bot>): String = buildString {
    val names = members.associate { it.id to it.name }
    appendLine("【程序强制的团队审议】")
    policy.rules.forEach {
        appendLine("规则 ${it.id}「${it.name}」：${names[it.authorId]}起草 → ${names[it.reviewerId]}审核 → ${names[it.executorId]}及下属执行；最多${it.maxRevisions}轮，争议交群主控裁决。")
    }
    appendLine("先 inspect-governance 查看版本与审核依据。起草者用 submit-proposal(rule_id, version=0, content) 提交，修改须传 proposal_id 与当前version。")
    appendLine("向审核者派单传 proposal_id，要求其调用 review-proposal(proposal_id, version, decision, reason)。审核任务COMPLETED不等于APPROVED。")
    appendLine("驳回后由原起草者修改并重新审核；已通过方案修改后旧批准失效。只有APPROVED的准确版本可以派给执行成员，assign-task须传proposal_id，下级继承。")
    appendLine("旧执行任务绑定的方案过期后不得改用新版冒充原批准；核验已有副作用后，为新批准版本创建新执行任务。")
    appendLine("本群普通发言、起草和审核仅允许阅读和团队协调；代码写入、Shell、宿主、外部MCP与通用子智能体只允许已批准的执行任务，不能在主控会话兜底绕过。")
}
