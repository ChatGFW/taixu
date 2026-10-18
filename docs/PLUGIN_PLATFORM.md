# 太墟插件平台方案与迁移路线

状态：P1–P4 已完成本地实现、单元测试与 Debug 打包；P5 已接入静态 Web 页面宿主与页面级授权首片，范围见第 18 节。尚未做真机交互验证。
目标是让业务能力通过插件装配，逐步形成稳定内核与开放生态。P3 已接入 v1 目录适配与 v2 PROCESS 协议；P4 接入编译时 Harness 扩展。P5 的外部 Web 包、HostBridge 迁移及全权模式与 P6 仍待实现。原生页面和 Harness 回调能力不会自动赋予 `.txplugin` 包。

## 1. 目标与当前事实

“一切皆插件”意味着聊天、终端、浏览器、工作流、Provider、执行后端都能通过公开扩展点接入。
内置功能和第三方功能使用相同契约；新插件不应要求修改顶层页面分发器或核心执行循环。
不以开放 Root 或任意 Shell 代替扩展 API。

当前实现证据：

| 位置 | 当前职责与限制 |
| --- | --- |
| [`ToolManifest`](../core/model/src/main/java/top/wkbin/taixu/core/model/ToolManifest.kt) | schema 1 兼容元数据；schema 2 增加 PROCESS 身份、SDK/依赖版本与资源/完整性声明，详见 [包协议](PLUGIN_PACKAGE_V2.md) |
| [`GenericRecipeInstaller`](../tools/src/main/java/top/wkbin/taixu/runtime/tools/GenericRecipeInstaller.kt) | 执行安装/卸载脚本；`offlineOnly` 跳过自动依赖获取，不阻止网络访问 |
| [`ToolManifestValidator`](../tools/src/main/java/top/wkbin/taixu/core/tools/ToolManifestValidator.kt) | 四种权限字符串是校验/展示数据，不是脚本隔离机制 |
| [`InstallTransactionManager`](../tools/src/main/java/top/wkbin/taixu/core/tools/InstallTransactionManager.kt) | 快照仅覆盖插件程序目录，不覆盖全部全局资源 |
| [`TaiXuNavHost`](../feature/navigation/src/main/java/top/wkbin/taixu/ui/navigation/TaiXuNavHost.kt) | 终端/浏览器页面及聊天嵌入面板经插件宿主装配，导航状态归 Navigation3 |
| [`NativePluginPlatform`](../feature/navigation/src/main/java/top/wkbin/taixu/ui/navigation/NativePluginPlatform.kt) | 编译时插件集合、页面目录和生命周期控制器；Koin 仍由 navigationModule 集中装配 |
| [`AgentPlugin`](../core/model/src/main/java/top/wkbin/taixu/core/model/AgentPlugin.kt) | 既有插件展示模型；不视为可执行插件加载协议 |

安全事实以 [`SECURITY_SURFACE.md`](SECURITY_SURFACE.md) 为准：PRoot 是兼容层，同 uid 代码没有恶意代码隔离；
沙箱可读的 HostBridge key 不是插件独立身份，也不能作为未来插件级授权凭据。

## 2. 内核保留什么

内核持有插件身份、兼容性校验、依赖图、扩展注册、授权代理、凭证代理、资源归属、状态恢复与安装事务。
Android Application/Activity、导航容器、最小启动与恢复界面也属于宿主基础设施。
业务功能不进入内核；内核通过接口调用执行、持久化和界面适配器，不反向依赖业务 feature。

现有工具审批、PLAN 硬门禁、会话固定执行目标、子任务资源归属、输出脱敏继续生效。
插件不能通过事件、检查点、上下文贡献或自报权限绕过这些规则。

目标依赖方向：

```text
app / feature:navigation（装配）
    -> 业务插件（终端、浏览器、工作流……）
    -> plugin:ui（原生 UI 适配） -> plugin:api（平台无关契约与目录）
    -> plugin:runtime（生命周期、资源作用域；授权代理待实现） -> plugin:api
业务插件 -> plugin:api / 对应宿主适配器；插件间通过服务契约交互
```

