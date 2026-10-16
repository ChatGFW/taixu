# 凭证与暴露面清单 (Security Surface)

> 本文逐条说明权限边界、凭证归属与暴露面，核验当前实现并标注代码出处；代码演进后请随改动更新。
>
> **一句话定性：PRoot 沙箱是 ptrace 系统调用虚拟化的兼容层，不是防恶意代码的安全边界。** guest 内进程实际以 App uid 运行；真正的安全边界是 Android 权限模型 + `ApprovalPolicyEngine` 审批门控 + 用户显式授权。对外文案应避免「安全沙箱」这类表述（另见 [`ADR_SANDBOX_BACKEND.md`](ADR_SANDBOX_BACKEND.md) 的选型依据）。

## 1. 权限域总览

| 权限域 | 实际身份 | 进入方式 | 能做什么 |
| --- | --- | --- | --- |
| App 进程 | Android app uid | — | 持有全部数据：密钥仓储（AndroidKeyStore 加密）、Room、设置、工作区 |
| PRoot guest | 名义 root，实际 App uid | ptrace 虚拟化（`--change-id=0:0`） | rootfs 内一切 + 第 3 节挂载清单；网络命名空间与 App 共享 |
| Shizuku | shell uid | `PrivilegeManager.executeShellCommand`（SHIZUKU 模式） | 宿主 shell 级命令 |
| Root | root uid | 同上（ROOT 模式，`su -c`） | 宿主任意命令 |
| 内置无线 ADB | shell uid（localhost adb） | `EmbeddedAdbManager`（HostBridge `/api/shell` 兜底） | 设备侧 shell、logcat、APK 安装 |

出处：`runtime/privilege/PrivilegeManager.kt` —— 模式判定与 `executeShellCommand`；PRoot 模式下宿主 shell 执行直接拒绝。

## 2. 密钥流：从存储到进程

### 2.1 Provider 密钥

- 存储于 `core/security/SecretManager.kt`（AndroidKeyStore 密钥加密持久化），读取后仅存在于内存数据结构，随请求头发出（`ProviderTransport`）。
- **不进入进程环境变量**：`RuntimePathManager.hostProcessEnvironment()` 只构造 `PROOT_L2S_DIR` / `PROOT_LOADER(_32)` / `TMPDIR` / `PROOT_TMP_DIR` / `LD_LIBRARY_PATH` 等路径变量（`RuntimePathManager.kt:229-244`）。
- 宿主侧子进程一律先 `environment().clear()` 再放白名单（`ProcessShellExecutor.kt:32`、`ProcessLinuxSession.startProcess`、`ProotInstaller.kt:68`）→ App 进程环境不扩散到 PRoot 与宿主辅助进程。
- JNI PTY 主描述符设置 `FD_CLOEXEC`，避免 exec 后继承主端；仅主端使用非阻塞读写，slave 保留普通终端行为（`app/src/main/cpp/pty_native.c`）。信号发送与 `waitpid(WNOHANG)` 回收由 `NativePtyChild` 串行执行，回收后不再对同一 PID 发信号。前台命令和 PTY 的终止失败保留资源，禁止把未确认退出当成可清理存储的完成状态。
- 已知例外（记录在案，风险低）：`PrivilegeManager` Root 通道 `ProcessBuilder("su", "-c", …)` 与 `ShizukuHostUserService` 的 `ProcessBuilder` 未清空环境，继承 App / Shizuku 服务进程的标准 Android 环境变量。用户密钥不在进程环境里，故不构成密钥泄漏面；**若未来向 App env 注入敏感变量，必须先给这两处补 clear**。

本地重型前台命令的资源名额仅限制已识别工作负载的并发，不改变命令、环境变量、审批或宿主权限。ProcessShellExecutor 对收尾失败的资源保留 `hasUnsettled` 状态；后续重型命令在进程分配前拒绝执行，成功清理后恢复，不把超时或取消本身当作进程已退出的证据。

### 2.2 进入沙箱的环境变量

