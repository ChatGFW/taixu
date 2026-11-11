package top.wkbin.taixu.ui.settings.search

import top.wkbin.taixu.ui.components.RuntimeIconName

internal val chatTopToolbarSearchItem = SettingsSearchItem(
    id = "appearance_chat_top_toolbar",
    title = "聊天顶部快捷按钮",
    subtitle = "自定义智枢顶部右侧置顶入口，最多 3 个，「更多」始终保留",
    category = SettingsSearchCategory.APPEARANCE,
    target = SettingsSearchTarget.APPEARANCE_SETTINGS,
    icon = RuntimeIconName.More,
    keywords = listOf("toolbar", "pin", "shortcut", "顶部按钮", "快捷按钮", "置顶", "更多", "工作台", "dingbu", "kuaijie"),
    breadcrumb = "外观与体验 > 聊天顶部快捷按钮",
)
