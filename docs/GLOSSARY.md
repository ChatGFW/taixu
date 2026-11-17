# 术语表（GLOSSARY）

> 术语只在这里定义一次；其它文档首次出现时链接回本表。代码标识符与中文名并列，避免同物异名。

## Bot 与记忆

| 术语 | 代码 / 位置 | 定义 |
| --- | --- | --- |
| Bot / PersonaBot | `core/model/bot/Bot.kt`、`persona_bots` 表 | 带人格、专属会话与独立记忆仓库的智能体；不是一次性会话。 |
| Bot 会话 | `Bot.sessionId` | 每个 Bot 独占的会话；群聊中每个 Bot 仍各有自己的 Lane。 |
| 记忆仓库 | `BotMemoryManager`、`memory/` 目录 | Bot 的 Git 仓库，存 `persona.md`、`preferences.md`、`dialogue_notes.md`、`knowledge/`；可回滚。 |
| 常驻记忆 | `BotResidentMemory` | 每次组装上下文都注入的三档内容：persona / preferences / dialogue_notes。 |
| 按需记忆 | `knowledge/` + `memory-read` | 不逐轮投递，模型需要时通过工具读取的知识文件。 |
| 记忆冻结 | `BotResidentMemory.render()` | 同一 `(botId, sessionId)` 首次读取后固定快照，会话内不再重读。 |
| 记忆失效 | `invalidateBot` / `invalidateSession` | 人格变更、Bot 删除、群会话删除时主动丢弃快照。 |
| 回滚 | `rollbackToCommit` | 把记忆仓库恢复到指定提交，并在恢复前留快照。 |

## 定时与投递

| 术语 | 代码 / 位置 | 定义 |
| --- | --- | --- |
| 排期 | `BotSchedule`、`bot_schedules` | Bot 自主登记的 Cron 规则，`nextRunAt` 由解析结果维护。 |
| 触发 | `BotScheduleRunner` | 到达 `nextRunAt` 后产生一次投递。 |
| 投递 | `BotScheduleDelivery`、`bot_schedule_deliveries` | 一次触发的持久记录，状态为 PENDING/ADMITTING/ADMITTED/REJECTED/UNKNOWN。 |
| 合并 | `stage()` 的 supersede 分支 | 同一排期最多保留「执行中一条 + 待执行一条」；后来的触发刷新前者，不新增行。 |
| 准入 | `BotInboxProjection.admit()` | 判断一次来源是否对某个 Bot 可见，并写下理由；同源同 Bot 幂等。 |
| Inbox 投影 | `bot_inbox_items`、`BotInboxItem` | 派生事实表：记录准入理由与 Attention Decision（observed/deferred/ignored/handled），不取代原投递表。 |
| 观察（Observation） | `BotInboxProjection.observe()` | 内容真正进入某一轮时才成立；入队、通知、UI 看过都不算。 |
| 准入 | `Admission`（规划中） | 判断一次触发是否值得进入模型轮次；定时合并是它的最小实现。 |

## 归档与清单

| 术语 | 代码 / 位置 | 定义 |
| --- | --- | --- |
| 导出包 | `BotPackageService`、`bot-exports/*.zip` | `bot.json` + `memory/` 的 ZIP；默认含 `.git`，可关闭。 |
| 清单 | `bot.json`、`BotManifest` | 导出包的元数据；当前 `version = 1`，新增字段需先定存储与消费方。 |
| 历史开关 | `includeHistory` | 导出时是否打包 `.git`；关闭后导入方无法回滚。 |

## 审批与安全

| 术语 | 代码 / 位置 | 定义 |
| --- | --- | --- |
| 确定性审批策略 | `harness/approval/*Policy` | 按工具、参数、来源、会话授权做判断，规则可审计。 |
| 会话授权 | `SessionApprovalGrants` | 某一会话内对某类操作的临时放行，结束即失效。 |
| 审批官 | Reviewer（暂无实现） | 由模型给出 allow / ask-human 的两态判断；属新增能力，见 ADR-0004。 |
| 审计记录 | `ApprovalAuditRecorder` | 事后记录谁在何时批准/拒绝了什么；不参与实时放行。 |
| provenance | 工具结果 / 渠道消息 | 内容来源链；可信来源与外部输入必须分开处理。 |

## 团队与运行时

| 术语 | 代码 / 位置 | 定义 |
| --- | --- | --- |
| Lane | `BotOrchestrator` | 单个 Bot 的执行通道；子 Lane 有自己的 system prompt 与工具集。 |
| 三省六部 | `GROUP_TEAM_GOVERNANCE.md` | 群聊团队的治理隐喻：分工、审议、驳回与主控裁决。 |
| claimNonce | 群聊抢答 | 认领某条消息的租约令牌；抢到后进入「待核对」，不是最终回复。 |
| 待核对 | 群聊回复状态 | 认领方回复前的复核态，防止多个 Bot 同时答同一问。 |
| 派工请求 | `BotAssignmentRequest` | 一条追加指令；可独立 claim/admit/settle，也是 Inbox 投影 `sourceId` 的粒度。 |
| 工作批次 | `BotWorkBatch` | 多个 assignment 的 UI 分组，不是可见性单元，不用于投影 `sourceId`。 |
| PRoot | `runtime` | Android 上的无 root 沙箱执行后端；不用 chroot/namespace，见 `ADR_SANDBOX_BACKEND.md`。 |
| 架构门禁 | `architectureCheck` | 依赖白名单、无环、import 黑名单、单文件行数棘轮；挂在 `app:preBuild`。 |
| 棘轮 | `.architecture-baseline.json` | 存量超限文件只许下调不许上涨；新文件一律 ≤ 400 行。 |
| 让步 | `CONCESSIONS_LEDGER.md` | 为宿主平台或既有架构登记的临时妥协，必须写偿还路径。 |
