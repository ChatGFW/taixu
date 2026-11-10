# 🏗️ 太墟 (TaiXu) — 系统架构与模块拓扑 (Architecture & Modules)

---

## 1. 🚀 项目定位与技术栈摘要 (Tech Stack Snapshot)

- **核心定位**：在 **Android 无 Root 用户态** 下，基于 **PRoot** 运行完整的 **Linux 沙箱**，并深度集成 **AI Agent Harness 智能体引擎**、**原生 PTY 终端**、**工作区文件管理** 与 **Miuix 原生 UI**。
- **Android / UI 层**：
  - **主工程构建链**：Gradle 9.9.0 / AGP 9.4.1 / Kotlin 2.4.21 / KSP 2.3.11 / compileSdk 37.1（`compileSdk=37` + `compileSdkMinor=1`）/ targetSdk 37 / minSdk 29 / NDK 声明 `30.0.15729638`（构建时由 `taixu-android-ndk.gradle` 覆盖为沙箱实际 r30 `30.0.16248370`）
    （取值位置：`gradle/libs.versions.toml:5-7`、`build-logic/convention/.../KotlinAndroid.kt:18-19`、`app/build.gradle.kts:26,30-31`；Gradle 来自 `gradle/wrapper/gradle-wrapper.properties`）
  - **语言**：Kotlin（100% Kotlin + Jetpack Compose）
  - **UI 风格**：Miuix（原生组件、HCT 动态配色、触觉反馈、双栏自适应）
  - **依赖注入**：Koin 4.2.2（显式 Kotlin DSL）
  - **状态与异步**：Kotlin Coroutines, StateFlow, SharedFlow
  - **本地存储**：Jetpack DataStore (Preferences), Room Database (SQLite)
- **底层运行时 (Runtime & Native)**：
  - **C/C++ JNI**：`app/src/main/cpp/pty.c` (openpty, fork, exec, ioctl 终端 PTY 桥接，编译为 `libtaixu_pty.so`, `arm64-v8a`)
  - **Linux 容器**：内置 PRoot 引擎，启动 Debian rootfs
  - **宿主存储映射**：PRoot `-b <host>:<guest>` 原生绑定机制（Download, Documents, /sdcard）
- **AI 智能体引擎 (Agent Harness)**：
  - **协议兼容**：OpenAI Chat Completion API 标准（支持流式 SSE、`reasoning_content` 思考过程、函数工具调用 `tool_calls`）
  - **内置工具**：`read` / `write` / `edit` / `base` / `process`，并可扩展 MCP 与子智能体工具
  - **命令生命周期**：`base` 用于有界前台命令，默认超时由用户配置；`process` 通过 Runtime 进程注册表托管跨工具调用持续运行的命令
  - **本地端侧模型**：沙箱内原生支持 `llama.cpp` 与 `Ollama`，无需 API Key
- **沙箱 Android 开发套件（不要与主工程构建链混淆）**：Gradle 9.9.0 / JDK 21.0.13 (Temurin) / build-tools 37.0.0 / platforms `android-37.2`（另有 `android-34`、`android-37`）/ NDK 30.0.16248370 (r30) / CMake 4.4.5 / Ninja 1.13.2 / platform-tools 37.0.0p1，仅面向 ARM64 沙箱内的 Android/Flutter APK 构建。
  - **插件版本不锁定**：AGP 与 Kotlin 由各工程自行声明（非套件固定值），当前工作区工程为 AGP 9.4.1 / Kotlin 2.4.21；Gradle 9.9.0 内置 Kotlin 也是 2.4.21，与工程声明一致。
  - **路径**：`/opt/gradle-9.9.0`、`/opt/android-sdk/{build-tools,platforms}`、`/opt/taixu/tools/ndk`、`/opt/taixu/tools/cmake-4.4.5`；`GRADLE_HOME` 由 `/etc/profile.d/zz-taixu-pin.sh` 动态取 `/opt/gradle-*` 最高版，脚本勿写死版本号。