- 固定基座（`EnvironmentResolver.baseEnvironment`）：`HOME` / `LANG` / `TMPDIR` / `PATH` / `TAIXU_BRIDGE_URL` / `TAIXU_BRIDGE_PORT` / `ANDROID_BIN_PATH` / `ANDROID_LIB_PATH` / `TERM` 等，无敏感项。
- 叠加通道：distro manifest 与 per-command / provider env（含 MCP STDIO 服务器配置的 env）→ `ProotCommandBuilder.shellCommand` 逐个 `export`（键名经 `ENVIRONMENT_KEY` 正则校验 + 单引号转义，`ProotCommandBuilder.kt:169-181`）。
- 含义：**用户在 MCP / 环境配置里写的变量（可能是第三方 API Key）会明文进入沙箱进程环境**。输出侧由 2.3 脱敏兜底；输入侧是用户配置责任，配置页宜有风险提示。

### 2.3 输出脱敏（`core/security/SecretRedactor.kt`）

- 识别范围：labelled secrets（password / secret / api_key / token / authorization / cookie…）、`Bearer xxx`、`sk-` / `AIza` / `xox` 品牌前缀、JSON 与脚本赋值中的密钥值、大陆手机号。
- privacyMode 下叠加「已知密钥值遮蔽」：以 `LinuxEnvironmentManager` 中用户配置的环境变量值作为已知 secret 列表（`ToolExecutor.kt:96-104`）。
- 已绑定的工具调用及子任务报告通过当前环境的 `outputSecrets` 获取已知值；本地后端固定发行版并在读取锁内复制快照，远端不会刷新手机当前发行版。子任务报告在保存到当前环境前完成同样的脱敏；刷新失败不会使用另一发行版的旧缓存。
- 应用点：ToolExecutor 工具结果后处理（4 处调用）、`RequestDiagnosticsStore` 请求预览、浏览器 `SecretRedactingInterceptor`。
- `history_search` 的预览和 `history_read` 的分页在切片前，使用工具管道提供的当前环境脱敏函数处理整条消息文本，避免页边界拆开已知密钥后漏匹配；结果仍走通用输出脱敏。分页偏移针对脱敏后的关联文本，不包含媒体或 Responses 不透明载荷；脱敏配置或分支变化后需要重新定位。历史页有字符上限，保留长单行而不再折叠内容，不改变历史原文、审批或重放策略。
- 本地 `base` 长输出在排水期间暂存原始字节于 Android 私有 `cacheDir/command-output`，不加入 PRoot 挂载、不进入系统备份；文件按流限制大小并在工具收尾删除，崩溃残留在下一次长输出捕获时回收。只有走完既有输出脱敏后才能进入 `.taixu-outputs`；捕获上限或排水不完整时明确标注，不能把有损捕获宣称为完整输出。此暂存仍属于 App uid 的数据，并不构成对同 uid 恶意代码的隔离边界。
- `base` / `process` / `build_script` 的模型侧文本剥离 ANSI 控制并折叠回车刷新；归一化后再次经过当前环境输出脱敏，再做有界首尾采样，避免控制序列移除后重新拼出已知密钥。呈现改变时沿既有 `.taixu-outputs` 保存归一化后二次脱敏的未采样文本，SHA-256 和字节数对应实际归档内容；原捕获若不完整，归档仍保留不完整标志；无产物能力或保存失败明确引导重新检索，不回退手机同名文件。此归一化不是完整终端模拟器，不改变真实 PTY 渲染；原日志脱敏仍有本节列出的启发式局限。
- 任务交付记录只保留已提交消息的引用和受信任后端生成的文件版本观察，不复制用户目标、工具参数、日志正文或助手结论。模型自述、命令成功和文件指纹均不升级为目标行为验证；记录明确未验证项，不授予审批权限、不触发重放。输出日志指纹按已脱敏的实际保存内容计算；APK/下载文件/图片指纹按实际二进制字节计算，不修改原文件、不复制二进制到历史记录。相对路径沿既有输出脱敏链检查，若会改变则舍弃引用并记录缺口；格式无效的元数据不原样保留。二进制指纹本身不是匿名化保证，已知内容可通过指纹关联。
- `ToolFileArtifacts` 只接受受信任 `base`、`download`、`read` 生产者；MCP 与其他工具不能直接声明此字段。构建观察有固定目录、条目、字节和时间上限，不依赖 stdout 路径。当前环境缺少文件指纹或严格枚举能力时标记不完整，不读取手机的同名文件作为替代。读取前后属性核验仅检测常见并发修改，不是恶意并发下的原子快照；本地同 uid 和远端实际账号权限不因指纹读取而获得新的隔离边界。版本引用不保证当前文件仍存在或匹配，APK 类型按后缀分类；构建变化信号携带指纹但不会自动绕过安装审批或保证安装内容匹配该版本。
- 运行内失败记忆只保存当前 operation 的参数指纹、经过清洗的工具名、结果类型和来源 ID，不复制参数、输出或模型编写的经验。动作建议来自固定实现，以低权威合成 user 背景资料投影，明确并非用户消息，不修改 system 权限或触发重放；来源 ID、指纹、容量与失效时间都校验。父子 Lane、新任务和其他会话不共用该记忆；普通同参成功不解除此前未知副作用的核验要求。
- **局限（务必知道）**：正则启发式，不是密码学保证；无标签的随机串（如 HostBridge key 本身是 UUID hex）不在模式内；Base64 等编码变形与非赋值形态不保证命中。高危场景需叠加人工检查。
- 请求诊断仅将脱敏后的有界预览和字段指纹交给 `RequestDiagnosticsRepository`，用 `SecretManager` 的 AndroidKeyStore AES/GCM 加密后原子写入 `noBackupFilesDir/request-diagnostics/requests.enc`。不保存请求头、原始请求体或媒体载荷；加密失败不回退明文。普通对话和文件内容仍可能保留，脱敏不等于匿名化。会话删除等待加密存档清理完成；存档排除系统备份，并受大小及格式校验约束。
- 请求诊断的 `context_governance (local)` 为本地组装元数据，不外发模型；只记录预算/阶段估算、固定动作和有界来源 ID，不复制正文、参数、文件路径或内容指纹。每次最多 32 条变更明细，超额计数；ID 限定 1–64 位 ASCII 字母、数字、下划线或连字符，其余不保留。报告与真实请求字段一起过已知凭据替换和整次预览脱敏，沿既有加密/删除/容量边界持久化，不授予审批权限或启动重放。

