# 📚 太墟 (TaiXu) — 关键文件索引速查 (File Index)

> 用于 AI 编码助手快速定位某个功能/类。详细架构细节见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

---

## 🤖 Agent Harness（核心调度）

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| `harness/HarnessLoop.kt` | 主循环，多会话并发 | Agent 主循环 |
| `harness/session/SessionControl.kt` / `InteractiveSessionControl.kt` | 共享控制与前台交互契约，同一执行 singleton | 会话控制接口 |
| `harness/session/SessionInputController.kt` | 锁内输入接收、任务/队列提交、启动/排队/拒绝回执 | 接收屏障 |
| `runtime/webchat/WebChatRunProtocol.kt` / `app/.../webchat/TaiXuWebChatAgentGateway.kt` | REST 输入模式、回执与错误分类；显式会话控制适配 | Web 控制边界 |
| `runtime/webchat/WebChatRunTracker.kt` / `webchat/src/taskEvents.ts` | 按关联 ID 跟踪持久化任务结果；客户端会话/任务事件隔离 | Web 结果观察 |
| `harness/core/.../AgentTurnRunner.kt` / `TurnContracts.kt` | 纯 Kotlin 轮次状态机、`prepareRequest` / `finishTurn`、预算与追问调度 | 可复用轮次核心 |
| `harness/core/.../DurableToolRunner.kt` | 意图提交 → 执行 → 结果提交；区分提交失败与工具失败 | 工具持久化屏障 |
| `harness/core/.../ToolBackend.kt` / `ToolCheckpoints.kt` | 泛型工具后端、按序等待的前置否决与后置说明；异常和取消规则 | 纯 Kotlin 工具契约 |
| `harness/core/.../SessionProjection.kt` | 存储无关的保留消息、并发补回、摘要与召回贡献规则；来源和诊断类型 | 纯 Kotlin 会话投影 |
| `harness/session/SessionContextProjector.kt` | 捕获 Lane 叶子，解码快照并选择分支窗口；检查与模型上下文共用来源 | 可检查上下文 |
| `harness/compaction/CompactionManager.kt` | 压缩持久化、`inspect` / `project` 入口与轻量摘要横幅快照 | 压缩编排 |
| `harness/TurnRunner.kt` | 将 Provider 响应与文本工具协议适配到轮次核心 | 协议适配 |
| `harness/core/.../ModelDescriptor.kt` / `harness/ModelConfig.kt` | 协议标识、无凭证能力描述；兼容现有模型档案字段 | 模型能力 |
| `harness/ProviderModelResolver.kt` | 档案/variant 选择、密钥读取、上下文容量和全局推理偏好 | 模型解析 |
| `harness/LlmApiAdapter.kt` / `ProviderTransport.kt` | 三种协议统一入口、密钥轮换、超时、诊断和取消语义 | 请求边界 |
| `harness/subagent/SubagentToolRoundRunner.kt` | Lane 工具执行、写租约与审批移交证据，共用持久化屏障 | 子任务工具回合 |
| `harness/HarnessProviderRunner.kt` | 模型能力选择、流式请求与重试、助手回复结算 | 模型回合 |
| `harness/diagnostics/*` | 最终请求体脱敏快照、内存保留预算；聊天圆环「最近请求上下文」入口 | 请求诊断（详见 [CONTEXT_DIAGNOSTICS.md](CONTEXT_DIAGNOSTICS.md)） |
| `harness/ResponsesApi.kt` / `ResponsesRequestBuilder.kt` / `ResponsesTurn.kt` | 原生 output 保存与回放、作用域校验、调用 ID 映射；主会话与子智能体共用投影 | Responses 历史（详见 [RESPONSES_HISTORY.md](RESPONSES_HISTORY.md)） |
| `harness/HarnessToolRoundRunner.kt` | 工具参数校验、单轮限额、执行与审批暂停 | 工具回合 |
| `harness/HarnessWorkspaceRecommendations.kt` | 工作区路径边界、MCP 推荐扫描与前台投影 | MCP 推荐 |
| `harness/ToolExecutor.kt` | 内置工具策略与分派；文件操作转交后端，保留审批和 PLAN 门控 | 工具执行门面 |
| `harness/ToolExecutionBoundary.kt` / `ToolExecutionRequest.kt` | 检查点观察快照、否决及说明脱敏；保留结果标识与审批状态 | 扩展控制边界 |
| `harness/WorkspaceToolBackend.kt` / `WorkspaceToolOperations.kt` | read/write/edit 语义与可替换工作区操作接口；本地实现为 `WorkspaceFileAccess` | 文件工具后端 |
| `harness/WorkspaceMutationSnapshots.kt` | 写入前后快照与大小保护，文件后端和下载工具复用 | 写入恢复证据 |
| `harness/di/harness/ToolBackendModule.kt` | 工具门面、默认检查点、工作区后端与快照的 Koin 装配 | 工具依赖装配 |
| `harness/ApprovalPolicyEngine.kt` | 工具调用的审批策略（normal / high / critical 三档） |
| `harness/ToolRoundDispatcher.kt` | 单回合多工具并发调度（mutation 互斥 / read-only 4 并发） |
| `harness/SubagentOrchestrator.kt` | 子智能体 Lane 编排 |
| `harness/mcp/*` | MCP 协议：`McpManager` / `McpHttpTransport` / `McpJsonRpc` |
| `harness/browser/BrowserMcpBootstrap.kt` | 内置 Browser MCP Server 启动 + 注册引擎 |
| `harness/mcp/server/*` | in-process MCP Server：`McpServerRuntime` / Auth / Tool+Resource Dispatcher |
| `harness/HarnessMessage.kt` | `HarnessTool` 枚举 + `ToolResult` (含 `imageAttachments`) |