---

## 2. 🗺️ 模块拓扑与职责 (Module Topology)

```text
LinuxAIRuntime/
├── app/                  # 应用壳工程：MainActivity、Koin 初始化、JNI C 代码、前台保活 Service
├── core/
│   ├── model/           # 纯 Kotlin 数据模型 (不含 Android SDK 依赖)
│   ├── common/          # 协程调度器、日志、通用工具类
│   ├── database/        # Room 数据库：Harness 对话记录、会话、工具执行历史
│   ├── datastore/       # Jetpack DataStore：用户偏好、存储挂载、激活模型、Agent 命令超时
│   ├── document/        # 有界文档正文提取、旁路文本与 Android 15+ 隔离 PDF 解析
│   ├── network/         # OkHttp 网络客户端、SSE 流式解析器、超时策略
│   └── security/        # API Key 本地安全加解密
├── runtime/              # Linux 沙箱与 PRoot 核心：命令/进程注册、PTY、工作区导入导出与构建
├── project-template/     # 标准化项目模板：manifest、变量表单协议、导入导出、物化与内置模板资产
├── harness/              # Agent 智能体核心：Agent 循环、流式推理、内置工具/审批、MCP
│   └── core/            # 纯 Kotlin 轮次核心与模型能力契约；不依赖 Android/Room/网络/PRoot
├── tools/                # 工具生态中心：Registry、本地插件、安装事务、批量组件安装、Provider 安全
├── plugin/
│   ├── packages/        # 纯 Kotlin v1/v2 包协议、SemVer、安装依赖与资源声明检查
│   ├── api/             # 纯 Kotlin 插件描述、扩展引用与不可变目录校验
│   ├── runtime/         # 纯 Kotlin 插件状态机、代次、使用租约与资源排空
│   └── ui/              # 原生页面贡献、Compose 宿主与受限静态 Web 页面适配
└── feature/              # Compose UI 业务特性层
    ├── components/      # 太墟 Miuix 设计系统、通用组件 (RuntimeCard, TopBar, Icons)
    ├── theme/           # Miuix 调色板、原生字阶、页面间距与主题配置
    ├── home/            # 系统概览：Linux 状态、内存/磁盘/进程实时监控，从侧边导航进入
    ├── chat/            # 默认入口：智枢对话、共享会话抽屉、任务拆解卡片、宽屏双栏布局
    ├── terminal/        # 终端 Compose UI 与触觉按键条；会话/VT100 状态机位于 runtime
    ├── workspace/       # 工作区管理器：创建/ZIP/GitHub 导入、导出、代码浏览、后台构建
    ├── workflow/        # 工作流目录、DAG 画布、执行时间线与人工审批界面
    ├── settings/        # 设置中心：模型档案、Agent 超时、工具/本地插件、存储挂载、外观与诊断
    ├── developer/       # 开发者原生沙箱与诊断面板
    ├── onboarding/      # 首次启动引导与 RootFS 解压就绪流程
    ├── custom_iteration/ # 自定义迭代（AI 改应用）入口
    └── navigation/      # 顶层导航路由与 NavHost 调度
```

---

## 3. 🔗 跨模块交互原则 (Cross-Module Rules)

1. **单向依赖流**：
   - 依赖方向严格保持 `feature/*` → `core/*` / `runtime` / `harness` / `tools`。
   - 业务 feature 之间**严禁**互相横向依赖，仅可依赖共享 UI `feature/components` 与 `feature/theme`，顶层由 `feature/navigation` 负责页面装配与路由调度。
2. **持久化隔离**：
   - feature / runtime / harness 只能依赖 `core/database/PersistenceRepositories.kt` 暴露的 Repository 接口，严禁直接注入或操作 Room DAO。
   - 偏好配置统一通过 `core/datastore/PreferenceFacades.kt` 提供的领域分面接口读取或写入。
