# pi 1.1.0 设计借鉴与实施记录

参考版本固定为 [earendil-works/pi v1.1.0](https://github.com/earendil-works/pi/tree/v1.1.0)，不以本地 0.84.3 或持续变化的 main 作为实现依据。

## 实施顺序

| 阶段 | 范围 | 状态 |
| --- | --- | --- |
| 1 | 纯 Kotlin 轮次核心、请求/收尾契约、工具持久化屏障 | 已实现 |
| 2 | Provider、通信协议、模型能力分离；统一协议适配器入口 | 已实现 |
| 3 | 工具后端接口与扩展检查点；明确修改权限、失败及取消语义 | 已实现 |
| 4 | 会话树与压缩投影的可检查性、跨实现契约验证 | 已实现 |
| 5 | 多入口共享会话控制接口；按集成需求扩展 RPC | 已实现 |

每阶段先复用现有能力，再以有独立测试的边界渐进迁移。审批、宿主访问、PRoot 执行与资源限额由太墟现有策略负责。

## 阶段 1：轮次核心与持久化屏障

参考 [Agent API](https://github.com/earendil-works/pi/blob/v1.1.0/packages/agent/README.md) 和 [agent-loop.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/agent/src/agent-loop.ts) 的请求准备、消息发布屏障和轮次收尾。

新增 `:harness:core`，使用 JVM library 插件，仅依赖 Kotlin Coroutines。架构策略禁止它引用 Android、UI、网络客户端、DI、数据库或运行时实现。它是轮次执行核心的首个提取边界；消息持久化、整个会话循环和协议解析仍在 Android `:harness` 中。

| 核心类型 | 职责 |
| --- | --- |
| `TurnResponse<Call>` | 协议无关的有效性判定和工具调用集合 |
| `ProviderTurnResult<Response>` | 模型请求成功或失败，不包含 HTTP 实现 |
| `AgentTurnRunner` | 发布助手消息、执行工具、收尾及追问调度 |
| `TurnLifecycle` | 可等待的 `prepareRequest` / `finishTurn` 控制契约 |
| `DurableToolRunner` | 意图提交 → 执行 → 结果提交的统一屏障 |

`TurnRunner` 保留为 Harness 协议适配器：将 `ChatResult` 和文本工具调用归一化后交给核心。空响应、残渣推理、无法解析的文本工具调用等判定仍由 `ProviderResponseNormalizer` 负责。

### 顺序与失败语义

1. 上层选择并提交用户输入或 steering。
2. 等待 `prepareRequest`，再请求模型。
3. 等待助手消息持久化完成，再进行工具限额和执行。
4. 每个工具等待执行意图提交完成，再开始产生副作用。
5. 等待所有工具结果提交完成，再调用 `finishTurn`。
6. 收尾完成后才返回结果或消费 follow-up。

`finishTurn` 返回 `END` 时保留未消费追问；返回 `CONTINUE` 时请求一次续跑，已有工具续跑或追问可以满足它。已有轮次预算仍然有效。模型失败或无效响应会调用收尾契约，但收尾决策不能将失败改成成功或续跑。

持久化失败属于基础设施失败，直接向上抛出，不伪造成普通工具失败。只有工具执行阶段的异常可转换为面向模型的失败结果。取消始终传播；提交回调即使使用 `NonCancellable` 完成事务，核心也会在事务后检查取消，避免继续执行工具或报告成功。中断后的悬空调用由已有恢复机制负责。

主工具回合和 `SubagentToolRoundRunner` 共用 `DurableToolRunner`。子任务的写租约、递归派发禁令、审批移交及失败写入证据保持在 Lane 适配层；证据在结果提交成功后更新。

现有 `HarnessEventBus` 继续提供可丢弃的观察事件，不承担持久化、审批或续跑控制。新生命周期契约是可复用的代码 API，本阶段不开放任意第三方插件修改执行状态。

### 验证

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :harness:core:test :harness:testDebugUnitTest --console=plain
.\gradlew.bat architectureBaselineSync architectureCheck --console=plain
```

核心测试验证异步提交屏障、提交失败、取消传播、一次续跑、追问保留及收尾决策。Harness 测试覆盖协议适配和现有恢复/审批契约；子工具回合用真实 Room 事务验证写租约、审批移交及提交失败。无需真实模型或设备。

本阶段核心与 Harness 合计 894 项单元测试通过（含新增 19 项核心测试、6 项 Room 集成测试），架构门禁 32 个模块全部通过，`:app:assembleDebug` 构建成功。尚未做真机验证。

## 阶段 2：模型解析、协议实现与模型能力

参考 [pi AI README](https://github.com/earendil-works/pi/blob/v1.1.0/packages/ai/README.md) 中 Provider runtime 与 API implementation 的区分，以及 [types.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/ai/src/types.ts) 的模型元数据设计。借鉴其边界，不引入 TypeScript SDK 或照搬桌面认证流程。

| 边界 | 太墟实现 |
| --- | --- |
| 档案、凭证与用户偏好 | `ProviderModelResolver` 选择档案/具体模型，读取安全仓储中的 Key 池，应用显式容量与全局推理偏好；不发送请求 |
| 模型元数据 | 纯 JVM `ModelDescriptor`、`ModelCapabilities`、`LlmApi`；不含密钥、HTTP 客户端、Room 或 Android 类型 |
| 通信协议 | `LlmApiAdapter` 统一非流式/流式签名；Chat Completions、Responses、Anthropic Messages 各自实现序列化、鉴权及 SSE 解析 |
| 请求策略 | `ProviderTransport` 共用历史修复、Key 调度、超时、诊断、空响应检查、推理计时和取消检查 |
| 兼容入口 | `ProviderClient` 转发既有模型解析与请求方法；公共解析工具仍保留，避免破坏现有调用者 |

`LlmApiRegistry` 是不可变的内置协议集合，重复或缺失注册直接失败。路由依据 `ModelConfig.api`，同一网关可复用三种协议。现有持久化字段和 `ModelConfig` 构造参数不变：Responses 开关继续优先于旧 `ApiProtocol`；未启用时仍按原档案 URL/厂商推断兼容协议。

能力描述表示当前档案的**有效配置**，并非远端能力探测或可信模型目录。显式图片开关、工具模式和容量仍由用户控制；纯净模式禁用两种工具形式；缓存仅对 Messages 有效。这些投影已用于主/子会话的工具上下文、图片投影、三种请求的工具 schema 和 Anthropic 缓存字段。

推理能力按实际序列化器确定：Responses 的现有适配器省略 DISABLED 字段，因此全局关闭不再把它标记为已关闭；Messages 用 thinking 开关与 budget_tokens 支持全局深度映射。Chat Completions 保持既有厂商字段兼容策略。模型档案中显式关闭推理仍优先。

统一请求边界还修复了取消时关闭阻塞 socket 后 IOException 可能掩盖协程取消的问题：成功返回和异常返回都检查当前 Job；取消不会进入另一个 Key 的重试。

### 验证

新增测试使用 MockWebServer 对三种真实协议逐一验证路由、鉴权、自定义头、正文/推理/工具进度回调、诊断、429 Key 切换、非 429 不重试、空响应、历史修复和 socket 取消。Room/DataStore 测试验证模型 variant、显式容量、严格选择失败及全局推理设置。注册表测试验证缺失/重复注册和流式/非流式超时隔离。

新增 43 项测试通过；Harness 与纯 Kotlin 核心合计 937 项测试无失败、无跳过。全项目 `test`（含 Koin 依赖图回归）、32 模块 `architectureCheck` 与 `:app:assembleDebug` 均成功。全量回归还暴露了既有优先级排队测试对固定延时的依赖，已改为明确的入队/释放屏障，生产调度实现不变。

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test :harness:core:test :app:assembleDebug architectureCheck --console=plain
```

尚未进行真机或真实模型服务测试。

## 阶段 3：工具操作后端与可等待检查点

参考 v1.1.0 的 [read.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/coding-agent/src/core/tools/read.ts)、[write.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/coding-agent/src/core/tools/write.ts)、[edit.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/coding-agent/src/core/tools/edit.ts) 中可注入的 operations，以及 [extensions/types.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/coding-agent/src/core/extensions/types.ts) 的工具调用阻断与结果处理边界。

| 边界 | 太墟实现 |
| --- | --- |
| 可复用契约 | 纯 JVM `ToolBackend<Request, Result>`、`ToolCheckpoint` / `ToolCheckpoints`，不引用 Android、文件系统或数据库 |
| 文件工具语义 | `WorkspaceToolBackend` 处理 read/write/edit 的参数、分页、图片、差异元数据和写入证据 |
| 可替换文件操作 | `WorkspaceToolOperations`；本地 `WorkspaceFileAccess` 继续负责规范路径、大小限制与原子写入 |
| 写入快照 | `WorkspaceMutationSnapshots` 复用轮前/轮后捕获逻辑，下载工具也使用同一前置快照逻辑 |
| 扩展边界 | `ToolExecutionBoundary` 提供独立只读参数/结果快照，等待否决与追加说明；`ToolExecutor` 保留既有策略和分派 |
| 装配 | `ToolBackendModule` 注册本地文件后端、快照与默认空检查点集合 |

首批迁移文件工具，以内存操作实现与真实本地文件实现验证替换能力。命令、进程、宿主动作和 MCP 分派继续使用已有执行器；本阶段没有新增 SSH 后端或外部脚本插件加载器。

### 控制与失败语义

顺序为：等待调用意图提交 → 前置检查点 → 既有审批/PLAN/权限门控 → 工具执行 → 后置检查点 → 说明脱敏与输出限制 → 等待工具结果提交。主会话、子任务 Lane 和批准后重放均经过同一工具边界；写租约、并发互斥与审批移交仍由已有调用层负责。

检查点仅供受信任应用代码注入，允许前置否决和后置文本说明。它不能替换参数、工具结果身份、成功标记、审批请求或审批移交标记，也不能授予跳过审批的权限。参数中的嵌套 JSON 容器和结果中的集合均复制并只读封装；后置回调观察原工具结果，前一个回调的说明不会作为后一个回调的输入。检查点实例可能被并发调用，实现者须自行管理内部状态；这不是不可信代码的安全沙箱。

前置异常阻止调用；后置异常追加通用说明并继续后续检查点，不把已经发生的成功写入变成可重试的失败。异常消息不直接进入输出，避免携带原始凭证。否决理由和说明经现有脱敏及截断流程处理；说明格式化失败时丢弃未脱敏说明，保留原结果的身份与控制状态。注册顺序固定，重复/非法 ID 拒绝注册，最多 32 个检查点，每项说明最多 4096 字符。

取消直接传播，并在回调、文件操作和格式化等等待屏障之后检查当前 Job；即使某个实现使用 `NonCancellable`，取消也不会继续下一步或返回成功。文件写入沿用轮前快照去重及实际后像捕获，失败编辑不生成后像；超限文件仍报告恢复证据不完整，不能把它误记成原本不存在的文件。事件总线保持观察用途，不替代这些控制屏障。

### 验证

新增测试覆盖可替换后端、分页/图片、真实路径穿越防护、写入快照、回调顺序、异常与取消、审批/PLAN/Lane 约束、批准后重放、只读嵌套参数和结果、说明脱敏，以及真实 Room 中意图提交与结果提交的等待顺序。

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test :harness:core:test :app:assembleDebug architectureCheck --console=plain
```

新增 37 项测试通过；Harness 与纯 Kotlin 核心合计 974 项测试，无失败、无跳过。全项目 `test`、5 项 Koin 依赖图测试、32 模块架构门禁及 `:app:assembleDebug` 均成功；app 测试保留 1 项既有跳过。Koin 检查对后端构造器中由装配代码提供的函数和空检查点集合显式登记了注入参数，没有添加全局类型豁免。

全量回归发现既有 MCP 假通道的延迟写协程在通道关闭后仍尝试发送，导致未捕获异常污染后续测试；已在假通道关闭时先取消并等待写协程，生产 MCP 实现未改动。尚未进行真机或真实模型服务测试。下一阶段是会话树与压缩投影的可检查性及跨实现契约验证。

## 阶段 4：可检查会话投影与跨仓储契约

参考 v1.1.0 [session-manager.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/coding-agent/src/core/session-manager.ts) 的 `ProjectedSessionEntry` / `buildSessionProjection`：模型上下文保留来源条目，原始会话树与上下文投影分离。太墟继续使用现有 Room 不可变树与保留消息快照，不改成 pi 的 JSONL 存储格式，也不在本阶段引入上下文编辑插件。

| 边界 | 太墟实现 |
| --- | --- |
| 存储无关的规则 | `:harness:core` 中的 `SessionProjectionBuilder`：保留快照 → 水位线补回 → 新消息，另行处理分支摘要、冻结召回和状态条目 |
| 分支选择与解码 | `SessionContextProjector` 捕获 Lane 叶子，并在所有仓储读取中固定该叶子；解码最新压缩快照和对应窗口 |
| 检查结果 | `SessionContextProjection` 包含会话/Lane/叶子、消息与摘要来源、召回来源、条目贡献分类、诊断代码及来源水位线 |
| 兼容入口 | `CompactionManager.inspect()` 返回检查结果；`project()` 从同一结果生成既有 `CompactedContext`，调用者签名保持兼容 |
| 压缩提交 | 持久化后再次通过同一投影器读取，立即返回并发写入补回内容及真实水位线 |

`inspect()` 是应用代码的只读检查 API，不创建或移动 Lane、不生成摘要、不发起模型请求。它解释的是**会话层上下文**，不是最终 HTTP 请求：提示词注入、工具协议映射、图片压缩、字节限制仍由已有请求组装层处理。最终请求正文检查仍使用现有请求诊断。主会话请求、上下文用量面板、模型切换与压缩重读经 `project()` 共用规则。

### 来源与读取范围

消息来源分为原分支条目、压缩保留快照、水位线补回条目。保留消息的 `sourceEntryId` 是承载它的压缩条目 ID，消息本身仍保留逻辑消息 ID；它并不表示已重新加载对应原始历史条目。分支摘要与用户召回分别保留源条目 ID 和 sequence；覆盖后的旧召回标为 `SUPERSEDED_RECALL`，状态条目不会进入模型消息。

有有效压缩快照时继续使用 `branchWindow()`：不把已经折叠的消息或大 Blob 重新读入堆。检查结果的 entries 只代表本次实际读取窗口，而非完整会话树。无快照或快照损坏时才读取完整活动分支。一次检查固定叶子，读取期间 Lane 切换不会混合兄弟分支的快照和消息；之后的新检查反映新叶子。

### 恢复与失败语义

损坏压缩负载、损坏旧格式保留消息、缺失非空保留区或非法水位线会回退完整活动分支，并报告对应诊断代码，避免只留下摘要而静默丢失保留历史。不能解码的消息或分支摘要报告条目 ID 与代码，不把原始负载或异常消息写入诊断。普通状态条目继续保持上下文不可见。

原实现压缩提交后直接返回提交前的保留列表；提交窗口中的并发直写虽能在下一次 `project()` 补回，却没有进入此次返回值。现在提交后返回规范投影，立即携带补回消息、分支摘要及来源水位线，与后续请求保持一致。旧格式 JSON 保留区继续兼容，已有工具调用/结果配对及冻结召回保持原形。

模型上下文读取的仓储异常继续传播，不能变成成功的空历史。取消在仓储读取与压缩提交屏障后检查并传播，`NonCancellable` 的返回也不能掩盖取消；轻量 UI 快照仍可在普通读取失败时返回 null，但不吞取消。

### 验证

纯 Kotlin 测试验证消息顺序、保留/补回来源、历史摘要去重、召回覆盖、损坏诊断及完整分支/窗口的内容一致性。12 套契约场景分别运行于内存仓储（接口默认完整分支查询）和真实 Room（递归 SQL 窗口查询），覆盖 Lane 隔离与读取时切换、重复压缩、旧格式、损坏回退、并发消息/摘要补回及 NATIVE / JSON_TEXT 工具消息投影。另有取消和读取失败测试。

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test :harness:core:test :app:assembleDebug architectureCheck --console=plain
```

新增 25 项测试通过（8 项纯核心规则、12 项跨仓储契约、5 项取消/读取失败）；核心与 Harness 合计 999 项测试，无失败、无跳过。全项目 `test`、5 项 Koin 依赖图测试、32 模块 `architectureCheck` 和 `:app:assembleDebug` 均成功；app 保留 1 项既有跳过。另核对 Debug APK 的 DEX，确认包含新的投影器与纯 Kotlin 投影规则。

尚未进行真机或真实模型服务验证。下一阶段是多入口共享会话控制接口，并按现有集成需求完善 RPC 边界。

## 阶段 5：共享会话控制与可等待输入接收

参考 v1.1.0 [agent-session.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/coding-agent/src/core/agent-session.ts) 的共享 AgentSession，以及 [rpc-types.ts](https://github.com/earendil-works/pi/blob/v1.1.0/packages/coding-agent/src/modes/rpc/rpc-types.ts) 中 prompt / steer / follow_up 的 disposition 回执。太墟沿用已有认证 REST 与 SSE 集成。

| 边界 | 太墟实现 |
| --- | --- |
| 会话控制 | `SessionControl` 提供显式会话 ID 的消息、状态、输入接收、停止与审批/提问恢复接口 |
| 交互便利接口 | `InteractiveSessionControl` 增加前台导航、编辑、重试、队列投影及文件回退 |
| 接收规则 | `SessionInputController` 共用校验、锁内存在性检查、审批过期结算、任务记录与队列接收 |
| 执行实现 | `HarnessLoop` 实现接口；Koin 两种接口均解析到同一 singleton |
| 多入口迁移 | 聊天 ViewModel、A2UI、悬浮窗依赖交互接口；通知停止/回复与 Web gateway 依赖共享接口 |
| Web 协议适配 | `WebChatRunProtocol` 解析输入模式与回执；Runtime 不依赖 Harness 类型 |

应用启动恢复继续由具体 HarnessLoop 管理；子任务继续使用独立 Lane runner 与写租约。审批调用保留现有 claimPending 独占、operation 归属及参数摘要检查，Web gateway 继续检查审批会话归属与 pending 状态。

### 接收语义

`submit(sessionId, text, imageUrls, queue)` 等待输入接收，不等待模型完成：

- `STARTED`：任务、原始用户消息和 operation 已提交，运行 Job 已启动；可能仍等待全局并发额度。
- `QUEUED`：输入队列已提交，返回队列类型与条目 ID；next_run 另带独立任务 ID，忙碌时的 steer / follow_up 归属当前任务。
- `Rejected`：空输入、空会话 ID、会话不存在或启动失败。

默认 send 保持 next_run FIFO；运行中或等待审批时入队。steer / follow_up 保持既有消费时机，空闲时创建新运行。UI 的 send / steer / followUp 为异步便利方法，调用同一 submit；接收基础设施失败记录日志并进入 UI error 投影。显式远端会话 ID 不切换前台焦点。

任务创建移到会话锁内的数据库存在性检查之后，避免已删除会话留下孤立任务。启动受理通过 `acceptTaskOperation` 在同一 Room 事务中提交完整用户消息、operation 和 task 的 RUNNING 状态及关联 ID；next_run 消费也在该事务内。启动 Job 不再重复认领或增加尝试计数，进程在提交后、启动前退出时由既有 RUNNING 恢复路径续跑。任务元数据及标题创建仍在该事务之前；受理前的存储失败向上传播。已受理后的队列投影刷新失败只记录日志，保留成功回执；取消继续传播，启动的本地提交与 Job 交接使用 NonCancellable，避免已提交输入因调用方取消而丢失调度。

纯图片输入的任务描述补充图片数量，原始消息的空文本与附件保持原形；operation 消息与队列项保存完整输入，任务描述本身不保存图片。

### Web REST 兼容扩展

发送路径及旧字段不变。请求可选 `inputMode` 为 `next_run`（默认）、`steer` 或 `follow_up`；未知模式和空消息拒绝，图片附件继续只接收 data:image/。

```json
{"userMessage":"先检查测试再修改","inputMode":"steer","taskId":"client-request-17"}
```

成功响应保留旧 `taskId` / `conversationMode` / `conversation`，新增 `input`：

```json
{"taskId":"client-request-17","input":{"disposition":"queued","queue":"steer","queueItemId":"..."}}
```

旧 taskId 是 Web 客户端关联 ID；`input.durableTaskId` 是 Harness 独立任务 ID，仅新运行和 next_run 提供。完成、失败和等待审批沿现有 SSE 报告；后续加固将有独立任务 ID 的输入改为按持久化任务状态报告结果（见下节）。停止仍为会话级异步请求，状态流表示清理结果。

验证/接收拒绝为 HTTP 400；存储等内部异常为 HTTP 500，输出通用消息。请求取消传播，不转换成业务错误。服务开关与鉴权流程保持原形。

### 验证

新增 30 项测试：13 项接收规则/并发/取消测试、5 项真实 Room 持久化测试、6 项 Web 协议测试、5 项 gateway 测试及 1 项 Koin Android 集成测试。覆盖持久化等待、队列顺序、删除时锁等待、附件快照、纯图片、失败和取消、Web 回执、审批隔离以及共享实例。

全项目 `test`、`:harness:core:test`、`:app:assembleDebug` 与 32 模块 `architectureCheck` 均成功。核心与 Harness 合计 1017 项测试无失败、无跳过；app 95 项、Runtime 144 项无失败，分别保留 1 项和 4 项既有平台条件跳过。6 项 Koin 装配/Android 集成测试均通过。超限文件基线下调，未新增尺寸豁免。

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat architectureBaselineSync test :harness:core:test :app:assembleDebug architectureCheck --console=plain -q
```

五个计划阶段均已实现。尚未进行真机、LAN Web 客户端或真实模型服务验证；这些集成验证仍需要相应运行环境。

## 后续加固：Web 任务结果与客户端事件隔离

沿实际调用链发现两处竞态：观察者启动前运行已经结束时，原实现因未见过 RUNNING 而永远不发送完成事件；同会话的后一次发送替换前一次观察者，且会话的 IDLE 可能被误认为排队输入已经完成。

- `AgentTaskRepository.observeTask()` 增加单任务观察契约；接口默认实现兼容已有内存仓储，Room 使用按 ID 的 SQL Flow，不重新加载整个任务列表。无需数据库版本迁移。
- `TaiXuWebChatAgentGateway.observeTask()` 只公开状态并核对会话归属，缺失、跨会话或未知状态不伪造成完成。`SessionControl.persistedMessages()` 通过只读 `SessionTreeStore.loadStrict()` 读取持久化消息，不切换前台、不覆盖流式内存消息；存储及消息解码异常（包括不可读取的外置载荷）传播至 Web 观察者，报告 error 并保留现有消息，不能把读取失败当成空历史和 completed。
- `WebChatRunTracker` 按客户端关联 ID 保留独立观察者；独立任务的 queued / running / waiting / terminal 来自持久化任务，完成前重读已提交消息。审批事件等待具体审批内容，排队输入不接收前一个任务的审批事件。
- 同一关联 ID 活跃或仍在接收时，重复发送在提交输入之前拒绝；未指定 ID 时采用 UUID，避免同毫秒请求冲突。接收失败释放关联，服务停止清理观察者与在途预留，迟到接收不能重新登记观察者；完成后的 ID 仍可复用。这不是跨重连、重试的持久化幂等协议。
- 任务完成报告 completed；失败、取消、暂停、缺失与未知状态沿旧客户端支持的 error 事件结束，不能把取消标为成功。已有停止 API 仍请求停止整个会话，观察者留到状态结算后再释放。
- 无独立任务 ID 的忙碌 steer / follow_up 继续使用会话观察语义，不承诺独立指令完成回执。SSE 仍为实时广播，无断线事件重放。

客户端 `taskEvents.ts` 根据会话和当前任务 ID 决定是否清理控件：旧任务或其他会话的完成不能清空当前任务，其他会话的审批不能覆盖当前审批。当前会话的前驱等待审批时可以显示审批内容，同时保留排队后继的任务控制。使用同步 task ID ref 避免连续 SSE 事件之间 React 状态提交滞后。`TaskReceiptTracker` 记住 HTTP 回执到达前的 waiting_approval / completed / error，回执不会重新激活已暂停或结束的任务；终态优先于迟到审批事件，记录仅保留至该请求结束。

新增 25 项测试：14 项观察/并发/取消测试、3 项 Room 任务状态测试、2 项仓储默认观察契约、1 项最终消息读取测试、5 项前端事件隔离测试。取消测试覆盖 Flow collect 子协程在 NonCancellable 读取后返回或抛 IOException 的两种情况；读取边界内检查取消，避免异常跨 combine 子作用域后误发失败事件。

前端 9 项测试、TypeScript 检查和 Vite 生产构建成功，构建产物同步到 APK 的 Web assets。全项目 `test`、`:harness:core:test`、Debug APK 构建及 32 模块架构检查通过：核心/Harness 1017 项、Runtime 158 项、app 99 项、数据库 56 项均无失败，保留 5 项既有平台条件跳过。超限文件基线继续下调。

实际验证使用已有本地 Node / TypeScript / Vite 执行入口；运行环境中的 pnpm shim 会额外触发依赖安装及 build-script policy 检查，已使用本地工具完成等价测试和构建，无项目依赖策略修改。

已核对 Debug APK 的 DEX 包含 WebChatRunTracker；包内 index.html 引用的 JS 存在，且其 SHA-256 与本次 Vite dist 文件完全一致。

尚未进行设备或 LAN 客户端端到端验证，也未提供 SSE 断线重放或跨请求持久化幂等保证。

## 状态归属审计（借鉴 pi 状态分层）

以 v1.1.0 extensions 文档的状态归属表（工具态跟随分支 → tool-result details；持久不进上下文 → appendEntry；发给模型 → sendMessage；跨会话 → 外部存储）与三条语义作为检查模板，逐个核验状态消费者：**分支敏感状态必须从当前分支重建；不得从全部历史条目重建（废弃分支是替代历史）；任务执行状态不因切换历史分支而被回滚**。本轮为只读审计，未修改实现。

| 状态消费者 | 存储位置 | 归属判定 | 从分支重建？ | 结论 |
| --- | --- | --- | --- | --- |
| 模型可见上下文 | Room 不可变会话树，`SessionContextProjector` 固定叶子经 `branch()` / `branchWindow()` 读取 | 分支（Lane 叶子） | ✅ 唯一从分支重建的消费者 | 符合语义 1、2 |
| RunMode（PLAN/BUILD） | `harness_sessions.runMode`（会话级，默认 build）+ `agent_approval_settings.runMode`（全局默认回落） | 会话 + 全局 | ❌ DB 直读 | 任务执行态，切分支不回滚 ✅ |
| 计划内容 | `agent_plans` 表（sessionId 键），plan 工具写入，`MemoryRecallSelector.planBlock(sessionId)` 注入提示词 | 会话 | ❌ DB 直读 | 同上；fork 不带入（缺口 2） |
| scratchpad | `agent_scratchpads` 表（sessionId, key），子代理 Lane 复用父 sessionId，跨 Lane 共享 | 会话 | ❌ DB 直读 | 同上；跨 Lane 共享见缺口 3 |
| 恢复运行态 | `OperationCoordinator` 持久化 OperationSnapshot per (sessionId, laneName)，含 phase 与 ReplayPolicy；`RecoveryManager` 按 Lane 遍历恢复 | (会话, Lane) | ❌ 快照直读 | 崩溃恢复不依赖 transcript ✅ |
| 文件回滚快照 | `CheckpointStore`：per-session 轮次 pre-image + 改动后凭据，MAX_KEPT=100 轮 + 64MB 预算，落盘 App 私有目录，懒恢复 | 会话（跨分支共享） | ❌ 磁盘直读 | 覆盖子代理写入（快照按 sessionId 是正确结果） |
| 审批授权缓存 | `SessionApprovalGrants`：纯内存 per-sessionId，无持久化，revoke 随会话删除；high/critical 不可记 | 会话 + 进程生命周期 | ❌ 刻意不重建 | 重启即清空 = 保守正确 ✅ |
| 工具面 / 工具激活 | 请求期从静态集合 + 模型能力投影派生；MCP 发现缓存为进程级 `ConcurrentHashMap<serverId, CachedTools>` | 请求期派生 / 全局进程 | ❌ | 见阶段 6 缺口 1（对统一工具目录的前置约束） |

pi 三条语义对照全部成立：模型上下文是唯一分支敏感状态（语义 1）；对话回滚走 `SessionForkConversationRewinder` fork 新 sessionId、原树不动（语义 2）；执行侧状态全部 DB/内存直读，切分支绝不回滚（语义 3）。缺口四项：① per-session 工具激活必须落 transcript（阶段 6 已遵循）；② fork 不复制 plans/scratchpads（设计确认项，保守正确）；③ scratchpad 跨 Lane 同 key 后写覆盖（低风险，需要隔离时加 laneName 前缀）；④ 授权缓存对用户不可见（设置页增强项）。

## 阶段 6：统一工具目录与注解升级

参考 v1.1.0 extensions 文档的工具暴露分级（direct / model-only / codemode / deferred / hidden）与 tool.annotations 语义。借鉴其边界而不引入 TypeScript 扩展机制：太墟的目录是编译期静态事实源，审批仍由 `ApprovalPolicyEngine` 独家决定，检查点继续只观察或否决。

| 边界 | 太墟实现 |
| --- | --- |
| 宿主能力目录 | `harness/directory/HostCapabilityDirectory`：direct/deferred 拆分、只读与 GUI-Assisted 动作分类、共享参数池、隐藏位的唯一事实源；provider 工具面的 host 声明由目录生成 |
| 代理分发 | `harness/directory/CapabilityToolRouter`：use_capability 的 list/inspect/call/decline 自 ToolExecutor 零增长迁出；server="host" 走与直接 host 调用完全相同的执行通道（输出上限、截图 metadata 附带、特权实时复核） |
| 注解升级 | `harness/approval/AnnotationEscalation`：escalation-only——显式 `destructiveHint=true` → 至少 high；`openWorldHint=true` 且非只读 → 至少 medium；永不降级，缺省 hint 不参与（存量服务零回归） |
| 注解数据面 | `McpToolAnnotations`（core:model）随 `McpToolDto` → `McpToolInfo` 透传；`annotationsForCall` 从发现缓存解析注解，未缓存一律 null（保守不升级） |
| REQUEST 模式 | 统一要求审批不变；注解升级只抬高 MCP 风险等级（声明破坏性 → high，从而不可被「本会话内记住」一揽子豁免） |

host 工具拆分：17 个高频动作保留 direct（status / exec / settings_get / package_list / app_list / logcat / device_status / 主屏 screen_* GUI / paste_text / app_launch），24 个低频动作（设置修改、应用管理变更、screen_capture、全部 virtual_screen_*）迁入按需发现域。巨型说明与参数表不再进入每轮工具声明，改为 inspect 按需输出——inspect 结果是普通工具结果，天然随分支回放，无需额外激活状态（遵循状态归属审计的结论）。

审批等价性（由测试锁定）：deferred 调用经 `flattenToHostArgs` 展平后由引擎委派回 HOST 分支——ASSISTED 的 GUI 自动放行、critical 判定与 PLAN 只读拦截均与直接调用一致；`SessionApprovalGrants` 的 `mcp:host:<tool>` 类别键继续生效。`hidden` 不只从提示词摘除：路由器在调用入口真正拒绝执行。**校验面 = 执行器接受面**：`ToolSchemaValidator` 对 host 走 `validationSchema()`（direct ∪ deferred 并集），provider 声明面只宣告 direct 用于 prompt 减负，旧式直接调用（重放/沙箱直调/历史模仿）不被 enum 硬拒——该不变量由 `HostCapabilityDirectoryTest` 锁定。

### 重新接线追记

阶段 6/7 的接线曾随并行重构提交（c7822283，基于旧版文件快照的 harness/core 重构）被覆盖回退：ProviderClient 恢复巨型 host schema、引擎/执行器失去目录引用，孤儿文件（与 HEAD API 兼容）留在工作区。已按用户决策在新架构上完成重放——ProviderClient host 目录引用、引擎注解升级与 host 委派、ToolExecutor 路由与 parentToolCallId 穿透、校验面 union override、提示词引导、AGENTS.md 规则全部恢复；全项目 test + `:harness:core:test` + `architectureCheck` + `:app:assembleDebug` 重新通过。

## 阶段 7：组合工具嵌套调用记录契约

参考 v1.1.0 extensions 文档的嵌套调用契约：子调用不产生独立 transcript 条目，结果只回给调用方工具；会话在父结果上保留**有界审计记录**（名称、参数、状态、时长、错误；绝不存结果正文）。采纳其契约但按状态归属审计的结论划分适用面：

| 组合路径 | 记录方式 |
| --- | --- |
| `invoke_subagent` / `invoke_dual_agent` / workflow 推理节点 | **不适用本契约**：三者均经 `SubagentLaneRunner` 拥有独立模型循环与持久化 Lane，保留完整历史以支持恢复、审批移交与写入证据；父会话只接收摘要与 Lane 引用 |
| `use_capability` 统一代理（MCP 工具 + 宿主 deferred 能力） | **首个落地者**：内层调用在父结果 metadata 留下有界 nestedCalls 记录 |

| 边界 | 太墟实现 |
| --- | --- |
| 契约类型 | `harness/directory/NestedCallRecord`：`NestedCallRecord`（toolCallId=`<parentToolCallId>/<n>`、name、status、durationMs、argumentsPreview、error）+ `NestedCallLog`（complete 标记 + 有界列表 + 独立递增 totalCalls；旧记录由保留 ID 推导下一序号） |
| 写入点 | `CapabilityToolRouter.call()`：宿主 deferred 与 MCP 两条内层路径统一计时并落 `metadata["nested_calls"]`；被目录拒绝的尝试记为 `blocked`，元操作 list/inspect/decline 不记录 |
| 有界化 | 最多 256 条（超出丢最旧，序号继续递增）；参数摘要截断 8 KiB、错误截断 512 字符；任意记录或文本截断均置 `complete=false`，后续追加不复原 |
| 脱敏 | `argRedactor` 由调用方注入（ToolExecutor 传 `SecretRedactor.redact`）——参数与错误即使不存结果正文也可能含密钥 |
| 上下文与持久化 | metadata 随 ToolResult 持久化但**不进入模型上下文**；写入证据语义不变 |
| usage 归属 | 当前落地者均为非模型消耗型内层调用，无重复计费面；模型驱动组合工具的「内层 usage 逐层累加进父结果、父只报自身消耗」规则随契约生效 |

新增测试：`HostCapabilityDirectoryTest`（拆分覆盖不变量、只读留 direct、校验面 = 执行器接受面、deferred 调用与直接调用的审批等价、PLAN 委派拦截、路由器零 MCP 依赖、嵌套记录脱敏与 blocked 审计）、`AnnotationEscalationTest`（升级只升不降、缺省不参与、openWorld 被只读声明压制、免审判定不受影响）；`BuiltinToolContractTest` 的 host 合同改为 direct/deferred 双向断言。

已知边界：注解升级对外部 MCP 服务的实际效力有限——非浏览器外部工具的基础判定已是 high，escalation 仅对浏览器 medium 档与 REQUEST 模式的 rememberability 产生可观察效果；目录注解的完整价值待后续把 per-tool 元数据接入目录后兑现。model-only 暴露级别未引入（ask_user 等派发型工具暂无被组合工具递归调用的通路），留待组合执行落地时一并评估。真机 deferred「发现 → 调用」两跳交互与压缩/导出对 nestedCalls 的消费留待对应功能接入。

## 阶段 8：codemode 脚本引擎

pi 的 codemode 暴露级别落地：模型把多轮能力调用合并成一段 JS（循环、条件、聚合），减少往返轮次。引擎选型 Rhino（纯 JVM、Android 可用、MPL-2.0 与 GPL-3.0 兼容）——Android 必须解释模式（optimizationLevel=-1，dex 不支持 Rhino 运行期字节码生成）。

| 边界 | 太墟实现 |
| --- | --- |
| 入口 | `use_capability` 新增 `action="script"`：`code`（必填，≤32 KiB）+ `timeout_seconds`（1-300，默认 60） |
| 脚本 API | `capability.call(server, tool, args)` → `{ok, output}`；`capability.list()` / `capability.inspect(server)` → 能力域 JSON；脚本返回值（字符串或可 JSON 化对象）即结果正文（64 KiB 截断） |
| 审批不绕行 | `ScriptCapabilityDispatcher` 校验每条调用并重入 `ToolExecutor.execute`，复用 PLAN、审批、检查点与脱敏。MCP 首次发现后的注解参与审批；普通失败以 `{ok:false}` 暴露给脚本 |
| 审批恢复 | 待审批 / 后台 Lane 交接立即终止脚本，控制标记传播到父结果；请求只包含当前 call，批准后单次执行并提示模型继续剩余任务，不重放此前操作 |
| 沙箱化 | ClassShutter 全禁 Java 互操作（脚本无法触达 java.*/反射）；无文件/网络/进程 API；指令观察器检查父 Job 与单调时钟 deadline；内层调用继承取消并受剩余时间预算约束 |
| 结构 | `CapabilityToolRouter` 的 script 必须注入受控入口，缺失时拒绝执行；直接 call 保留 `invokeCapability` 分发与审计路径，脚本通过重入直接 call 复用实际执行路径 |

新增测试：`CapabilityScriptRunnerTest` 7 项（多调用编排、Java 全禁、deadline 熔断、blocked 感知与改道、内层错误可见、超长代码拒绝、对象 JSON 化）+ 路由器集成测试（script 动作的内层嵌套记录逐条落 metadata）。`use_capability` schema 的 action 枚举扩为 5 值并新增 code/timeout_seconds 参数（护栏测试不受影响）。

Review 修复回归：`CapabilityScriptPolicyTest` 经真实执行器与 Room 审批仓库验证 REQUEST、PLAN、后台交接、批准后单次重放、检查点、参数校验及 MockWebServer MCP 注解/执行链路；运行器新增取消传播、JS catch 不可吞控制状态、内层超时与超时后返回检查；`NestedCallRecordTest` 验证持续淘汰后的唯一 ID、旧数据兼容及参数/错误截断标记。

已知边界：脚本内暂不支持 MCP 能力域的自动发现（inspect 需在脚本外先做，发现结果按名传入）；内层调用 `runBlocking` 占用单个 IO 线程至完成；prompt 侧 script 用例引导需真机会话检验；安全边界同步登记于 [`SECURITY_SURFACE.md`](SECURITY_SURFACE.md) 第 6 节。

## 真机验证清单

汇总阶段 4/6/7 所有「尚未真机验证」项的手工核验步骤。前置：真机 + Shizuku 或 Root 授权 + ASSISTED 与 REQUEST 各一个会话；可选一个已启用 MCP 服务。

### A. deferred 两跳（阶段 6 主路径）

1. **发现 → 调用**：ASSISTED 会话下达「在虚拟屏打开设置并截图」——预期模型先 `use_capability(action="inspect", server="host")` 再 `action="call"` 调 `virtual_screen_task`；悬浮窗弹出，标题栏实时显示步骤。
2. **transcript 无重复条目**：确认上述调用在会话里只有 inspect 与 call 两条工具记录，内层执行不产生独立条目。
3. **PLAN 拦截**：只读规划下同类请求 → 虚拟屏调用被硬拦截，文案指向「只读规划仅允许只读查询」。

### B. 审批等价性（直接调用 vs 经代理）

4. **ASSISTED GUI 放行**：直接 `host + virtual_screen_click` → 自动放行，与经代理调用行为一致。
5. **REQUEST 逐条审批 + 不可记住**：REQUEST 会话经代理调用 `virtual_screen_task` → 弹审批；「本会话内记住」不可用（critical 级）。

### C. codemode（阶段 8）

6. **批量编排与审批**：允许自动执行的调用可在一次脚本中完成多条内层；REQUEST 下遇到待审批调用即停止，批准后只执行该条，剩余动作由模型继续发起；确认此前完成的操作没有重放。
7. **超时熔断**：脚本 `while(true){}` → 60 秒中止，提示部分结果可用。
8. **无 Java 逃逸**：脚本尝试 `java.lang.Runtime` → 失败信息确认被 ClassShutter 拦截。

### D. 校验面与重放

9. **旧会话重放**：升级前会话里直接 `host + virtual_screen_*` 的调用 → 恢复/重放不被 enum 校验拒绝（校验面 = 执行器接受面）。
10. **模型历史模仿**：新会话模型直接发 `host + virtual_screen_*` → 校验放行（union 面）、审批矩阵照旧；提示词引导仍应优先指向 use_capability。

### E. 观测与卫生

11. **嵌套留痕持久化**：use_capability 结果在 Room 中携带 `metadata["nested_calls"]`（UI 未消费时验证持久化即可）。
12. **脱敏抽查**：触发含密钥样式的工具输出 → logcat 与会话存储中经 SecretRedactor 遮蔽。
13. **MCP 首次自动启动**：inspect 未连接的 MCP 服务 → 按需拉起进程并返回清单。
