package top.wkbin.taixu.ui.settings.search

import top.wkbin.taixu.ui.components.RuntimeIconName

internal val hashlineSearchItem = SettingsSearchItem(
    id = "agent_hashline",
    title = "Hashline 锚点编辑",
    subtitle = "read 行锚、edit ops、legacy oldText 闸门与跨启动累计计数",
    category = SettingsSearchCategory.AGENT,
    target = SettingsSearchTarget.AGENT_EXECUTION,
    icon = RuntimeIconName.Code,
    keywords = listOf(
        "hashline", "anchor", "ops", "oldText", "edit", "str_replace", "legacy",
        "行锚", "锚点", "哈希编辑", "文本替换", "观测", "maodian", "bianji",
    ),
    breadcrumb = "智能体与 AI 模型 > Agent 执行参数 > Hashline 编辑",
)