### 2.4 远程 SSH 凭证与主机身份

- 远程配置由 `RemoteServerRepository` 持有，整个快照经 `SecretManager` 加密后原子写入 `noBackupFilesDir/remote-servers.enc`；不进入备份、模型上下文或进程环境。密码、PEM/OpenSSH 私钥与私钥口令只在认证和内存脱敏列表中使用，私钥文件导入限定为用户选择的 SAF URI，最大 64 KiB，不保留 URI 授权。
- 会话的执行目标和无凭证的连接身份持久化到 Room；创建时固定，重启、回退分支及子任务复用。新增 SSH 功能前的 `unbound` 历史首次执行时固定本地发行版，不采用远程默认选择；便携导入单独标记 `restore-required`，拒绝推测主机。重新连接校验仓库中的档案与记录一致，变化时拒绝执行。无法解密的连接存档显式不可用，不默认选择本地；用户明确切回本地后保留原损坏加密文件，再恢复配置写入。
- 首次连接的 host-key 回调在认证前拒绝连接，UI 显示 SHA-256 主机指纹；用户核对并确认后再次连接。后续严格匹配保存的指纹，变化即拒绝，不自动接受，也不启用旧版 RSA/SHA-1 算法。
- Harness 会话与子任务分别持有 SSH 连接。文件操作经 SFTP 将逻辑 `/workspace` 路径映射到远程工作区，校验真实路径与新文件父目录，拒绝 `..` 和越界符号链接；命令仍拥有 SSH 用户的真实权限，不受文件工作区限制。远程用户或同机其它进程可并发改动文件，SFTP 真实路径检查不能提供对恶意远端的安全隔离。
- 远程工具沿用 PLAN/审批/输出脱敏规则；缺失的下载、产物、构建绑定能力明确失败。`host` / Android / 浏览器能力仍作用于手机，远程提示词明确区分。停止发送 TERM，必要时 KILL；服务器未确认退出时保留连接并报告失败，可重试。SSH 不保证回收命令自行脱离会话创建的后代进程。