UI、进程执行和授权机制分层；不把 Compose、Koin、Android Context 或 Room DAO 放入纯 Kotlin SDK。
P1 的目录在 `plugin:api` 中实现无副作用的注册校验；P2 已拆出纯 Kotlin `plugin:runtime`。

## 3. 三种执行形态

| 形态 | 交付与用途 | 信任边界 |
| --- | --- | --- |
| 进程插件 | 保留 `.txplugin` v1；运行 CLI、MCP、语言服务与开发工具链 | 实际权限由执行账号/Android uid 决定；不能宣称 manifest 限制了任意脚本 |
| Web 插件 | 静态资源或受管理服务，提供面板、编辑器与设置页 | 独立 WebView 策略、固定来源、逐请求能力代理；禁止暴露通用 JS 执行桥 |
| 原生插件 | P1 编译时打包 Compose 功能，后续评估独立 APK/IPC；提供深度 Android 集成 | 同进程原生代码属于受信任代码，SDK 授权无法隔离反射或直接平台调用 |

运行时 Dex/任意 Kotlin 热加载不进入首批范围。后续需单独决策签名、ABI、依赖冲突、商店发布约束与恢复策略。
需要隔离的第三方代码优先使用独立进程/独立 uid 与 IPC，不能只给同 uid 的脚本增加权限字段。

## 4. 统一身份、扩展与版本

- 插件 ID 使用稳定的小写点分命名，例如 `taixu.terminal`；保留 `taixu.*` 给内置装配来源。
- 扩展 ID 在插件内唯一，例如 `main`。全局引用为 `(pluginId, extensionId)`，不使用可碰撞的显示名称。
- SDK 版本与插件版本分开。SDK 先采用整数 API level 区间；插件版本是独立发布字符串，不用字符串排序判断升级。
- 安装来源和信任等级由宿主验证，插件清单不能自称“内置”或“全权”。
- 依赖缺失、依赖环、重复 ID、API 不兼容或扩展类型不匹配时拒绝发布目录；构建完成前不暴露半注册状态。
- P1 为不可变的编译时目录，不支持热启停或热替换，也不执行插件安装、命令和模型调用。
- P1 原生注册对象是应用编译时装配的受信任代码，不接受外部 JSON 注册；保留命名空间在后续导入边界强制校验。

P1 契约只落地原生页面扩展，避免为尚未验证的工具/Provider/后端提前制造空接口。
未来每个扩展点都有宿主定义的类型、输入输出、版本与资源生命周期，不用任意 `Map<String, Any>` 作为执行协议。
页面路由参数仅传可持久化的小型非敏感数据（如项目名）；凭证、文件正文和大对象通过宿主代理或存储引用取得。

## 5. 扩展点逐步开放

| 扩展点 | 宿主负责 | 插件提供 |
| --- | --- | --- |
| 页面/面板 | 导航栈、ViewModel 与保存状态的 owner、返回动作、主题与窗口 Insets | 页面内容与入口描述，参数使用约定键，不持有导航容器 |
| 设置/导航菜单 | 排序、可见性、搜索和恢复 | 设置分组、菜单描述与页面引用 |
| Agent 工具 | 名称映射、Schema 校验、审批、PLAN、执行环境绑定、脱敏与持久化屏障 | 工具 Schema 与受管理执行器 |
| 上下文/检查点 | 容量预算、来源标注、脱敏与低权威投影 | 上下文数据或否决意见；不授予权限、不替换执行结果 |
| Provider | 凭证引用、传输策略、取消和诊断 | 协议适配、模型能力描述；默认不接触其他 Provider 的密钥 |
| 执行后端 | 会话固定目标、能力探测、资源排空 | 文件/命令/PTY/下载能力适配，不支持项明确失败 |
| 工作流 | 定义校验、调度、审批、恢复和未知副作用处理 | 节点类型、编辑字段、触发器与执行器 |
| 宿主能力 | Android 权限、目标约束、授权、审计和撤销 | 结构化请求，例如安装指定 APK、读取指定应用日志 |

## 6. 权限与全权模式

后续采用“声明请求 + 用户授权 + 宿主执行时校验”。权限至少绑定插件、动作、目标、执行环境和有效期。
一次性授权与持续授权分开；升级新增能力重新确认，撤销后阻止新请求并按资源所有者停止可回收资源。
Android 系统权限是独立前提，插件授权不能提升 App 本身的权限。

