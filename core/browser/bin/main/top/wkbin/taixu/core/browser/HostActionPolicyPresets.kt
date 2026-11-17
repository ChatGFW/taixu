package top.wkbin.taixu.core.browser

/**
 * 设置页一键填入的 CEL-lite 策略草稿（仍需用户点保存才持久化/生效）。
 */
data class HostActionPolicyPreset(
    val id: String,
    val title: String,
    val description: String,
    val document: ActionPolicyDocument,
)

object HostActionPolicyPresets {
    val DEFAULT = HostActionPolicyPreset(
        id = "default",
        title = "出厂默认",
        description = "deny 空 + allow true",
        document = ActionPolicyDocument.DEFAULT,
    )

    val BLOCK_UNATTENDED_NAV = HostActionPolicyPreset(
        id = "block_unattended_nav",
        title = "拒绝对无人值守导航",
        description = "排期/委派/工作流不得 browser.navigate / open",
        document = ActionPolicyDocument(
            deny = listOf(
                "initiator.kind == 'schedule' && (tool.name == 'browser.navigate' || tool.name == 'browser.open')",
                "initiator.kind == 'handoff' && (tool.name == 'browser.navigate' || tool.name == 'browser.open')",
                "initiator.kind == 'workflow' && (tool.name == 'browser.navigate' || tool.name == 'browser.open')",
            ),
            allow = listOf("true"),
        ),
    )

    val ALLOWLIST_EXAMPLE = HostActionPolicyPreset(
        id = "allowlist_example",
        title = "仅允许 example.com",
        description = "未带 url 的工具按当前标签页 host 评估；无法确定 host 时拒绝",
        document = ActionPolicyDocument(
            deny = listOf(
                "page.host != '' && page.host != 'example.com' && !contains(page.host, 'example.com')",
            ),
            allow = listOf("true"),
        ),
    )

    val ALL = listOf(DEFAULT, BLOCK_UNATTENDED_NAV, ALLOWLIST_EXAMPLE)
}