## 3. PRoot 挂载清单（沙箱可见的宿主路径）

`read(path, query, max_results)` 的本地字面检索复用工作区路径解析，不运行 Shell、解释正则或跟随扫描中的符号链接。单文件 1 MiB、总读取 8 MiB、最多 5000 个条目、深度 16、最多 100 个命中与约 5 秒协作式预算；跳过项、达到限额均标注结果不完整。排除 `.git/.gradle/node_modules/build/.taixu-outputs` 子目录，不解析 gitignore。时间预算不能抢占阻塞中的文件系统调用，canonical/NOFOLLOW 检查也不是对同 uid 恶意并发替换的原子安全隔离。扫描开始时由 ToolExecutor 捕获当前环境的脱敏策略，通过受信任回调对完整命中行脱敏后再裁剪片段，最终输出仍沿统一脱敏链；不可读条目标记缺口后继续扫描，只有资源预算耗尽才停止。远程 `ExecutionFiles.search` 默认明确不支持。

设备性能实验见 [RUNTIME_BENCHMARKS.md](RUNTIME_BENCHMARKS.md)：`tools/runtime-benchmark.py` 仅经 debug APK 的 `run-as` 使用 App uid，在独立临时目录比较只读 inventory、临时 I/O 与 tracer 开关。实验不访问用户工作区、不复用 HostBridge 密钥、不改变生产挂载或持久化 seccomp 选择。`cacheDir` 可能被 Android 回收，不能直接承载需要持久化的依赖或凭证。

默认绑定（`ProotCommandBuilder.build/buildInteractive` + `ProotMountLayout.hostSystemPaths`）：

| 宿主路径 | guest 视角 | 说明 |
| --- | --- | --- |
| rootfs 目录 | `/`（`-r`） | App 私有目录内 |
| `/dev` `/proc` `/sys` | 同名 | 宿主系统视图；`/proc` 内容以 App 权限可见范围为准 |
| `/system` `/system_ext` `/vendor` `/product` `/odm` `/apex` `/data/app` `/data/dalvik-cache` 等 | 同名 | 面向 Android 二进制执行的系统路径（存在且可读才绑定） |
| tmp 目录 | `/tmp` | App 私有 |
| workspace 目录 | `/workspace` | Agent 工作区 |
| home 目录 | `/root` | guest HOME |
| opt 目录（含 `.bridge-key`、`bin/taixu-host`、`bin/taixu-android-exec`） | `/opt/taixu` | 桥接脚本与 API 密钥 |
| attachments 目录 | `/attachments` | 会话附件 |
| link2symlink 后备存储 | 同名绝对路径 | `rootfs/.l2s` 硬链接代理 |
| 用户配置存储挂载 | `/sdcard/*` 等自定义 guest 路径 | `StorageMountBinding.validationError` 校验 + `require` 硬门槛，DB 被篡改也不放行越界绑定（`ProotCommandBuilder.kt:236-255`） |

要点：

- guest 内进程对上述挂载的读写权限等于 App 对这些目录的权限；rootfs 本身就在 App 私有目录里。
- guest「root」是 PRoot 虚拟身份，不能越过 Android 对 App uid 的限制；setuid 位不生效（`DistroConfigurator` 的 apt hook 主动清除 setuid，避免 dpkg 卡死）。

## 4. HostBridge（127.0.0.1:7980）

