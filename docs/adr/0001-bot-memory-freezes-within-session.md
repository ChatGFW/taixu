# ADR-0001 Bot 记忆在会话内冻结

- 状态：已接受
- 日期：2026-11-17
- 决策域：Bot 记忆 / 上下文装配
- 关联：`docs/BOT_COLLABORATION.md`、`docs/TOBS_COMPARISON.md` 第 08 项
- 取代：无
- 被取代：无

## 背景

太墟的 Bot 记忆原先有三条注入路径：`BotMemoryPlugin` 每次请求重新 `assembleContext()`；`BotOrchestrator.dispatchSubtask()` 把整份记忆拼进派发消息前缀；子 Lane 走自建的 `laneSystemPrompt()`。三条路径各自重读，同一会话内多轮之间记忆会漂移，且长会话反复付出读取与拼接成本。

`ApiContextAssembler` 已有一条明确决策：记忆召回、计划看板这类逐轮变化的内容**不进 system prompt**，否则整段前缀每轮失效。因此不能把记忆迁到 system 段来「顺便」冻结。

## 选项

1. **迁到 system prompt 并冻结**：被拒。违反 `ApiContextAssembler` 既有决策，会让每轮前缀失效，缓存命中率下降。
2. **继续逐轮重读，只加缓存**：被拒。缓存键难界定，用户改人格后旧值可能长期滞留。
3. **在插件上下文内按 `(botId, sessionId)` 冻结快照，显式失效**：采纳。

## 决策

新增 `BotResidentMemory`：

- 首次 `render(botId, sessionId)` 读取并缓存 `Snapshot(text, capturedAt)`，会话内后续轮次直接返回同一份文本。
- `invalidateBot(botId)` 前缀匹配、`invalidateSession(sessionId)` 后缀匹配；`updateBot` 改 `systemPrompt` 写 persona 后失效，`deleteBot` 与 `deleteGroupSessions` 清理。
- 常驻三档：persona 1,600 / preferences 1,200 / dialogue_notes 1,200，总长上限 4,400；超限只截断注入文本并附提示，不回写文件。
- `knowledge/` 不再逐轮投递，改为一行「按需记忆…调用 memory-read」指引。
- 条目上限 128，超限按 `capturedAt` 淘汰最旧。

接线：`BotMemoryPlugin` 第三参、`BotOrchestrator` 构造参、`BotHarnessModule` 单例注册；`BotMemoryManager.assembleContext()` 保留，KDoc 注明「由 BotResidentMemory 取代，仅供诊断/回填」。

## 影响

- 正面：同一会话内注入稳定；记忆读取从每轮一次降为每会话一次；知识库不再挤占常驻预算。
- 代价：同一会话内新写入的记忆不会自动出现在下一轮，需等新会话或显式失效；这是有意为之的「快照语义」。
- 回滚：删掉 `BotResidentMemory` 接线、恢复 `assembleContext()` 调用即可；无数据库迁移。

## 验收

`BotResidentMemoryTest` 5 例：冻结、会话隔离、knowledge 按需、超限截断不动原文、空记忆。`architectureCheck` 通过。