3. **纯模型层隔离**：
   - `:core:model` 必须保持 Pure Kotlin，**严禁**引入 `android.*`、`androidx.*` 或 Compose 依赖。
   - `architectureCheck` 任务已接入 `app:preBuild` 验证阶段，严密阻断模型层平台化与违规依赖。
   - `:harness:core` 的 `SessionProjectionBuilder` 处理存储无关的上下文贡献。Android 层 `SessionContextProjector` 负责捕获叶子、读取分支窗口和解码；`CompactionManager.inspect()` 提供来源与诊断，`project()` 从同一结果生成模型上下文。压缩后的查询继续避免加载已折叠消息 Blob，检查接口不创建或移动 Lane。
   - 多入口依赖 `SessionControl` / `InteractiveSessionControl`，均由同一 `HarnessLoop` singleton 实现。`SessionInputController` 在会话锁内校验、记录任务并提交输入；Web REST 等待接收回执，执行结果沿状态流报告。启动恢复保留具体实现入口，子任务保留独立 Lane 调度。
4. **命令执行边界**：
   - 短时、有明确退出状态的前台命令调用 `LinuxRuntime.execute()` / Harness `base`。
   - 跨调用持续运行的服务或长任务调用 `LinuxRuntime.startBackground()` / Harness `process`，统一由 `ProcessRegistry` 托管 PID、生命周期与环形日志缓冲。
5. **工作区操作边界**：
   - 项目生命周期与物理目录交互统一通过 `WorkspaceManager` 与 `WorkspaceFileService`；`runtime/ProjectType.kt` 定义项目类型，包括 `TAIXU_PLUGIN` 本地进程插件开发。
   - ZIP 导入必须执行路径穿越防范（Zip Slip Check）、文件大小上限与条目数校验；Git clone 必须在沙箱内隔离执行。
6. **构建调度与保活边界**：
   - 工作区构建由单例 `WorkspaceBuildTaskCoordinator` 全局持有，UI 页面旋转或重建不中断构建任务；底层构建执行与环境预检由 `WorkspaceBuildRunner` 承载。

## 4. 依赖装配（Koin）

