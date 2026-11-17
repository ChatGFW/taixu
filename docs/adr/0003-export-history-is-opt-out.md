# ADR-0003 导出包默认携带记忆历史，可显式排除

- 状态：已接受
- 日期：2026-11-17
- 决策域：Bot 归档 / 分享安全
- 关联：`docs/BOT_COLLABORATION.md`、`docs/TOBS_COMPARISON.md` 第 05/11 项
- 取代：无
- 被取代：无

## 背景

`BotPackageService.exportBot()` 原先无条件复制整个记忆目录，包含 `.git`。这带来两个后果：

1. 导入方可回滚到导出前的任意版本（功能）。
2. 已删除内容的旧提交也会随包分享出去（泄漏面）。

分享场景通常只需要「当前形态」，不需要对方的完整历史。

## 选项

1. **保持无条件携带**：被拒。分享即泄漏，用户没有拒绝的机会。
2. **默认排除历史，需要时手动加**：被拒。会让「导入后回滚」这个既有能力默认失效，属于静默降级。
3. **默认携带，导出前弹确认并提供开关**：采纳。

## 决策

- `exportBot(botId, destination, includeHistory: Boolean = true)`。
- `includeHistory = false` 时，`writeMemoryToZip` 按路径段过滤，`memory/.git` 整段不进包；`true` 时保留 `.git`，仍跳过 `index.lock`。
- UI 导出前弹 `RuntimeAlertDialog`：说明「不含历史无法回滚；含历史会连已删除内容的旧提交一起打包」，开关默认开启，确认后才落盘。
- 未做逐文件勾选：当前 UI 没有文件选择入口；开关先覆盖最大的泄漏面。

## 影响

- 正面：用户可在分享前明确选择，默认行为不变。
- 代价：开关默认开，粗心分享仍可能带历史；后续若做「选择性分享」，应按「取消勾选任一文件即不带 `.git`」的规则扩展到文件级。
- 回滚：去掉参数与弹窗即可；已导出的包不受影响。

## 验收

`BotPackageZipTest` 2 例：不含历史时 `.git` 全排除；含历史时保留且跳过 `index.lock`。`:feature:bot:compileDebugKotlin` 与 `architectureCheck` 通过。