| 端点 | 认证 | 动作 | 暴露面 |
| --- | --- | --- | --- |
| `GET /api/health` | **免认证** | 特权模式、Shizuku/Root/ADB 可用性、ADB 端口 | localhost 内信息泄露（低危）：沙箱内任意进程可探测权限状态 |
| `POST /api/install-apk` | Bearer | 解析路径 → 无线 ADB 静默安装 / 系统安装器 | 持 key 即可安装任意可达的 APK |
| `POST /api/shell` | Bearer | Shizuku/Root 优先，否则无线 ADB | **持 key = 在宿主执行任意 shell（shell uid 或 root）** |
| `POST /api/logcat` | Bearer | 抓取 / 清除日志 | 日志可能含其他应用的敏感信息 |

- 密钥：`HostBridge` 构造时生成 UUID，写入沙箱 `/opt/taixu/.bridge-key`（`DistroConfigurator.installHostBridgeScripts`），每次配置刷新以保证与 App 重启后同步。
- **信任模型必须写明**：key 放在沙箱可读位置，等于设计上把「沙箱内代码」视为可触发宿主特权动作的半信任方。被攻陷的沙箱内容物拿到 key 后即可在宿主侧执行 shell（受当时生效的 Shizuku/Root/ADB 状态限制）。这是功能需要而非疏漏；**「在沙箱内运行不受信代码」的场景必须先切换到 PRoot 模式或收紧桥接授权**。
- `resolveSandboxPath` 兜底分支（`HostBridge.kt:398-425`）：路径非 `/sdcard`、`/workspace`、`/attachments` 前缀时先在工作区试一次，否则**原样返回宿主绝对路径**——`install-apk` 因此可读取任意宿主路径下的 `.apk`（有后缀校验，无目录白名单）；另有工作区全树按文件名的模糊兜底扫描（maxDepth 8）。
- 日志只记录路径与结果，不记录 key（`HostBridge.kt` 各 `logger.i`）。

## 5. 工具审批与特权执行链

- `ApprovalPolicyEngine`：风险矩阵决定审批；`use_capability` 的 list / inspect / decline 为只读元操作免审；`use_capability(call)` 从 arguments 合成 `(server, tool)` 后套同一矩阵（`ApprovalPolicyEngine.kt:82-89, 305-331`）。
- 宿主动作与内置浏览器的风险级别、审批原因集中于编译进应用的 `TrustedToolRiskPolicy`，宿主能力目录的只读/ASSISTED 放行集合由它派生；浏览器工具必须匹配完整编码标识（含服务/工具摘要）或内置服务的完整 legacy 名称，不能仅凭截短服务前缀放行。外部同名工具及前缀碰撞服务不能借用规则。此次统一保持 `FULL_ACCESS` 的显式全权授权语义，PLAN 硬门禁继续独立生效；不把工具风险目录扩展为 HostBridge 的直接 HTTP 审批，也不把 Shell 启发式宣称为完整语法树检查。
- PLAN 模式：宿主侧硬拦截（`ToolExecutor → ApprovalPolicyEngine.planBlock`），提示词约束只是第二层。
- 路径边界：`HarnessPathResolver` 与 Lane 写租约约束写入范围；`ToolCheckpoint` 只能观察 / 否决 / 追加脱敏说明，不能授予权限或替换结果状态（`docs/ARCHITECTURE.md`）。
- 工具入口：主回合与 Lane 先检查工具名称，再解析并校验参数；未知名称不能借映射回退执行命令。Lane 对结构化写工具按执行器的参数视图检查租约，覆盖路径别名与包装参数；`base` 的任意 shell 写入仍不属于结构化路径租约检查范围。
- 取消：`PrivilegeManager.cancelShellCommand` 同步终止对应 Shizuku/Root 宿主子进程。
- MCP：工具 schema 不进 provider 可见面（`use_capability` 统一代理 + 护栏测试 `BuiltinToolContractTest`）；STDIO 服务器进程在 PRoot 沙箱内启动（`McpStdioTransport` → `McpStdioChannelFactory`），其 env 走 2.2 的 provider 通道。
- STDIO 空闲 TTL 可在 MCP 设置页配置为 1–60 分钟，缺省维持 10 分钟。单调时钟从请求收尾开始计时，握手/等待写锁/在途请求均受连接使用租约保护；清扫每分钟执行一次。只在进程确认关闭后显示休眠，清理失败保留重试资源并显示离线；休眠保留当前绑定环境的工具目录，下一次真实调用重新连接和握手，不重放旧请求。界面初次进入不主动唤醒有有效工具缓存的休眠服务；显式刷新仍可重新连接。HTTP MCP 不采用该进程休眠策略。