开放开发者全权模式：用户明确授予指定插件和执行目标，清晰展示任意命令/文件/宿主操作的实际范围，允许撤销。
全权不代表绕过 Android、跨会话执行目标、访问所有插件凭证或改变 PLAN 模式。
恢复、重启和升级不自动扩大授权；授权存档损坏时拒绝推断为允许。

HostBridge 迁移前不得声称具备插件级隔离。迁移时将共享 key 与任意 shell 路径拆开，
使用宿主签发、可撤销的短期凭据和结构化动作；任意 shell 单列为全权能力。
如果代码仍能读取共享 key 或直达同 uid 数据，只能称为应用层代理门控，不能声称强隔离。

## 7. 生命周期与资源（目标；P2 已实现部分见第 13 节）

期望状态：发现 -> 校验 -> 已就绪 -> 启动 -> 运行 -> 排空 -> 停止；失败保留可诊断状态。
依赖按拓扑启动、逆序停止；单插件失败不把无关插件标记为失败。
每次激活拥有独立代次、协程作用域和资源账本，登记进程、MCP 连接、端口、订阅和临时文件。
停止先拒绝新调用，等待在途调用或取消，再回收资源；无法确认退出的资源不能标记为已释放。
进程死亡恢复只重建状态与重新授权所需信息，不能自动重放安装、发消息或未知结果的副作用。

目录、凭证引用、授权与插件业务数据分别存储；应用配置通过现有 PreferenceFacades，业务存储通过 Repository。
插件私有数据带命名空间和 Schema 版本，迁移失败保留旧数据，默认卸载保留用户数据。
全局 SDK/共享依赖声明所有权与引用关系，只有最后一个使用者释放后才允许删除。

## 8. 包格式兼容

`.txplugin` v1 的导入与安装行为保持兼容，不把四个权限标签解释成已强制执行的隔离策略。
后续 v2 添加 SDK 区间、执行形态、扩展声明、插件依赖和能力请求，使用显式版本解析器。
v1 经适配器进入统一目录时保留原 ID 映射，不用新命名规则直接拒绝已有包。
v2 不兼容版本明确拒绝；无静默降级、无同 ID 覆盖内置插件。升级先校验完整性、兼容性和权限差异，再提交事务。

## 9. 实施阶段与验收

| 阶段 | 交付 | 验收门槛 |
| --- | --- | --- |
| P1 已实现 | 纯 Kotlin 描述/页面引用/目录校验；Compose 页面注册和宿主；终端页面及聊天终端面板接入 | 真实导航路径通过注册表渲染；旧路由与 ViewModel owner 保留；重复/缺失/环/API/声明实现不一致拒绝测试；缺失页面可返回 |
| P2 已实现 | 生命周期与资源作用域；浏览器第二个界面插件；菜单/设置贡献和统一插件管理页 | 插件启停与依赖失败可见；页面入口检查可用性；在途租约排空、假资源回收失败及启用选择冷启动恢复测试；真实进程与真机待验收 |
| P3 已接入，真机待验收 | v1 目录适配、v2 PROCESS 包协议、依赖版本与资源归属声明 | 离线校验与安装测试；保留工具目录事务；不宣称全局副作用回滚 |
| P4 本地验收通过 | 工具/上下文/Provider/工作流扩展 | 假 Provider 完整链路：输入 -> 响应 -> 工具 -> 审批 -> 恢复 -> Web 完成；无 Key 通过 |
| P5 部分接入 | 已接入静态 Web 宿主、结构化能力代理、页面级限时授权；外部 Web 包、共享桥迁移及全权模式待实现 | 来源校验、授权撤销、越权拒绝本地测试；WebView 真机验证待完成 |
| P6 | 后端插件化、外部原生 IPC、SDK 示例与兼容矩阵 | 第三方无需修改核心分发器完成示例；跨版本协议与故障恢复验收 |

阶段按可交付切片推进，不在 P1 同时重写 Agent、导航栈、安装器和权限体系。
终端 P1 是“页面扩展接入”，其 PTY 引擎、设置和菜单仍由原模块提供，不能称为终端已完整可卸载。
全部功能插件化是路线终点，不是本轮完成声明。

