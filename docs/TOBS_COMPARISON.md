# Tobs（DeepSeekBot）对照清单

对照日期为 2026-11-17。Tobs 使用 GitHub `BotHarness/DeepSeekBot` 的 `9c8223b` 浅克隆快照（9,141 个文件、330 篇 ADR、2,838 个 TS/TSX 文件）；太墟使用 `bd7210d6` 快照。覆盖双方 README、架构文档、ADR 与 `packages/core`、`packages/market` 的主要入口。

本轮以静态源码与文档为依据，未运行 Tobs，也未做真机验收。“已有对应”指太墟具备同类原生入口；“部分覆盖”指已有基础能力但仍存在语义、边界或归属差异；“新增候选”指本轮未定位同等入口；“明确不抄”指与太墟定位或合规边界冲突。

> 命名说明：仓库名仍是 `DeepSeekBot`，产品已改名 **Tobs**（旧 npm 包 `deepseekbot` 仍保留）。下文统一称 Tobs。

## 一句话结论

两边在“Bot 有身份、有记忆、能群聊、能定时”上高度同构，**功能清单已经拉不开差距**；真正值得抄的是三件事：

1. **唤醒与注意力的分层**（Source Event → Admission → Attention Decision → Observation，Wake 在模型之外）；
2. **记忆的常驻分层与冻结**（SOUL / MEMORY 两档 + 字符预算 + Session 冻结 + 前缀只追加）；
3. **渠道与分发的契约化封装**（Provider 能力声明、可信 provenance、幂等 outbox、可选择性导出的清单）。

反过来，太墟在群聊可靠分发、团队治理、PRoot 沙箱执行上比 Tobs 更细，这部分**不要向下对齐**。

## 汇总

| 类别 | 数量 | 判定方式 |
| --- | --- | --- |
| 已有对应 | 6 | 太墟已有同等或更强的原生入口 |
| 部分覆盖 | 5 | 有基础能力，差在语义、冻结、合并或清单字段 |
| 新增候选 | 6 | 本轮未定位同等入口 |
| 明确不抄 | 5 | 栈不匹配、定位冲突或合规风险 |

## 一、已有对应（对照即可，不要重写）