按需 MCP 的安装就绪状态在当前绑定环境中核验，不借用手机其它发行版或远端的结果。`McpCatalogAvailability` 在列表、发现和调用前投影用户偏好与就绪状态；STDIO 通道创建前再次核验。`setup_mcp_catalog.sh probe/run` 只读取本地标记及实际全局包清单，检查包名、实际版本与入口路径，直接执行该包入口，不经 npx、不联网补装。卸载后即使保留用户开启偏好也不允许启动；探针失败不扩散为可用。显式安装与升级仍运行第三方 npm 包及其安装脚本，保留既有 PRoot/App uid 和凭证环境暴露面。

工作流恢复边界（`WorkflowRunManager.resume` / `WorkflowRecovery`）：仅从原运行持久化定义继续，恢复前复核当前发行版与日志版本；STARTED 未结算的外部动作和失败节点需逐个人工确认重试，已成功的动作不重新派发。旧记录、托管进程及虚拟屏资源流程不支持继续。继续确认覆盖原计划剩余步骤及已完成的审批，等待中的审批重新请求；不提升宿主权限、不自动续跑，也不保证外部动作 exactly-once。恢复日志与结果位于既有 Room 运行快照中，保留既有输出/产物的暴露面，参见 `docs/WORKFLOW.md`。

动态扩图（`expansionResultNode`）：只接受直接成功上游的结构化 nodes/edges 提案，限制容量、类型和追加范围，不能修改已有图或新增宿主/托管资源节点。完整配置必须在应用内确认，请求令牌绑定当前提案；通知只跳转查看，不能直接批准。审批结果与图扩展使用同一个持久化屏障，后继执行前等待写入。恢复验证原始图、上游提案、审批日志和最终图一致；已确认图只复用一次，整流程重跑重新规划。提案及命令完整保存到本地历史，请勿在配置中硬编码凭证；规划和新增智能体的工具调用保留既有权限策略，扩图确认不构成任意工具调用的免审批授权。

分阶段扩图最多四层，新增审批只能引用同一提案内的智能体结果源；第四层不能再生成扩图审批。每一批提案单独校验、展示与确认，父阶段批准不授权未来未知提案；恢复等待中的审批生成新令牌，不能使用旧请求完成新阶段。拒绝后续提案不回滚前面副作用；未完成外部动作仍需按恢复策略核验重试。

工作流 JSON 导入只创建未保存的编辑草稿，不执行节点、调用模型或恢复历史。输入经大小/深度/节点连接数量限制及定义校验；运行状态快照和未知字段被拒绝。导入生成新工作流 ID，清除内置标记、时间戳及自动/命令触发绑定，节点配置和变量作为数据保留供编辑核对。保存与运行由用户单独操作，已有同 ID 定义不会被导入覆盖；后续执行仍使用既有 Harness、宿主权限和审批策略。

工作流 JSON 导出只序列化用户打开导出窗时的编辑定义快照，不包含数据库运行日志、模型结果、审批记录或 Provider 凭证仓库；节点配置和默认变量中的显式文字完整保留，不做破坏引用的自动脱敏。生成过程校验图及导入容量，禁止截断；剪贴板仅在用户点击复制后写入，并标记为敏感内容以隐藏系统剪贴板预览，该标记不限制其它应用读取。导出不自动发送、保存或执行定义，再次导入生成新 ID 并采用手动触发。