## 🗂️ 模型档案迁移

模型档案的备份与迁移见 [MODEL_PROFILE_TRANSFER.md](MODEL_PROFILE_TRANSFER.md)：`tools/AiProfileBackupCodec.kt` / `AiProfileTransferFormat.kt` / `AiProfileImportWriter.kt`，持久化端口为 `core/database/AiModelRepository.kt`，界面为 `feature/settings/ModelImportDialog.kt` / `ModelExportDialog.kt`。

## 🌐 内置浏览器（Browser）

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| `core/browser/...` | `BrowserFamily / Risk / Capability / SelectionPolicy / Preferences / FileOps` | Pure Kotlin 模型 + 策略 |
| `runtime/browser/BrowserRegistry.kt` | 浏览器注册中心 interface | 多家族管理 |
| `runtime/browser/BrowserRegistryImpl.kt` | 单一 in-app WebView 实现 | 注册 / 启动 / 选 family |
| `runtime/browser/BrowserEngine.kt` | 引擎操作 interface（24 个动作）| 抽象所有浏览器动作 |
| `runtime/browser/AndroidInAppBrowserEngine.kt` | in-app WebView 引擎实现 | 全部动作落地 |
| `runtime/browser/engine/WebViewTabPool.kt` | 多 tab 复用池 | 主线程 + StateFlow |
| `runtime/browser/snapshot/SnapshotBuilder.kt` | DOM 扫描脚本 + ref 注入 | PageSnapshot 生成 |
| `runtime/browser/screenshot/ScreenshotRecorder.kt` | `view.draw` 软渲截图落 PNG | ToolImageRef |
| `runtime/browser/network/NetworkInterceptor.kt` | `shouldInterceptRequest` 拦截 | CapturedRequest |
| `runtime/browser/storage/StorageController.kt` | Cookie + local/session 操作 | WebView eval |
| `runtime/browser/secret/SecretRedactingInterceptor.kt` | 接入现有 `SecretRedactor` | 工具产物脱敏 |
| `runtime/browser/hook/HookRuleStore.kt` | Hook 规则存储（线程安全） | 规则 CRUD + payload 生成 |
| `runtime/browser/hook/HookInstaller.kt` | `TaixuBridge` + document-start 注入 | 页面侧 runtime 安装 |
| `runtime/browser/hook/HookEventPipeline.kt` | 桥事件 → 事件总线 | hook 命中/网络捕获合并 |
| `runtime/browser/hook/NetworkBodyStore.kt` | 请求/响应体 LRU 缓存 | 字节预算内 body 存取 |
| `runtime/browser/hook/hook_runtime.js`（assets） | 页面侧 fetch/XHR/fn/prop 拦截 | 网络改写 + 函数 hook |
| `runtime/browser/cdp/CdpTransport.kt` | LocalSocket → DevTools socket 传输 | `webview_devtools_remote_<pid>` |
| `runtime/browser/cdp/CdpSession.kt` | WS 帧编解码 + 命令关联/事件分发 | CDP JSON-RPC 会话 |
| `runtime/browser/cdp/CdpManager.kt` | attach 生命周期 + socket 引用计数 | `setWebContentsDebuggingEnabled` |
| `runtime/browser/cdp/CdpTabConnection.kt` | 单 tab 连接（Debugger + Fetch + Worker 子会话） | 断点/拦截路由 |
| `runtime/browser/cdp/CdpDebugController.kt` | 真断点/暂停/单步/作用域/求值 | JS 调试状态机 |
| `runtime/browser/cdp/CdpFetchInterceptor.kt` | `Fetch.requestPaused` 引擎级拦截 | Worker/子资源网络改写 |
| `runtime/browser/tools/BrowserMcpTools.kt` | `mcp__browser__*` 工具分派 + 风险等级 | 50+ tools（hook_*/debug_* 门禁） |
| `runtime/browser/tools/BrowserMcpResources.kt` | `browser://*` resources | 6 resources |
| `feature/browser/BrowserScreen.kt` | 内置浏览器 Compose 主屏 | UI 入口 |
| `feature/browser/BrowserViewModel.kt` | 持有 Registry + EventBus + Snapshot State | 状态 |
| `feature/browser/BrowserActionCard.kt` | 给 Chat 复用的产物卡（缩略图） | 跨模块复用 |
| `feature/browser/BrowserNavRoute.kt` | BrowserRoute 常量与跳转助手 | 路由入口 |
| `docs/BROWSER_DESIGN.md` | 内置浏览器设计文档 | 决策 + ADR |

