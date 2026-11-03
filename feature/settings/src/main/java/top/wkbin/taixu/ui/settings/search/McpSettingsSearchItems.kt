package top.wkbin.taixu.ui.settings.search

import top.wkbin.taixu.ui.components.RuntimeIconName

internal val mcpProtocolSearchItems = listOf(
    SettingsSearchItem(
        id = "agent_mcp_builtin_servers",
        title = "内置 SQLite / Git / Fetch MCP Server",
        subtitle = "管理沙箱内内置的轻量化数据与网络请求 MCP 服务端",
        category = SettingsSearchCategory.AGENT,
        target = SettingsSearchTarget.MCP_SETTINGS,
        icon = RuntimeIconName.Server,
        keywords = listOf("sqlite", "git", "fetch", "filesystem", "内置服务", "mcp server"),
        breadcrumb = "智能体与 AI 模型 > MCP 协议 > 内置服务",
    ),
    SettingsSearchItem(
        id = "agent_mcp_host_action_policy",
        title = "主机动作策略（CEL-lite）",
        subtitle = "浏览器工具 deny/allow 规则；保存即时生效并跨启动保留",
        category = SettingsSearchCategory.AGENT,
        target = SettingsSearchTarget.MCP_SETTINGS,
        icon = RuntimeIconName.Shield,
        keywords = listOf(
            "cel", "policy", "deny", "allow", "host", "browser policy",
            "主机策略", "动作策略", "allowlist", "denylist", "cel-lite",
        ),
        breadcrumb = "智能体与 AI 模型 > MCP 协议 > 主机动作策略",
    ),
)
