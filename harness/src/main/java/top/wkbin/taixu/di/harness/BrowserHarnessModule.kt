package top.wkbin.taixu.di.harness

import org.koin.dsl.module
import top.wkbin.taixu.harness.browser.BrowserControlAuditBridge
import top.wkbin.taixu.harness.browser.BrowserMcpBootstrap
import top.wkbin.taixu.harness.mcp.server.McpServerRuntime

/** 浏览器 MCP 引导与人工接管审计，从主 Koin 模块拆出以守住行数棘轮。 */
val browserHarnessModule = module {
    single<BrowserControlAuditBridge> { BrowserControlAuditBridge(auditor = get()) }

    single<BrowserMcpBootstrap> {
        BrowserMcpBootstrap(
            context = get(),
            runtime = lazy { get<McpServerRuntime>() },
            registry = get(),
            browserPrefs = get(),
            controlAuditBridge = get(),
        )
    }
}