## 🖥️ Linux 运行时（PRoot）

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| `runtime/.../LinuxRuntime.kt` | PRoot 启动入口 / `base` 命令面板 | 命令执行边界 |
| `runtime/.../ProcessRegistry.kt` | `process` 命令的 PID / 日志环形缓冲 | 后台进程托管 |
| `runtime/.../ProotCommandBuilder.kt` | `-b` 挂载点规范化 + Shell 注入防护 | 安全 |
| `runtime/.../WorkspaceFileService.kt` | 工作区读/写/搜/hash/zip/share | 与 file.* 工具对齐 |
| `runtime/.../shell/VT100.kt` | 终端 VT100 状态机 | 终端渲染 |

## 📱 内置无线 ADB 与 Logcat

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| `runtime/.../bridge/adb/EmbeddedAdbManager.kt` | Kadb 客户端 + mDNS 发现 + 持久密钥 | 自动发现 `_adb-tls-pairing` / `_adb-tls-connect`、一次配对、自动重连、Logcat 抓取 |
| `runtime/.../bridge/HostBridge.kt` | 沙箱 HTTP 桥接 (127.0.0.1:7980) | 提供 `/api/logcat`、`/api/shell`（内置无线 ADB 回退）与静默 APK 安装 |
| `app/src/main/assets/bin/logcat-grabber` | 沙箱内置 CLI 日志工具 | `logcat-grabber` / `logcat-tail` / `logcat-export` 脚本资产 |
| `feature/developer/.../AdbLogcatScreen.kt` | 独立无线 ADB 与日志工作台 | 系统保活与诊断一级直达：配对码输入、mDNS 探测、多维 Logcat 过滤与复制 |
| `feature/developer/.../DeveloperScreen.kt` | 开发者控制台 | 包含底层健康监控、无线 ADB 状态卡片、工具源更新等 |
| `harness/ToolExecutor.kt` | `host.logcat` 分派 | 优先使用无线 ADB，失败后回退 Shizuku/Root |

## 🌿 Git 分支管理（feature:git）

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| `feature/git/.../GitManager.kt` | JGit 封装（MGit 同款技术栈） | 分支列表/切换/新建/删除、提交树泳道算法、push/pull 进度、友好错误映射 |
| `feature/git/.../GitCredentialsStore.kt` | 按 host 的 HTTPS 凭据存储 | Token 经 SecretManager（AndroidKeyStore AES/GCM）加密后落 JSON |
| `feature/git/.../GitViewModel.kt` | GitScreen 状态机 | 项目绑定 / 操作互斥 / 进度上抛 |
| `feature/git/.../GitScreen.kt` | 分支管理页（分支+提交记录双页签） | 入口：智枢顶部工具条「仓库」 |
| `feature/git/.../GitCommitGraph.kt` | Canvas 泳道提交图 | 穿线/合并/分叉斜线 + 多色节点 |
| `feature/chat/.../ChatWorkbenchPanels.kt` | 顶部工具条「仓库」入口 | `onOpenRepository` 可选回调模式 |
| `feature/navigation/.../TaiXuNavHost.kt` | `GitRepositoryDestination(projectName)` | 路由注册 |

> JGit 在宿主侧直接打开工作区仓库（`RepositoryBuilder` + 空的 system/user 配置规避 Android 路径问题），不依赖沙箱内 git 安装。

## 🤝 Web Reverse MCP 参考

项目内置浏览器/MCP 设计借鉴自 `mnjh666/WebReverse-MCP`（模块切分 / 工具动词集 / 风险矩阵），不复用其代码。

## 💾 本地数据备份与恢复

完整说明见 [LOCAL_BACKUP.md](LOCAL_BACKUP.md)。

| 文件 | 说明 |
|---|---|
| `tools/backup/LocalBackupService.kt` | 备份/恢复协调器、原子恢复日志、启动挂单恢复 |
| `tools/backup/BackupArchive.kt` | ZIP 归档读写、路径安全校验、SHA-256 摘要 |
| `tools/backup/BackupResources.kt` | 资源文件收集与路径重写 |
| `core/database/BackupRecordPolicy.kt` | 可备份表清单、运行时字段归零、记录差集 |
| `core/database/BackupRestoreReceipt.kt` | 恢复收据实体 + DB Migration 53→54 |
| `core/database/DatabaseBackupRepository.kt` | Room 事务级快照、结构校验和合并 |
| `core/datastore/BackupPreferences.kt` | 可移植偏好快照、校验、原子回滚 |
| `core/model/LocalBackup.kt` | `LocalBackupManifest` / `BackupRecords` / `BackupPreview` 数据类 |
| `feature/settings/LocalBackupDialog.kt` | 导出/导入 UI（含预览、偏好复选框）|
| `feature/settings/LocalBackupViewModel.kt` | 备份 UI ViewModel |
| `app/BackupStartupRecovery.kt` | Application 启动检查并完成挂起恢复 |