| 编号 | 对照项 | 太墟当前覆盖 | Tobs 对应 | 结论 |
| --- | --- | --- | --- | --- |
| 01 | 群聊持久投递与独立任务 | `group_deliveries`、`claimNonce` 租约、`submitting/admitted` 待核对语义、原负责人回报 | [bot-runtime-architecture.md](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/architecture/bot-runtime-architecture.md) 的 Assignment Runtime | 太墟文档更细，Tobs 也明确不宣称 exactly-once。保持现状。 |
| 02 | Bot Git 记忆库 | `BotMemoryManager`（JGit）、提交差异、`MemoryVersionOperations` 逐文件恢复 | [memory/](https://github.com/BotHarness/DeepSeekBot/tree/main/packages/core/src/memory)、[bot-zip.zh.md](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/bot-zip.zh.md) | 双方都有。差异见第 08 项的注入方式。 |
| 03 | 工具审批与完全访问 | `ApprovalMode`、Plan 只读、会话/全局策略分离、能力到期面板 | [bot-approval-policy.md](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/architecture/bot-approval-policy.md) | 机制齐备，差异见第 10 项。 |
| 04 | Bot 打包与清单 | `BotPackageService`：`bot.json` + `memory/` + ZIP | [core/src/bots/bot-zip.ts](https://github.com/BotHarness/DeepSeekBot/blob/main/packages/core/src/bots/bot-zip.ts) | 都有。差异见第 05 项与第 11 项。 |
| 05 | 像素头像与场景身份 | `PixelAvatarDesigner`、`BotAvatarEditor`、`colorHex`、`PixelOffice*` | [avatar-*](https://github.com/BotHarness/DeepSeekBot/tree/main/packages/core/src/bots) | 太墟场景更重，Tobs 强在“便携身份”口径，见第 11 项。 |
| 06 | 角色与团队模板 | 三省六部、软件交付模板、发布/审议/版本门禁 | 无同等物（Tobs 只有 PersonaBot + Assignment） | 太墟领先，不抄。 |

## 二、部分覆盖（本次真正“可对齐”的部分）

### 07 唤醒与注意力：Inbox 应当是一组事实，而不是一个队列

**太墟现状**：`BotAssignmentTracker`、`AssignmentRequestPump`、`group_deliveries`、`BotScheduleRunner` 各自持有投递状态；`SessionAttentionBanner` 只是界面投影。缺一层统一的“这条来源对哪个 Bot 可见、为什么可见、是否已被看见”的事实。

**Tobs 做法**（[ADR-0025](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0025-inbox-is-event-stream-with-triggers.md)、[runtime/attention.ts](https://github.com/BotHarness/DeepSeekBot/blob/main/packages/core/src/runtime/attention.ts)、[inbox-triage.ts](https://github.com/BotHarness/DeepSeekBot/blob/main/packages/core/src/runtime/inbox-triage.ts)）：

- `Source Event` = 不可变内容与来源事实；`Inbox Admission` = “对某 Bot 有资格”的持久理由；`Attention Decision` = observed / deferred / ignored / handled；pending 是推导，不是可变状态列。
- **Wake Policy 必须跑在 Orchestrator 之外**（睡着的 Bot 无法决定要不要叫醒自己）；`Delivery Policy` 再按 Turn/Step 状态选 `next-step` / `next-turn` / 中断。
- **Observation 只在正文或忠实摘要真正进入某一轮输入时成立**。入队、通知、人类在 UI 里看过，都不算 Bot 看到。

**可抄的**：四层事实与两条 Policy 的分离；“忽略是 Attention Decision，不是 Wake 结果”；“UI 看过 ≠ Bot 看过”。

**风险/代价**：全套落地要动 Room 表结构与迁移链。建议先用**投影**方式：新增 `BotInboxItem`（sourceKind/sourceId/botId/reason/priority/state/observedAt）作为派生表，现有 `group_deliveries`、schedule delivery、DM session 仍是 authority，等语义稳定再考虑收权。

**本轮落地（部分，已完成）**：`bot_inbox_items`（迁移 91→92）+ `BotInboxRepository` + `BotInboxProjection`。唯一索引 `(botId, sourceKind, sourceId)` 保证同源同 Bot 只准入一次；`observedAt` 只在 `submit` 真正受理时写入，拒绝记 `ignored`、中断/未知记 `deferred`；投影写失败只记日志，不阻断投递。已接 `BotScheduleRunner`、`GroupDeliveryPump`（群聊用 `GroupTurnOutcome.observed` 区分“提交前失败”与“进过轮次后失败”）与派工 `BotAssignmentRuntime`；派工 sourceId 用单条 `request.id`（一条 request 可独立 claim/admit/settle，assignment 会把多次追加折叠成一次观察，batch 只是 UI 分组）。

### 08 常驻记忆：会话内冻结 + 常驻/按需分层

**太墟现状（落地前）**：`BotMemoryManager.assembleContext()` 按 `persona.md 1,600 / preferences.md 1,200 / dialogue_notes.md 1,200` 加 `knowledge/*.md` 组装；`BotMemoryPlugin` 的 `memory-context` 每次请求重读，`BotOrchestrator.dispatchSubtask()` 还会把整份再拼进派发的用户消息前缀。

**架构约束**：太墟已有明确决策——逐轮变化的内容不进 system prompt（`ApiContextAssembler` 注释：记忆召回与计划看板都逐轮变化，放进 system 会让整段前缀每轮失效）。因此**不能照抄“进 system 段”**；真正可对齐的是**冻结**与**常驻/按需分层**。

**Tobs 做法**（[soul-and-core-memory.zh.md](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/soul-and-core-memory.zh.md)、[memory/soul.ts](https://github.com/BotHarness/DeepSeekBot/blob/main/packages/core/src/memory/soul.ts)）：

- `SOUL.md`（是谁，很少改，默认 5,000 字符）与 `MEMORY.md`（**索引**，默认 3,000 字符）在新 Session 开始时进 system prompt，其余记忆靠工具按需读。
- 两个文件在**一个 Session 内冻结**，保证前缀每轮一致以命中 prompt cache；改动在“下一个 Session”或“下一次压缩”后才生效。
- 超限**只截断注入、不改文件**，并告诉模型“你的文件超长了，需要精简”。
- 明确不设 `USER.md`：Bot 在多个群和多个项目里对多个人，人和其他事实用同一套文件记。

**本轮落地（P1，已完成）**：

- 新增 `BotResidentMemory`（[BotResidentMemory.kt](../harness/src/main/java/top/wkbin/taixu/harness/bot/memory/BotResidentMemory.kt)）：按 `(bot, session)` 缓存，首个请求读取一次后**冻结**；用户改人格调用 `invalidateBot`，解散群聊时按会话 `invalidateSession`，条目数另有 128 上限兜底；模型自己的 `memory-write` **不**触发刷新，避免会话内自我漂移。
- 三档常驻（persona 1,600 / preferences 1,200 / dialogue_notes 1,200，总长上限 4,400），超限只截断注入并提示，**不回写文件**。
- `knowledge/` 不再逐轮投递，改为一行 `memory-read` 指引导引，细节按需读取。
- 接线：`BotMemoryPlugin` 的 `memory-context` 与 `BotOrchestrator.dispatchSubtask()` 均改用 `render()`；冻结只覆盖本进程，进程重启或会话重建后重读。
- 偏离说明：未迁到 system 段，理由见“架构约束”；若将来要进 system，必须先解决动态前缀，否则重蹈被否掉的旧方案。
- 验证：`BotResidentMemoryTest` 覆盖冻结、会话隔离、按需、截断与空记忆；`architectureCheck` 通过。


### 09 定时任务：一次触发是一条准入，而不是一次执行

**太墟现状（落地前）**：到期 `stage` 一条 pending 投递并推进 `nextRunAt`，`BotScheduleRunner` 逐条 `claim → submit`；投递表已有 `(scheduleId, scheduledAt)` 唯一索引与 claiming / recovery 语义，但**慢轮次期间每一次发生都会新增一条 pending**，恢复时可能一次处理多条积压。

**Tobs 做法**（[ADR-0133](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0133-bot-schedules-wake-the-orchestrator-through-the-bot-inbox.md)、[schedules/bot-schedules.ts](https://github.com/BotHarness/DeepSeekBot/blob/main/packages/core/src/schedules/bot-schedules.ts)）：

- 触发只产生一条 `schedule` Source Event 并入 Inbox，**不直接跑工作**；是否回复/派活由 Orchestrator 决定，与私聊、@ 走同一套路径。
- 合并：一个 schedule 最多一条未观察触发；新目标刷新旧 Attention Unit，不叠加。
- 重启后**只补最近一次错过的发生**；归档 Bot 暂停、恢复不补历史。
- “立即运行”标记 `manual`，不改变下一次计划目标；锁定（locked）后 Bot 只能读。
- 上限 20 条启用、最小间隔 1 分钟、低于 15 分钟提示成本。

**本轮落地（P2，已完成）**：

- `BotScheduleDeliveryDao.stage` 增加合并：先查该排期是否已有 `pending`；有则 `supersedePending` 刷新 `scheduledAt / createdAt / instruction`，无才插入。上限因此是**执行中一条 + 待执行一条**，不再随慢轮次堆叠。
- 旧版本可能已经积压多条 `pending`；`stage` 会顺手 `dropOtherPending` 收掉多余行（它们从未执行，删除无副作用），新装/升级后的第一条触发即可自愈。
- 重启只补最近一次：`stage` 以 `schedule.nextRunAt`（而非当前时刻）作为 `scheduledAt`，且每次只 stage 当前这一拍，错过的历史拍不会逐条补发；本轮补用例锁住该语义。
- 未接：`manual`（太墟没有“立即运行”入口）、`locked` 只读、Inbox 准入化——分别依赖 UI 与 Inbox 重构，仍列为候选。
- 验证：`BotScheduleRunnerTest` 新增“合并不堆叠”“合并后只受理一次”“重启只补最近一次”“旧积压收敛为一条”四个用例；`architectureCheck` 通过。


### 10 审批：确定性优先，Reviewer 只有两种输出

**太墟现状**：`ApprovalMode` + 会话授权 + 全局策略 + 工具注解风险；`TaskBudgetGuard`、`BotCapabilityExpiryPanel` 已有能力到期。

**Tobs 做法**（[bot-approval-policy.md](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/architecture/bot-approval-policy.md)、[ADR-0342](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0342-bot-approval-selects-one-template-and-keeps-deterministic-authorization-separate.md)）：

- 先用**确定性代码**判断是否需要审批并解析已有授权；命中的请求**不产生模型调用**。未命中才走 Reviewer 模板。
- 模板三段：`Always ask` / `Always allow` / 自由文本；冲突时 ask 优先。
- Reviewer 只有 **allow / ask-human** 两种输出——**取消了“模型自动拒绝”**，不确定就交给人。
- 每次 Review 捕获模板的 identity/version/content；模板缺失、模型超时、回复畸形一律路由到人。
- 删除被引用模板时一次二次确认，并**显式披露回退结果**（选它的 Bot 回退到继承全局）。

**可抄的**：取消模型自动拒绝、确定性规则先于模型判断、模板版本快照、删除回退披露。这四条同时降低“误拒导致任务停摆”和“改了模板影响正在进行中的审批”的风险。

**核对结论（本轮）**：太墟**没有模型审批官**。`harness/approval` 只有确定性策略（`TrustedToolRiskPolicy`、`McpPolicyRisk`、`AnnotationEscalation`、`SessionApprovalGrants`）和**事后**审计 `ApprovalAuditRecorder`；审批决策由 `ApprovalMode` + 会话授权直接给出，不经过模型。因此“Reviewer 两态”不是对齐现成能力，而是**新增一层模型审批官**（含成本、超时、误拒与 UI 入口），P1 暂缓、单独立项更稳妥；本轮只把这条差异记清楚，不做半成品。

### 11 身份便携：颜色与横幅是身份数据，不是主题状态

**太墟现状**：`Bot` 已带 `colorHex`、`avatarSeed`，`BotManifest` 也带这两个字段。

**Tobs 做法**（[ADR-0177](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0177-bot-primary-color-is-portable-identity-not-theme-state.md)、[primary-color.ts](https://github.com/BotHarness/DeepSeekBot/blob/main/packages/core/src/bots/primary-color.ts)、[share-bot.zh.md](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/share-bot.zh.md)）：

- 自动模式跟随头像主发色；自定义模式在改名、换头像后仍保留；无发色/上传图片时用 **Bot ID 派生兜底**，不取图像像素。
- 分享/导入携带“模式 + 自定义源色 + 稳定兜底色”，**主题派生色不进入身份数据**。
- 横幅是 `{ scene, seed }` **配方**而不是图片，十种场景可复现；也可上传 3:1 图片。
- `bot.json` 支持 `tags`、`bio`、`i18n`（中英名/简介）、`avatar`（图片或像素配方）、`banner`（配方），单字段超限只跳过该字段。

**可抄的**：颜色兜底策略与导入导出携带规则；横幅用配方存储（体积小、可分享）；清单补 `tags/bio/i18n/banner`。

**本轮落地（部分，已完成）**：导出确认弹窗加「包含提交历史」开关（默认开）。关闭时 `writeMemoryToZip` 按路径段过滤，整段 `.git` 不进包；开启时仍跳过 `index.lock`。`BotManifest` 的 `tags/bio/i18n/banner` **暂不加**：`Bot` 模型与 `persona_bots` 表没有对应列，离线精选市场（`MarketBotItem`）也不消费，加了只能导出空值、导入后无处存放，再导出即丢。要真正支持，需先定元数据存储（新增列 + 迁移，或记忆库内 `profile.json`），再让市场读取。

## 三、新增候选

| 编号 | 候选 | Tobs 依据 | 抄什么 | 不抄什么 |
| --- | --- | --- | --- | --- |
| 12 | **IM 渠道 Provider seam** | [dsh-im.ts](https://github.com/BotHarness/DeepSeekBot/blob/main/packages/core/src/messaging/dsh-im.ts)、[lark-connection.zh.md](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/lark-connection.zh.md)、[ADR-0038](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0038-provider-capabilities-and-service-grants-gate-external-actions.md) | 一渠道一 Provider 包、能力声明、可信 provenance、回复走原来源线程（thread-first）、幂等 outbox + 对账、配对/授权复核 | **个人微信扫码链路**（个人号协议，封号与合规风险）；优先级给飞书、QQ 官方机器人、Slack、Discord |
| 13 | **GitHub topic Bot 目录** | [ADR-0131](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0131-bot-marketplace-starts-as-a-github-indexed-catalog.md)、[market/src/catalog.ts](https://github.com/BotHarness/DeepSeekBot/blob/main/packages/market/src/catalog.ts) | “topic 即作者授权”、仓库 node id 作为稳定键、README 即安装后记忆、**无账号阶段不宣称下载量** | 托管上传市场与 Cloudflare Worker/D1 服务端；太墟已有签名目录（`REMOTE_PLUGIN_LIBRARY`），信任根应复用签名而不是 GitHub 话题 |
| 14 | **多 Bot 循环防护硬约束** | [bot-runtime-architecture.md](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/architecture/bot-runtime-architecture.md) 的“防止循环” | causation/correlation/root 消息 ID、hop count、每群速率、冷却、预算——由代码强制，提示词只做补充 | 无（成本低，建议纳入） |
| 15 | **ADR 决策账本 + GLOSSARY** | [docs/adr](https://github.com/BotHarness/DeepSeekBot/tree/main/docs/adr)（330 篇）、`GLOSSARY.md` | 一决策一文件、`Status/Date/Context/Options/Consequences`、`superseded by` 链、术语只在一处定义 | 不要照抄别人的决策内容，只抄模板与纪律 |
| 16 | **GenUI 富文档隔离执行**（P3） | [ADR-0301](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0301-genui-rich-documents-use-isolated-html.md)、[ADR-0302](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0302-genui-diagrams-compile-before-publication.md) | 生成→预览诊断→显式发布→隔离渲染→本地探索→显式续聊；不可信 HTML/CSS/JS 的执行边界与字节级预算 | QuickJS + Worker + iframe 的整套实现；Android 侧若要做得另立 ADR |
| 17 | **语音消息 Provider seam**（P3） | [ADR-0280](https://github.com/BotHarness/DeepSeekBot/blob/main/docs/adr/0280-tts-providers-and-bot-voice-messages.md)、`cli-voice.ts` | TTS Provider 抽象、语音消息作为一等消息类型、时长/格式边界 | 通话、WebRTC、桌宠（`Window Companion`） |

## 四、明确不抄

| 项 | 原因 |
| --- | --- |
| Expo / React Native 客户端与 DSH 插件壳 | 技术栈完全不同（TS/Expo vs Kotlin/Compose），无可移植代码 |
| 托管账号（Better Auth）与 Cloudflare 市场服务端 | 太墟是离线优先、无账号的端侧产品；引入即改变产品定位与合规面 |
| 个人微信扫码接入 | 个人号协议有封号与合规风险；只在企业微信/公众号/官方机器人范围内讨论 |
| Team mode（一 Host 多成员）与 RTC 通话 | 与“掌中单机”定位冲突，且依赖服务端与网络命名空间隔离 |
| PostHog 匿名遥测与 campaign 短链 | 与太墟本地优先、最小外发的口径不符 |

> 授权：Tobs 为 MIT（Copyright (c) 2026 BotHarness）。若照搬清单字段、文档段落或算法，请在 `THIRD_PARTY_LICENSES.md` 登记来源与许可证。按当前栈差异，**代码级直接复制基本不可行**，能抄的是契约、清单格式、状态机与验收口径。

## 五、建议顺序

| 顺序 | 内容 | 理由 | 验收重点 |
| --- | --- | --- | --- |
| P1 | 记忆冻结 + 常驻/按需分层（第 08 项） | 单点改动、会话内注入稳定、减少逐轮重读 | ✅ 已完成：`BotResidentMemory` + 接线 + 单测 |
| P1 | 审批 Reviewer 收成 allow/ask-human（第 10 项） | 防误拒停摆 | ⏸ 暂缓：无现成 Reviewer，属新增模型审批层 |
| P1 | ADR 目录与模板，先回填既有决策（第 15 项） | 纯文档，零风险 | ✅ 已完成：`docs/adr` 7 篇 + README 索引 + `docs/GLOSSARY.md`，已登记进 `docs/AI_NAVIGATION.md` |
| P2 | 定时触发准入化 + 合并 + 重启只补最近一次（第 09 项） | 直接消除“重启补发一串” | ✅ 已完成合并与重启语义；Inbox 准入化仍为 P3 |
| P2 | `bot.json` 扩展 tags/bio/i18n/banner，导出加选择与历史开关（第 05/11 项） | 分享与市场的前置 | ✅ 历史开关已完成（默认携带）；tags/bio/i18n/banner 待元数据存储定案，未加空字段 |
| P3 | Inbox/Attention 投影表（第 07 项） | 需要数据库演进，先投影后收权 | ✅ 投影表 + 定时/群聊/派工生产者已完成 |
| P3 | 飞书 Provider 试点（第 12 项） | 单渠道验证 seam 是否成立 | 回复回原线程、幂等键、附件按来源授权读取 |

## 六、可直接照做的改动

1. **记忆冻结（已完成）**：新增 `BotResidentMemory`，`BotMemoryPlugin` 的 `memory-context` 与 `BotOrchestrator.dispatchSubtask()` 均改为按 `(bot, session)` 取一次快照；会话内不重读，用户改人格显式失效。未迁 system 段，理由见第 08 项“架构约束”。
2. **定时合并（已完成）**：`BotScheduleDeliveryDao.stage` 在插入前查同排期的 `pending`，有则刷新 `scheduledAt/createdAt/instruction`；不需要新增 `coalesceKey` 列——`scheduleId` 本身就是合并键，`status` 已能区分“执行中”和“未观察”。
3. **清单扩展（暂缓，理由已记）**：`tags/bio/i18n/banner` 需要先解决“存哪”（`Bot` 表新增列 + 迁移，或记忆库 `profile.json`）与“谁消费”（离线市场不读），否则是导出恒空、导入即丢的死字段；定案后再加，`version` 是否升 2 同步决定（`importBot` 现硬校验 `version == 1`）。
4. **导出安全（已完成）**：`BotPackageService.exportBot(..., includeHistory)`；UI 导出前弹确认，开关默认携带历史；`includeHistory=false` 时整段 `.git` 排除，避免已删除内容的旧提交随包分享。未做逐文件勾选——当前 UI 没有文件选择入口，开关先覆盖最大的泄漏面，逐文件选择留到“选择性分享”立项时再加。
5. **审批两态（暂缓）**：先把“模型审批官”作为独立能力立项（模板、超时、成本、误拒入口），再谈两态收口；当前确定性策略已足够，不应为对齐而引入模型判断。

## 七、范围与验证记录

本次结论以所列两个 Git 快照为准，逐项链接已校验到本地对应文件。Tobs 侧全部为公开源码与文档的静态阅读；太墟侧以 `harness/bot`、`feature/bot` 的类与调用入口为依据。

本轮已跑通的验证：`.\gradlew.bat architectureCheck`（40 个模块，依赖白名单/无环/import 黑名单/尺寸棘轮全部通过）；`.\gradlew.bat :harness:testDebugUnitTest --tests "top.wkbin.taixu.harness.bot.memory.*"`（16 个用例通过，其中 `BotResidentMemoryTest` 5 个：冻结、会话隔离、按需、截断、空记忆）；`--tests "top.wkbin.taixu.harness.bot.schedule.*"`（23 个用例通过，`BotScheduleRunnerTest` 10 个含 4 个新增：合并不堆叠、合并后只受理一次、重启只补最近一次、旧积压收敛为一条）；`--tests "top.wkbin.taixu.harness.bot.archive.*"`（`BotPackageZipTest` 2 个：不含历史时 `.git` 全排除、含历史时保留且跳过 `index.lock`）；`:feature:bot:compileDebugKotlin` 通过；`:core:database:testDebugUnitTest --tests "top.wkbin.taixu.core.database.BotPersistenceTest"` 通过。

文档侧新增 `docs/adr/`（README 索引 + 0001–0007 七篇已接受决策）与 `docs/GLOSSARY.md`，并在 `docs/AI_NAVIGATION.md` 挂了两行入口；纯文档，无测试。

Inbox 投影：`BotInboxProjectionTest` 5 例（幂等准入、重复不重置、observe 时间、defer/ignore、失败不阻断）、`BotScheduleRunnerTest` 12 例（新增 accepted→observed、rejected→ignored）、`GroupDeliveryPumpTest` 10 例（新增群聊 observed/ignored/deferred）、`BotAssignmentRuntimeTest` 17 例（新增派工 observed/ignored）、`BotInboxMigrationTest` 1 例（91→92 建表与两个索引）；`:app:compileDebugKotlin` 与 `architectureCheck` 通过。

**仍未验证**：冻结后的真实模型行为（模型是否会主动 `memory-read` 读知识）、合并与慢轮次并发下的真实时序、导出排除历史后的可恢复性、审批超时路由；这些都需要真机或带模型的联调，不以单测替代。Tobs 的 GenUI、通话、Team mode 均为“设计已接受但交付状态另记”的状态，不能按文档宣称已实现。