## 10. P1 初始开发约定与验证（历史）

- `plugin:api`：无 Android/Compose/Koin/数据库/执行依赖；目录只校验受信任编译时贡献。
- `plugin:ui`：原生页面契约和安全的缺失页面呈现；不会通过 JSON 加载任意代码。
- `feature:terminal`：提供原生页面插件工厂，不访问其他 feature。
- `feature:navigation`：唯一业务装配入口，构造页面目录；终端旧路由适配到通用页面宿主。
- 此阶段不提供启停按钮和持久化授权，避免 UI 承诺运行时并不存在的能力。
- 单元测试覆盖目录原子校验、确定性顺序、依赖错误、页面实现与声明的一致性、参数/返回动作传递。
- 编译终端与导航；修改 DI 后执行依赖图验证、完整单元测试及 Debug 打包；保留未做真机验证的说明。

建议命令（PowerShell，先按 [`COMMANDS.md`](COMMANDS.md) 设置 JDK）：

```powershell
.\gradlew.bat :plugin:api:test :plugin:ui:testDebugUnitTest :feature:terminal:testDebugUnitTest :feature:navigation:compileDebugKotlin architectureCheck --console=plain
.\gradlew.bat test :app:assembleDebug --console=plain
```

## 11. P1 原生页面接入示例

在业务 feature 中依赖 `:plugin:ui`，提供工厂（不创建 PTY、网络连接或其他副作用）：

```kotlin
fun notesPlugin() = NativePagePlugin(
    descriptor = PluginDescriptor(
        id = "example.notes",
        version = "1.0.0",
        extensions = listOf(PluginExtension("main", PluginExtensionPoint.PAGE)),
    ),
    pages = listOf(NativePluginPage("main") { request ->
        NotesScreen(project = request.arguments["project"].orEmpty(), onBack = request.onBack)
    }),
)
```

上例类型分别来自 `top.wkbin.taixu.plugin.api` 与 `top.wkbin.taixu.plugin.ui`；`NotesScreen` 是插件自身页面。
在 `NativePluginPlatform.plugins` 集合中加入该工厂，按架构策略登记模块依赖。
宿主使用 `PluginPageDestination("example.notes", "main", mapOf("project" to project))` 打开页面，
无需增加一个 `entry<NotesDestination>` 分支。P2 可在工厂的 `entries` 中声明菜单或设置入口，见下节。
未知页面引用呈现“此功能暂不可用”，独立页面提供返回动作；外部元数据不会触发类加载或命令执行。

## 12. P1 验收记录（2026-10-15）

- 新增 19 项测试通过：目录 10 项、原生页面注册表 5 项、Compose 页面宿主 1 项、导航/装配/序列化 3 项。
- 专项命令通过：`:plugin:api:test :plugin:ui:testDebugUnitTest :feature:navigation:testDebugUnitTest :feature:terminal:testDebugUnitTest architectureCheck`。终端模块自身无独立测试源，其贡献与真实装配由导航测试覆盖。
- `architectureCheck` 对当前 35 个模块的依赖、无环、import 与尺寸棘轮检查通过；导航文件拆出路由声明后同步收紧其尺寸基线。
- `test :app:assembleDebug --continue` 全库单元测试与 Debug 打包通过，包含应用依赖图回归；未调用真实模型 API。
- 方案文档本地链接与本轮改动的 `git diff --check` 通过。
- 未进行真机页面切换、进程死亡恢复或 PTY 交互验证；目录测试与 Compose 宿主测试不替代这些验收。

## 13. P2 当前实现与边界

新增的 [`PluginRuntime`](../plugin/runtime/src/main/kotlin/top/wkbin/taixu/plugin/runtime/PluginRuntime.kt) 统一管理编译时插件，
状态为 `READY / STARTING / RUNNING / DRAINING / STOPPED / BLOCKED / FAILED`。
目录保持不可变；运行代次、页面租约与资源账本位于独立控制器，不在静态目录上附加可变开关。