工作流文件导入/保存走 Android SAF 的 OpenDocument/CreateDocument，仅读写用户选择的文档 URI，不申请全盘存储权限或保留长期 URI 授权。读取执行字节与字符双重限制，UTF-8 解码/编码拒绝替换损坏字符；所有流在成功、失败、取消时关闭。保存窗口请求绑定生成的 JSON 文本快照，快照失效拒绝写入；关闭流前的异常均不报告成功。文档提供者可能执行远端同步，目标由用户在系统窗口选择；其流式写入不保证原子性，失败/终止可留下部分内容，应用不自动删除目标文档。

节点结构化结果窗口仅呈现已有输出，分页不改变 JSON 值；复制和文件保存只在用户点击时进行，使用打开窗口时的完整结果快照。剪贴板标记为敏感内容；结果中已存在的文字完整保留，不额外脱敏，保存沿用上述 SAF 边界。结果查看不重新执行节点、不改变审批或恢复状态；结果 JSON 与工作流定义是不同格式。

## 6. codemode 脚本执行面（use_capability action=script）

模型可写一段 JS（上限 32 KiB）经 `CapabilityScriptRunner` 在 App 进程内执行，脚本通过全局 `capability.call/inspect/list` 调用能力域。暴露面逐条：

- **Java 互操作全禁**：Rhino `ClassShutter` 对所有类名返回 false——脚本无法触达 `java.*`、反射或宿主类路径（由 `CapabilityScriptRunnerTest` 锁定）。
- **无系统 API**：脚本环境不暴露文件、网络、进程句柄；唯一出口是 capability 绑定。
- **非免审通道**：每条 `capability.call` 经 `ScriptCapabilityDispatcher` 校验后重入 `ToolExecutor.execute`，执行同一 PLAN、审批、检查点及脱敏链路；MCP 首次调用先发现工具，使注解升级参与审批。未注入受控入口的路由器拒绝脚本执行。
- **审批与恢复**：首次遇到待审批调用即停止脚本，父结果保留 `awaitingApproval` / `approvalDeferred`。审批请求绑定父调用 ID，但参数仅包含当前 `call` 及内部来源标记；批准后只执行这一条调用，模型根据结果继续剩余任务，不重放脚本及已完成副作用。后台 Lane 不创建审批 UI。
- **资源熔断与取消**：解释模式（optimizationLevel=-1，Android dex 限制）+ 指令观察器检查父 Job 和单调时钟期限（默认 60s、上限 300s）；内层调用继承父 Job，并受剩余时间预算约束，返回与结果序列化后再次核验期限。JS 的 try/catch 无法吞掉取消、超时或审批交接。结果正文 64 KiB 截断。
- **依赖**：Rhino 1.7.15（MPL-2.0，与 GPL-3.0 兼容，已记录于 libs.versions.toml）。
- **线程面**：内层调用以 `runBlocking` 占用单个 IO 线程至完成——长耗时宿主动作期间该线程不可复用（Dispatchers.IO 池 64 线程，可接受；记录在案）。

## 7. 风险登记与待办

| # | 事项 | 定级 | 状态 |
| --- | --- | --- | --- |
| 1 | PRoot 非安全边界的定性认知 | 认知 | 本文已明确；对外文案避免「安全沙箱」表述 |
| 2 | `/api/health` 免认证返回特权状态 | 低 | localhost 限制下可接受；如需收紧可复用 Bearer |
| 3 | `resolveSandboxPath` 兜底允许任意宿主绝对路径（限 `.apk`） | 低-中 | 记录在案；如需收紧可加目录白名单 |
| 4 | Root / Shizuku 通道未清空子进程环境 | 低 | App env 无用户密钥；若未来注入敏感 env 需先补 clear |
| 5 | `.bridge-key` 文件权限依赖默认 umask | 已修复 | 写入后显式 `Os.chmod(…, 0600)`（`DistroConfigurator.installHostBridgeScripts`，与脚本 0755 同一处理点） |
| 6 | SecretRedactor 启发式局限 | 提示 | 见 2.3；高危场景叠加人工检查 |
| 7 | 用户配置的 MCP / 环境变量明文进沙箱 | 提示 | 配置页宜加风险提示文案 |

