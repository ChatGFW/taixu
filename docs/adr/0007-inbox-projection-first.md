# ADR-0007 Inbox 先做投影表，不收权

- 状态：已接受
- 日期：2026-11-17
- 决策域：唤醒 / 注意力 / 投递事实
- 关联：`docs/BOT_COLLABORATION.md`、`docs/TOBS_COMPARISON.md` 第 07 项
- 取代：无
- 被取代：无

## 背景

太墟的投递状态分散在 `BotAssignmentTracker`、`AssignmentRequestPump`、`group_deliveries`、`bot_schedule_deliveries` 与 `BotScheduleRunner` 各自的列里；`SessionAttentionBanner` 只是界面投影。系统无法统一回答：这条来源对哪个 Bot 可见、为什么可见、是否已被看见。

Tobs 的答案是四层事实：Source Event（不可变来源）→ Inbox Admission（准入理由）→ Attention Decision（observed/deferred/ignored/handled）→ Observation（真正进入某一轮）。Wake Policy 必须跑在 Orchestrator 之外；「入队、通知、UI 看过」都不算 Bot 看到。

## 选项

1. **直接把 Inbox 收成唯一 authority，改写既有投递表**：本轮拒绝。牵动群聊、派工、定时三条链路与迁移链，回归面过大，且旧语义尚未验证。
2. **维持现状，只加查询接口**：拒绝。查询仍是各表拼接，回答不了「为什么可见 / 是否看过」。
3. **新增派生投影表，authority 不动**：采纳。先积累语义，再谈收权。

## 决策

新增 `bot_inbox_items`（`BotInboxItemEntity` / `BotInboxDao`）：

- 字段：`id / botId / sourceKind / sourceId / sessionId / reason / priority / state / observedAt / createdAt / updatedAt`。
- 唯一索引 `(botId, sourceKind, sourceId)`：同一条来源对同一个 Bot 只能准入一次；`INSERT OR IGNORE` 消化重复，且不会把已决策项重置回 pending。
- 状态：`pending / observed / deferred / ignored / handled`；`pending` 是「尚未决策」的推导值。
- `observedAt` 只在内容真正进入某一轮时写入；入队、通知、UI 浏览一律不算。
- 投影写失败只记日志、不抛出（`BotInboxProjection` 内部 `runCatching`），绝不阻断原始投递。
- 生产者：`BotScheduleRunner`、`GroupDeliveryPump` 与派工 `BotAssignmentRuntime.execute`。定时：claim 后准入，`submit` 接受记 observed，拒绝记 ignored，中断/未知记 deferred。群聊：claim 后准入，`GroupTurnOutcome.observed`（由 `GroupBotResponder` 在 `submit` 受理时置位）记 observed，`uncertain` 记 deferred，其余失败记 ignored；中断/异常也记 deferred。派工：claim 成功后准入；`executor.execute` 的 admitted 回调里置 observed；结果不确定或执行异常记 deferred，提交前已失败记 ignored。
- 派工的 `sourceId` 用单条 `request.id`，不用 `assignmentId` 或批次 id：一条 request 就是一次可独立 claim/admit/settle 的追加指令，而 assignment 会把多次追加要求折叠成一次观察；`BotWorkBatch` 只是 UI 分组，不是可见性单元。

## 影响

- 正面：定时触发第一次有统一的「为什么可见、是否看过」事实；重复触发不会重复准入；投影坏了不影响投递。
- 代价：投影可能滞后或缺失（写失败被吞）；目前没有读取 UI。
- 回滚：删除表与接线即可，`bot_schedule_deliveries` 不受影响；迁移 91→92 可留在链上（空表无副作用）。

## 验收

`BotInboxProjectionTest` 5 例（幂等准入、重复不重置、observe 时间、defer/ignore、失败不阻断）；`BotScheduleRunnerTest` 12 例（新增 accepted→observed、rejected→ignored）；`GroupDeliveryPumpTest` 10 例（新增 observed/ignored/deferred 三例）；`BotAssignmentRuntimeTest` 17 例（新增派工 observed/ignored 两例）；`BotInboxMigrationTest` 1 例（91→92 建表与两个索引）；`architectureCheck` 通过。
