# ADR-0005 `bot.json` v1 字段冻结，扩展先定存储

- 状态：已接受
- 日期：2026-11-17
- 决策域：Bot 清单 / 导入导出
- 关联：`docs/TOBS_COMPARISON.md` 第 11 项
- 取代：无
- 被取代：无

## 背景

Tobs 的 `bot.json` 支持 `tags`、`bio`、`i18n`（中英名/简介）、`avatar`（图片或像素配方）、`banner`（`{scene, seed}` 配方），单字段超限只跳过该字段。

太墟的 `BotManifest` 目前是：`version / name / role / systemPrompt / modelId / avatarSeed / colorHex / modelVariant / allowModelSelection`。`Bot` 模型与 `persona_bots` 表没有 `tags/bio/i18n/banner` 对应列；离线精选市场 `MarketBotItem` 也不消费这些字段。

## 选项

1. **照抄字段进清单**：被拒。`Bot` 没有存储列，导出只能恒为空；导入后无处存放，再导出即丢，是死字段。
2. **升 v2 并同时加列迁移**：本轮被拒。需要数据库迁移与市场改造，属于独立改动，不应夹在导出安全修复里。
3. **v1 冻结，扩展前先定「存哪 + 谁消费」**：采纳。

## 决策

- `BotManifest.version` 保持 1；不新增无法持久化的字段。
- 若要支持 `tags/bio/i18n/banner`，先解决两件事：
  - 存储：`persona_bots` 新增列 + 迁移，或记忆仓库内 `profile.json`（需在 `BotMemoryManager` 开放受控的 JSON 读写）。
  - 消费：市场列表/详情真正读取并展示，否则不导出。
- 若新增的是**可选、可丢弃**字段且保持向后兼容，可以留在 v1；一旦改变语义（必填、影响导入行为），必须升 2，并同步放开 `importBot` 的 `version == 1` 硬校验。

## 影响

- 正面：清单不膨胀，不产生「导出有、导入丢」的假能力。
- 代价：与 Tobs 包互操作时，这些字段目前会被忽略；导入不会失败，但信息不保留。
- 回滚：无代码改动。

## 验收

未来加字段时补两例：带新字段的包导入再导出字段不丢；`version` 不匹配的包被明确拒绝且不留下半个 Bot。