## 8. 原生页面插件（P2）

`plugin:runtime` 管理受信任的编译时页面贡献。代次租约和 `own` 资源账本控制准入与排空，不是同 uid 原生代码的隔离层。
`PluginPreferences` 仅保存禁用插件 ID，不保存命令、凭证、路由参数或运行结果；读取失败阻止初始化，不默认全启用。
停止或资源取消未获确认时保留失败状态，禁止创建替代代次；依赖者未释放时保留其依赖。后台异常只投影固定失败类型，不复制异常正文。
终端/浏览器目前只迁移界面作用域，停用界面不会关闭已有 PTY、共享 WebView 引擎、Agent 浏览器工具或 MCP，也不改变审批与 HostBridge 权限。
冷启动只恢复界面选择，不重放旧副作用；外部进程恢复仍由实际资源所有者负责。详见 [`PLUGIN_PLATFORM.md`](PLUGIN_PLATFORM.md) 第 13–14 节。

## 维护约定

P4 Harness 扩展只接受受信任的编译时 Kotlin 实现。`plugin.<id>` 能力域保留原工具意图/结果屏障、PLAN、审批、后台交接、嵌套留痕与输出脱敏；插件没有声明免审或写入可信产物 metadata 的入口。插件 Schema 使用严格独立子集，不套用历史参数别名/字符串转换。背景资料先经过当前执行环境脱敏，再限额投影为标注来源的合成 user 文本并计入预算，不能修改 system 权限。

Provider 适配器通过协议与 Provider ID 精确选择，接收宿主配置的传输客户端和当前模型配置；后者可能含此 Provider 的 Key，不包含其它凭证仓库。通用 `PLUGIN_ACTION` 节点使用新令牌确认和原工作流日志/恢复审查，回调异常不承诺副作用未发生。以上是同 uid 内的合作契约；P2 页面开关不控制 Harness 扩展，不提供恶意代码隔离或外部原生加载。详见 [P4 契约](PLUGIN_HARNESS_EXTENSIONS.md)。

`.txplugin` P3：v2 导入拒绝不安全 ZIP 路径、重复条目、未知字段与 payload 哈希集合不符；版本目录不可覆盖，选中指针原子写入，复制时再次验证。安装/卸载依赖与资源检查按发行版使用实际安装版本收据；收据位于 `noBackupFilesDir/plugin-receipts`，不含脚本、环境变量或 Provider 凭证。预览确认绑定清单和目标发行版。v2 仅 PROCESS，不加载外部原生代码。

能力与资源声明不是运行时沙箱；安装脚本沿用既有 PRoot 权限及 Provider 环境注入，`offlineOnly` 也不阻断网络。共享指纹仅校验声明相容，不核验脚本实际创建的全局内容。v2 全局声明只接受 RETAIN，不执行任意卸载脚本；程序目录事务不覆盖任意全局副作用。页面启停不会撤销进程插件权限或清退后台进程。详见 [v2 包边界](PLUGIN_PACKAGE_V2.md)。

- 改动 `ProotCommandBuilder` / `ProotMountLayout` / `HostBridge` / `SecretRedactor` / `EnvironmentResolver` / `PrivilegeManager` / `CapabilityScriptRunner` 的 PR，请同步核对本文是否需要更新。
- 本文只描述事实与登记风险，不替代代码注释；修复某项时请更新第 7 节状态列。

MCP catalog 升级完成事件只携带本地发行版、服务 ID 和进程内代次，无凭证或工具参数。只有装配脚本成功及入口复核完成才发布；升级固定在捕获的发行版执行，结果直接来自脚本复核；切换 UI 选择不改变目标。旧进程按完整操作租约排空后关闭，缓存按发行版失效；SSH 连接不响应本地升级事件，工具调用不重放、不提升审批权限。
