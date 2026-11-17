# ADR-0002 定时触发合并为「执行一条 + 待执行一条」

- 状态：已接受
- 日期：2026-11-17
- 决策域：Bot 定时任务 / 投递状态机
- 关联：`docs/BOT_COLLABORATION.md`、`docs/TOBS_COMPARISON.md` 第 09 项
- 取代：无
- 被取代：无

## 背景

`BotScheduleDeliveryDao.stage()` 原先每次触发都插入一条新的 `pending`。当一轮执行变慢、Cron 又连续到点时，`pending` 会线性堆叠；重启后 `recoverInterrupted()` 只把 `admitting` 改成 `unknown`、不重放，但积压的 `pending` 仍会被逐条执行，形成「一次重启补发一串旧触发」。

太墟没有「立即运行」入口，触发源只有 Cron，因此合并键天然存在：`scheduleId`。

## 选项

1. **新增 `coalesceKey` 列 + 索引**：被拒。`scheduleId` 已经是同一个排期，无需再引入一个同值列。
2. **新增 `SUPERSEDED` 状态**：被拒。`BotScheduleDelivery` 模型只有 PENDING/ADMITTING/ADMITTED/REJECTED/UNKNOWN，新增状态会迫使 UI 处理未知值。
3. **stage 时 SUPERSEDE 现有 `pending`，并删除旧积压**：采纳。

## 决策

`stage(id, scheduledAt, now, next)`：

- 有同排期 `pending` → `dropOtherPending(scheduleId, keepId)` 收掉旧的重复积压（它们从未执行，删除无副作用），再 `supersedePending(id, scheduledAt, now, instruction)` 刷新时间与指令。
- 无 `pending` → 才 `insert`。
- 投递落库与 `advance(id, now, next)` 仍在同一 `@Transaction`。

语义上限固定为「执行中一条 + 待执行一条」；`scheduledAt` 取 `schedule.nextRunAt`，重启只补最近一拍。旧版本已积压的多条 `pending` 会在第一次 `stage` 时自愈收敛。

## 影响

- 正面：慢轮次不再累积雪崩；重启补发量从「全部错过的拍」降为「最近一拍」。
- 代价：中间错过的触发不会逐条补发，只保留最终状态；对「每次都要执行」的排期属于语义变化，需要在排期说明里写清。
- 回滚：恢复原 `insert` 分支即可；已收敛的旧行无法还原，但没有执行价值。

## 验收

`BotScheduleRunnerTest` 10 例，其中新增 4 例：合并不堆叠、合并后只受理一次、重启只补最近一次、旧积压收敛为一条。`BotPersistenceTest` 与 `architectureCheck` 通过。