- 插件平台：`plugin:api` 与 `plugin:runtime` 均保持纯 Kotlin，`plugin:ui` 适配 Compose。`navigationModule` 装配 `NativePluginPlatform`、单例 `PluginPages` 与 `PluginController`；终端/浏览器的独立页与聊天面板共享带代次准入的页面宿主。`PluginPreferences` 只保存启用选择；状态机按依赖启停并保留未确认释放资源。菜单/设置贡献通过共享 slot 注入，管理页由通用宿主装配。页面插件不拥有全部终端/浏览器引擎，也没有外部原生代码加载或授权隔离，详见 [`PLUGIN_PLATFORM.md`](PLUGIN_PLATFORM.md)。
- P5 由 `WebPagePlugin` 将静态页面接入 PAGE 目录；独立 WebView 使用随机来源和结构化消息，`PluginCapabilitySession` 按文档绑定身份、代次、动作、目标及本地 Android 环境，提供原生单次/限时确认和撤销。`WebPluginRepository` 复用 v2 包目录提供外部静态 WEB 导入、完整性快照和移除；settings 装配宿主白名单能力，WEB 不进入 Linux 工具目录和进程安装收据，不改变旧 HostBridge 权限。详见 [`PLUGIN_WEB_EXTENSIONS.md`](PLUGIN_WEB_EXTENSIONS.md)。
- Provider 边界由 `harness/di/harness/ProviderModule.kt` 装配：`ProviderModelResolver` 管理档案、凭证与偏好，`ProviderTransport` 管理请求策略，`LlmApiRegistry` 按模型的 API 选择协议实现。兼容门面 `ProviderClient` 保留已有调用入口；纯 `ModelDescriptor` 不含密钥或端点。
- P4 的 `HarnessPluginRegistry` 装配受信任的编译时工具、背景资料、检查点、Provider 工厂和工作流节点。工具通过 `use_capability` 的 `plugin.<id>` 域沿用审批/PLAN；背景资料脱敏后以有来源标注的合成 user 消息计入预算。三种协议与等待节点已迁入插件工厂；`PLUGIN_ACTION` 是新节点的通用路由，执行前通过工作流 broker 进行带令牌的确认。详见 [`PLUGIN_HARNESS_EXTENSIONS.md`](PLUGIN_HARNESS_EXTENSIONS.md)。
- 工具边界由 `harness/di/harness/ToolBackendModule.kt` 装配：纯 JVM `ToolBackend` / `ToolCheckpoints` 定义可等待契约；`WorkspaceToolBackend` 通过 `WorkspaceToolOperations` 使用受限工作区访问与写入快照。`ToolExecutor` 保留审批、PLAN、宿主权限和其他工具分派；检查点只允许否决或追加经脱敏的说明，不能授予权限或替换结果状态。默认检查点为空，应用代码可注入受信任实现。
- 各模块的 `src/main/java/top/wkbin/taixu/di/**/KoinModule.kt` 声明本模块构造器与接口绑定；`app/di/TaiXuModules.kt` 汇总完整依赖图，Application 启动时注册应用 Context，并禁止定义覆盖。
- 业务类使用普通构造器；仅 Android 创建的 Application、Activity、Service、Receiver 和 Compose ViewModel 入口访问 Koin。新增构造依赖时同步更新所属模块注册，并运行 `:app:testDebugUnitTest` 的依赖图测试。
- 原进程单例使用 `single`，无作用域对象使用 `factory`，ViewModel 使用 `viewModel`，保留 Navigation3 的 ViewModelStoreOwner 归属。
- Kotlin `Lazy` 保留 Runtime、仓储与 MCP Server 的延迟创建；子代理通过 `() -> ToolExecutor` 打断依赖环。ToolExecutor 的偏好通过构造器显式装配。
- 工作流执行器与工具适配器集合使用不同 qualifier，避免泛型擦除后的 Set 定义冲突。
- WorkManager 保留 `Configuration.Provider` 按需初始化，使用 `KoinWorkerFactory` 创建 Worker；WorkerParameters 来自系统，Context 来自应用容器。
- DI 使用经典 DSL，不引入 Koin 编译插件；KSP 仅用于 Room。依赖完整性由图测试保障，不能把 Kotlin 编译通过等同于 DI 图验证通过。

P5 受管理静态来源由 navigation 装配 runtime 的 loopback 资源服务与 plugin/ui 的页面租约；navigation 显式依赖 runtime，已登记架构依赖白名单。服务不在注册或启用阶段启动，外部包不能声明此来源。


## 5. 聊天引用、工具展示与协议解码

- 工作区文件补全由 `feature/chat/WorkspaceMentionSuggestions` 经 `SessionExecutionEnvironments.activity` 读取会话已绑定的 `ExecutionFiles`，不读取当前手机目录作为远端兜底。仅输入末尾的 `@` token 触发；选择文件插入 guest 绝对路径，含空格路径使用引号，选择目录可继续补全，不自动附加正文。已有 Skills/MCP 挂载语义保留。
- `WorkspaceFileCompletion` 采用有界广度扫描：最多 512 条索引、20 条建议、8 层；本地最多 64 个目录/2 秒，远端最多 24 个目录/4 秒；缓存按执行环境身份隔离，本地 5 秒、远端 20 秒。排除常见生成目录，并处理分层 `.gitignore` 的常用 `*`、`**`、`?`、根路径及否定规则；不宣称完整 Git ignore 语法兼容。大项目只提供有界候选，不保证穷举。浏览失败不回退其他环境。
- 本地目录浏览实现 `WorkspaceFileAccess.listEntries`，省略符号链接和无法读取的条目；严格产物枚举继续独立使用 `listArtifactEntries`，不把不完整浏览当成完整产物观察。
- 工具过程行与详情共用 `LocalToolUiRegistry` / `ToolUiRenderer`。受信任的原生代码可按精确扩展工具名注册图标、标题、目标、Diff 统计和预览；未知工具使用通用详情，文件工具保留现有 Diff。展示注册不参与工具执行、审批或重试决策，也不加载外部原生代码。
- Chat Completions、Responses、Anthropic 的每条响应分别持有独立 `ProviderStreamDecoder`。HTTP 适配层继续负责请求、取消、首事件超时与非 SSE 兜底，解码器负责增量、工具参数、usage 和完成校验；`finish()` 幂等，缺少协议完成事件时拒绝返回可执行工具结果。Anthropic `pause_turn` 的多轮续跑及共享预算继续由适配层管理。