- 初始化按目录拓扑顺序激活；禁用依赖会逆序停止依赖它的插件，但保留这些插件自己的启用选择。
- 每代 [`PluginActivation`](../plugin/runtime/src/main/kotlin/top/wkbin/taixu/plugin/runtime/PluginActivation.kt) 提供独立协程 scope 与 `own(id, release)` 资源登记。资源释放函数须幂等，并只在确认释放后返回 true。
- 停用先关闭准入、令页面退出，等待已有租约归还；随后取消并等待代次任务退出，再逆序释放资源。一个资源释放失败时保留它及更早的资源；未退出的依赖者会阻止其依赖释放。
- 默认每个插件启动和单次资源排空各使用 10 秒协作式超时；超时保留失败状态和未确认资源。用户可重试，不能用“请求已取消”替代真实退出确认。生命周期/释放回调须使用可取消的挂起接口，自行将阻塞 IO 切到适当 dispatcher；同进程阻塞或忽略取消的回调不保证按时返回。
- 启动与后台任务失败不向状态复制原始异常。后台失败立即关闭该代准入，再串行处理清理及依赖阻塞；无关插件继续可用。失败的初始化不会因其他插件操作而自动重放。
- 用户选择先写入 [`PluginPreferences`](../core/datastore/src/main/java/top/wkbin/taixu/core/datastore/PluginPreferences.kt)，再完成有界生命周期转换；UI 取消不能把已提交选择与转换拆开。读取失败不假定默认启用，写入失败不改变当前选择；管理页显示重试入口。
- 冷启动只恢复插件启用选择，不保存或重放旧命令、安装动作、页面参数、资源释放脚本和运行结果。代次与租约只在进程内有效；不据此推断进程死亡前的外部进程已退出。

[`TerminalPlugin`](../feature/terminal/src/main/java/top/wkbin/taixu/ui/terminal/TerminalPlugin.kt) 与
[`BrowserPlugin`](../feature/browser/src/main/java/top/wkbin/taixu/ui/browser/BrowserPlugin.kt) 提供受信任的界面插件。
通用页面、旧终端/浏览器路由、聊天双栏面板均经过生命周期准入；页面退出会释放租约，重新启用使用新代次。
浏览器仍复用 Activity 级 ViewModel，嵌入面板原先没有退出按钮时继续隐藏该按钮。

**插件当前拥有的是界面作用域，不是整个 PTY/WebView/MCP 引擎。** 禁用后界面不可用，
已有终端进程、浏览器自动化和 MCP 服务继续由原资源所有者管理；不调用共享引擎 shutdown 冒充页面停用。
本轮资源失败测试使用假释放器和真实协程任务，没有新增原生进程终止后端。
原生代码仍是同进程受信任代码；准入与资源账本不是恶意插件隔离，也不扩大 HostBridge、Root 或审批权限。

## 14. P2 管理与贡献入口

设置根页新增“应用功能插件”，打开 [`PluginManagerScreen`](../plugin/ui/src/main/java/top/wkbin/taixu/plugin/ui/PluginManagerScreen.kt)，
展示选择、有效状态、失败重试和页面入口。插件停止或依赖不可用时，贡献链接隐藏；旧路由若仍可达则呈现不可用页面。

插件可在 `NativePagePlugin` 中提供本地化的 `title` 和 `entries`：

```kotlin
entries = listOf(
    NativePluginEntry("open", "main", PluginEntryLocation.MENU) { "笔记" },
    NativePluginEntry("settings", "main", PluginEntryLocation.SETTINGS) { "笔记设置入口" },
)
```

entry ID 在插件内唯一，目标必须是同一插件已声明并实现的页面。MENU 注入共享导航抽屉，SETTINGS 注入设置根页，
feature/components 与 feature/settings 只消费宿主提供的 Compose slot，不依赖插件实现。
title 工厂应使用所属模块的本地化资源；参数与页面内容仍由插件定义。
生命周期的 `activate` 只用于初始化、恢复元数据和登记资源；安装、发消息等外部动作必须走明确操作，不能借冷启动自动重放。

P2 专项命令：

