package top.wkbin.taixu.harness.subagent

import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.UserMessage

/** 子任务指派模板的首行标记，须与 assets/prompts/subagent_task.md 首行保持一致。 */
internal const val SUBAGENT_TASK_MARKER = "【子智能体任务指派】"

/**
 * Lane 自身历史在共享树路径中的起点。
 *
 * Lane 从父会话分叉，路径前缀是父对话（含父级 UserMessage），其后才是本 Lane 的任务指派。
 * 续跑（task_id）会再追加一条 UserMessage：若按「最后一条 UserMessage」截断，原任务、角色提示词
 * 与前序工具记录会被整段丢弃，模型只看到续跑指令。因此锚定首条任务指派消息；找不到标记时
 * （双智能体/workflow Lane 不用此模板，或历史超出读取窗口）回退最后一条 UserMessage。
 *
 * @return 起点下标；路径中没有任何 UserMessage 时返回 null，由调用方决定空历史语义。
 */
internal fun subagentTaskStartIndex(messages: List<HarnessMessage>): Int? {
    val marker = messages.indexOfFirst {
        it is UserMessage && it.text.trimStart().startsWith(SUBAGENT_TASK_MARKER)
    }
    if (marker >= 0) return marker
    return messages.indexOfLast { it is UserMessage }.takeIf { it >= 0 }
}