## 6. 文档附件正文提取

`:core:document` 独立承载 DOCX 主正文、PPTX 演示关系顺序和 EPUB spine 顺序解析，以及 Android 15+ 系统 PDF 正文提取；不引入第三方 PDF 引擎、不调用模型或 OCR。旧版 Android 对 PDF 返回明确状态并保留原文件。

输入上限 32 MiB，ZIP 最多 2048 条目、单条解压 8 MiB、累计读取解压数据 64 MiB、256 个幻灯片/章节，XML 深度最多 64；正文最多 256 Ki UTF-16 字符，超长正文附截断标记。PDF 最多读取 200 页。ZIP 读取按实际流量计费，禁止重复条目和越界路径，不展开到磁盘、不解析外部 DTD/实体、不访问网络。XML/ZIP 解析在可中断 IO 线程中按步骤检查 10 秒期限；这些检查不构成恶意代码隔离。

PDF 通过 `bindIsolatedService` 为每次请求创建独立的 `:document_parser` 实例，只传入原文件只读 FD、临时输出写 FD 和状态回信通道；正文不经过 Binder 大消息。宿主串行准入，并限制等待 10 秒；取消或超时解除本次绑定，专用服务销毁时终止自身进程。Application 对这些实例跳过 Koin、Room、凭据及 Harness 初始化。系统调度或服务销毁延迟不提供硬实时终止保证。

聊天仍通过原文件挂载路径和 `.txt` 旁路路径交付，提取全文不自动加入模型输入。旁路在同目录临时写入后原子发布；失败、无正文、容量限制和旧系统不支持通过附件提示及固定状态说明展示，原文件保留。移除待发送附件会清理宿主副本及其已登记的旁路文本。正文内容与提示均不授予工具权限。


### 排队消息编辑