```powershell
.\gradlew.bat :plugin:runtime:test :plugin:ui:testDebugUnitTest :feature:navigation:testDebugUnitTest :core:datastore:testDebugUnitTest architectureCheck --console=plain
.\gradlew.bat test :app:assembleDebug --continue --console=plain
```

## 15. P2 验收记录（2026-10-15）

- 本阶段新增 20 项测试：生命周期 16 项、偏好持久化 1 项、页面租约重组 1 项、贡献入口校验 1 项、管理路由恢复 1 项；专项测试已通过。
- 生命周期测试覆盖在途排空、资源释放失败/超时、依赖保留、后台异常、未协作退出的任务、失败后禁止自动重放，以及用户取消不拆开已提交选择与状态转换。
- Compose 测试确认停用触发页面租约归还、重新启用得到新代次；导航测试确认终端/浏览器均经真实装配注册，贡献入口指向有效页面。
- 36 个模块的架构门禁、全库单元测试、应用依赖图与 Debug 打包通过，包含并发准入修正后的最终回归。管理页开关补充了插件名称的无障碍标签。
- 文档本地链接与改动格式检查通过；未调用真实模型 API，尚未做真机交互、真实进程资源回收或进程死亡恢复验收。

## 16. P3 接入范围

包协议与开发示例见 [`PLUGIN_PACKAGE_V2.md`](PLUGIN_PACKAGE_V2.md)。本阶段新增纯 Kotlin `plugin:packages`，把旧工具映射为 `legacy.tool-*` 目录条目，并支持具有独立身份、SDK 区间、SemVer 依赖、资源及 payload 完整性声明的 v2 PROCESS 包。

统一管理页呈现页面插件与当前发行版的进程插件/兼容工具，后者继续通过工具详情管理安装、启动和卸载。v2 导入确认展示新增能力、插件依赖和资源声明；确认清单、安装版本与目标发行版固定。哈希、路径或同版本冲突失败时不切换选中包。包目录与进程安装是两个提交阶段，安装失败可能保留新包供重试。

安装和卸载使用实际安装版本的最小收据进行引用检查，拒绝破坏既有依赖者的升级和有依赖者的卸载。共享资源按 key/path/hash 相容检查，工具/数据/命令入口自动计入独占资源；声明全局路径保留。既有安装从“安装”入口被替换时也保留工具目录快照，并按更新任务记录，供中断恢复使用。

不在本阶段提供插件独立权限代理、任意全局资源回滚、动态原生加载、外部 Web 插件执行或版本切换 UI。既有目录事务包含尽力清理逻辑，不能据此承诺所有文件系统故障下必然恢复。真机 PRoot、安装脚本实际全局副作用和进程死亡窗口仍待验收。

验证命令（无需 API Key）：

```powershell
.\gradlew.bat :plugin:packages:test :tools:testDebugUnitTest architectureCheck --console=plain
.\gradlew.bat test :app:assembleDebug architectureCheck --continue --console=plain
```

P3 本地验收记录（2026-10-15）：新增 27 项测试全部通过（协议/策略 11、包存储/收据 10、安装与目录恢复集成 6）。覆盖 v1 兼容、v2 严格字段与 API 拒绝、SemVer、依赖升级/卸载/循环、共享及命令资源冲突、哈希集合、路径与重复条目、预览绑定、不可覆盖版本、损坏选择指针、取消、收据隔离及不保存环境秘密、发行版切换、严格验证/命令入口失败，以及程序快照恢复并保留数据和全局副作用。全库 `test :app:assembleDebug architectureCheck --continue` 最终通过，包含应用 DI 图与 37 模块架构门禁；未调用真实模型 API。真机安装、交互与进程死亡窗口仍未验收。

## 17. P4 实现与边界

契约、装配与示例见 [`PLUGIN_HARNESS_EXTENSIONS.md`](PLUGIN_HARNESS_EXTENSIONS.md)。新增 `HarnessPluginRegistry` 对工具、上下文、检查点、Provider 和工作流贡献进行类型/身份/Schema 校验；`.txplugin` 不参与回调注册。