排队消息通过 `QueuedPromptActions` 复用会话调度锁，由 `PromptQueueManager` 管理持久化编辑占用；`HarnessQueueEditQueries` 提供 Room 比较更新与任务描述同步事务。Compose 的 `QueuedPromptEditor` 保管独立草稿，取消/切换/退出释放占用，保存保留队列顺序、任务身份和附件。数据库版本 87，迁移为 `MIGRATION_86_87`；重启释放占用后恢复原已保存内容。详见 [执行时序](EXECUTION_TRACES.md#排队消息原位编辑)。


### Markdown 解析与渲染

`:core:markdown` 是只依赖纯 Kotlin `core:model` 的 JVM 模块，提供块模型、解析器、完整正文校验的有界 LRU，以及基于 `ChunkedText` 的增量解析。语法解析不读取文件、网络、Android Context 或 Compose；块语法与原渲染器一致，流式输出只重解析尚未完成的尾部。

`feature:components` 的 `MarkdownText` / `StreamingMarkdownText` 保留已有入口；`LocalMarkdownComponents` 提供受信任原生代码的代码块、表格和媒体渲染插槽。默认实现分别放在 `MarkdownContentBlocks`、`MarkdownInlineText`、`MarkdownMediaBlock`、`MarkdownImagePreview`、`MarkdownMediaAccess`；沙箱路径映射、图片加载/预览/保存及代码复制由宿主 UI 负责。聊天、群聊、设置和应用提示继续共用该入口，替换插槽仅改变展示，不改变模型消息和工具权限。

正文块缓存按条目数及源正文总字符数限量：普通缓存最多 300 项/512000 字符，大文本缓存最多 4 项/1048576 字符，超预算正文直接解析；大文本同 ID 命中时核对完整正文。行内 AnnotatedString 缓存按键与结果的总字符数限制在 512000，超过 128000 字符的单段不缓存；流式渲染不写入全局缓存。这些字符额度用于限制缓存保留量，不是解析输入上限或精确堆内存配额。


### 本地会话消息搜索

侧栏 `SessionMessageHits` 通过 `SessionMessageSearch` 串行维护当前关键词的加载/命中/空结果/失败状态，默认防抖 250ms；每次关键词变更立即清空旧结果并取消旧任务，发布前核对请求代次。关闭侧栏释放查询，失败可按当前关键词重试；搜索和重试仅查询本地消息，不调用模型。命中摘要按字面关键词高亮，点击仍打开对应会话。

`RoomHarnessRuntimeRepository.searchMessages` 向共享 SQL 常量 `MESSAGE_SEARCH_SQL` 分别传递原始关键词和转义后的 LIKE 模式。摘要按原始关键词定位并兼容 ASCII 大小写，命中前保留 16 字符，摘要最多 220 字符；结果按时间和消息 ID 倒序排列。JSON 有效性与 text 类型在 CASE 内检查，坏 JSON、外置正文占位、空值及非文本字段安全跳过；用户/助手之外的消息不检索。外置正文继续不参与这一 SQL 搜索，也不据此自动读文件、切换历史分支或授予工具权限；数据库 schema 无变化。


### 按会话管理输入草稿

`ChatSessionStartup` 在主聊天页和悬浮聊天之间共用初始化锁。启动优先沿用已选会话，其次恢复文字草稿所属会话，再按更新时间选择最近会话，空库创建默认会话。手动新建、切换和回退取得选择权并取消初始化。`CurrentSessionTracker` 分开记录选择请求和已发布选择，新会话完成落库与环境绑定后按请求顺序发布前台 ID。

`ChatMessageActions` 为重新生成、工具重试、编辑重发、删除和停止携带界面渲染时的会话 ID。切换后的过期回调会被忽略，消息修改核对目标消息及运行状态，编辑弹窗随会话切换关闭。Harness 在执行编辑重发时再次核对用户消息，目标已失效则结束本次操作。

`SessionComposerDrafts` 在聊天 ViewModel 生命周期内按 session ID 保留完整文字、待发附件、系统分享快捷动作和导入状态。切换会话加载该会话草稿，UI 输入/移除/发送回调携带显示时的 session ID；发送委托 Harness 时也固定该 ID。`/clear` 继续清空发起会话的输入文字并新建会话，普通新建/切换保留原草稿。

`ComposerAttachmentActions` 在附件处理开始时取得归属租约。多个导入分别计数；会话切换后仍交回原草稿，会话删除后的迟到结果释放本次新文件。取消在 IO 完成、主线程接收之前发生时也清理已准备的副本；已交给消息的附件从草稿移出但不删除 backing 文件。删除回执及既有会话列表的移除观察共同清理旧草稿，不再额外订阅一条数据库查询。系统分享行同样绑定原会话，过期快捷动作不提交到其他会话。

`SavedComposerDraftTextState` 用 SavedStateHandle 保存最多 64 个近期文字草稿，文字与会话键合计最多 32768 个 UTF-16 字符。完整内容留在 ViewModel 内存，超出快照容量时明确提示，不截断当前输入。旧单份 `chat_input_draft` 迁移到初始会话；附件、URI 和 base64 不写入状态包，页面/进程重建只恢复容量内文字，不承诺附件恢复或强制停止后的持久恢复。本轮不新增数据库表或模型调用，也不改变既有发送受理协议。