- 工具复用 `use_capability`，以 `plugin.<id>` 为能力域；直接调用和脚本嵌套均保留审批、PLAN、后台交接及结果屏障。插件只返回成功/失败与文字。
- 背景资料采用有来源标注的合成 user 消息，先按当前环境脱敏，再按项/总量限额，计入请求预算；不能修改 system 权限。
- 三种现有模型协议通过插件工厂装配；可按协议与 Provider ID 精确增加专属适配器。传输、取消、Key 轮换和诊断继续由现有请求边界负责。
- 等待节点迁入工厂；通用 `PLUGIN_ACTION` 支持注册自定义节点并在编辑器配置字面 JSON 参数。节点每次调用前经新令牌确认，调度、持久化与恢复审查仍归宿主；动态扩图不能新增插件动作。
- 管理页显示编译时 Harness 贡献；其启停/卸载不接入 P2 页面开关。没有外部 Kotlin/原生代码加载，也不提供同 uid 恶意代码隔离。

工作流节点主题、元数据与默认配置从原大文件拆出，收紧对应尺寸基线。验证包含严格嵌套参数、预算与取消、Provider 选择、审批交接/脚本重入、带令牌节点恢复及真实 Web 输入/完成通道。没有调用真实付费模型 API；真机界面、PRoot 和进程死亡窗口仍待验收。

P4 本地验收记录（2026-10-15）：新增 22 项测试通过（注册/上下文/Provider 10、严格 Schema 3、工具策略 3、节点确认 4、工作流恢复 1、Web 全链路 1）。Web 用例通过独立 Koin 插件贡献进入实际 Harness：无 API Key、禁止网络、假 LinuxRuntime，验证用户输入受理、背景资料低权威投影、插件 Provider 响应、审批前零执行、批准后执行一次及 Web 网关完成状态；节点恢复验证成功节点不重放、旧令牌不可批准新请求。最终全库 `test :app:assembleDebug architectureCheck --continue` 通过，含应用 DI 图和 37 模块架构检查。文档链接与改动格式检查通过；未做真机验收。

## 18. P5 静态 Web 宿主与授权首片（2026-10-18）

实现和接入示例见 [`PLUGIN_WEB_EXTENSIONS.md`](PLUGIN_WEB_EXTENSIONS.md)。受信任的宿主代码使用 `WebPagePlugin` 注册静态资源与结构化操作，复用现有 PAGE 目录和 P2 生命周期；不开放外部 `.txplugin` 的 Web 执行形态。

- 每个页面实例分配独立 HTTPS 来源，单独创建 WebView；只响应有界静态资源快照，拒绝外部网络、文件、Content URI、跳转、iframe、Worker、下载及系统权限请求。消息通过支持来源校验的 AndroidX WebKit 通道传递，不使用通用 JavaScriptInterface 或 JS 求值执行桥。
- 宿主绑定插件、版本、运行代次、来源和 `android:local` 环境；JS 只能提交请求 ID、动作和目标。只有宿主登记的精确动作/目标能够进入原生授权弹窗。
- 支持单次允许、当前页面最多 5 分钟允许、拒绝和撤销。授权不落盘；页面退出、重建、刷新、插件停用或新代次均不能沿用。撤销取消协作式在途任务，不能回滚已发生的副作用。
- 设置的“应用功能插件”中提供“Web 插件示例”，唯一操作是读取应用包名、Android API 与插件 API 版本。它不持有共享 HostBridge key，不调用模型、PRoot、Shell、文件或凭证仓库。

本片是应用层代理门控，不能替代 Android 权限或同 uid 隔离；旧进程插件的共享 HostBridge 路径保持原有边界。P5 后续仍需外部包加载协议、受管理服务来源、更多结构化宿主操作、共享 key 迁移与全权模式，不能把本片称为 P5 全部完成。

本片本地验收：新增 25 项测试通过（授权会话 11、页面适配 2、消息与确认集成 5、来源策略 4、静态资源 3）。最终 `:plugin:runtime:test :plugin:ui:testDebugUnitTest :feature:navigation:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug architectureCheck --continue` 通过，含应用 DI 图与 36 模块架构检查；中英文示例脚本交互冒烟、文档本地链接及改动格式检查通过。全库 `test` 尝试因既有 DataStore API 29 用例的 Robolectric 镜像下载阻塞而中止，不记作通过；未做真机 WebView 验收，未调用真实模型 API。
