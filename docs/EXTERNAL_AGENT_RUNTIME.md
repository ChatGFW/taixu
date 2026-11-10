# 外部 Agent 运行时协议

让电脑或服务器上的 Claude Code、Codex 等执行器，以某个非主控 Bot 身份加入太墟群聊协作。手机内 Harness 仍负责本地成员；外部执行器通过运行时令牌领取群收件、提交回执并发言。

## 边界

| 项目 | 约定 |
| --- | --- |
| 身份 | 绑定已有 `persona_bots` 成员，不另建账号体系 |
| 主控 | 主控 Bot 禁止切换为外部运行时 |
| 模式 | `Bot.runtimeMode=EXTERNAL` 时，本地 `GroupDeliveryPump` 不再领取该成员收件 |
| 令牌 | 明文只在签发时返回一次，库内仅存 SHA-256；可撤销、可设绝对过期 |
| 领取 | 复用 `group_deliveries` 与 `claimNonce`；错配回执忽略 |
| 租约 | `claimedAt` + 默认 15 分钟 TTL；无心跳的 EXTERNAL `submitting` 会被回收回 pending；长任务需 `heartbeat` |
| 合并 | 同一群内连续未领取收件最多合并 8 条进一轮；执行中新到的消息仍排队到下一轮 |
| 发言 | 仅允许向该成员所在群发 `BOT` 消息 |
| HTTP | 独立 LAN 口 `0.0.0.0:8901`，**只认** `Authorization: Bearer tx_rt_…`，不接受 Web 协作台 PIN |
| 不包含 | 工具代理、模型调用、审批代签、HostBridge；外部副作用仍由执行器本机负责 |

## 服务入口

`ExternalAgentRuntimeService`（harness）：

1. `issueToken(botId, label, expiresAt?)` → 切换 EXTERNAL 并返回明文令牌  
2. `authorize(rawToken)` → `ExternalAgentPrincipal`  
3. `describeSession(principal)` → botId / label / pendingCount / 所在群摘要  
4. `peekPending(principal, limit?)` → 预览 pending（不领取；正文截断）  
5. `claimNextDelivery(principal)` → 事件；`leases` 含本轮全部租约，`content` 为合并后的提示正文  
6. `acknowledge` / `acknowledgeAll` → 必须匹配 bot 与 nonce；活跃态结算，已终态同 nonce 幂等成功；批量按条结算  
7. `release(principal, deliveryId, claimNonce)` → 仅 `submitting` 可放回 pending  
8. `heartbeat(principal, leases)` → 刷新 `claimedAt`，避免 TTL 回收  
9. `postMessage(principal, groupId, content)`  
10. `revokeToken` / `restoreLocalRuntime` → 收回令牌并把成员切回 LOCAL；切回时该成员未结算 `submitting` 重新入队  
11. `listTokens(botId)` → 成员详情展示未撤销令牌（含已到期未清理）  
12. `revokeExpiredTokens(botId)` → 批量把已到期令牌标为撤销  

冷启动 `recoverDeliveries`：**保留**仍有效的 EXTERNAL `submitting` 租约（不清 `claimNonce`，避免电脑无法 ack 却再次领取同一消息）；仅把 LOCAL / 未知模式的中断收件标 `UNKNOWN`。失联 EXTERNAL 租约靠运行中 `claim` / `/me` / pending 预览触发的 TTL reclaim（`claimedAt`）。用户入口与回合中途 `@` EXTERNAL 都只落可 claim 收件，不进手机轮次槽位。

## LAN / HTTP 适配

`ExternalAgentRuntimeServer`（runtime）经 `ExternalAgentRuntimeGateway`（app：`TaiXuExternalAgentRuntimeGateway`）调用上述契约：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/runtime/v1/health` | 免鉴权探活（仅 `ok` / `service`） |
| GET | `/runtime/v1/meta` | 免鉴权路由目录、ack 状态与默认 TTL |
| GET | `/runtime/v1/me` | 身份 + `pendingCount` + `claimLeaseTtlMs` + 所在群摘要 |
| GET | `/runtime/v1/events/pending?limit=1..64` | 预览 pending（不领取；默认 16） |
| POST | `/runtime/v1/events/claim?waitMs=0..30000` | 领取下一轮；空队列 `204`；`waitMs` 长轮询；含 `leaseExpiresAt` |
| POST | `/runtime/v1/events/ack` | 单条或批量回执；`status` 仅 `completed` / `failed` / `rejected` / `unknown`（非法 → 400 `invalid_ack_status`）；同 nonce 已结算 → 幂等成功；租约不符 → 409 `lease_mismatch` |
| POST | `/runtime/v1/events/release` | 放弃租约，收件回到 pending |
| POST | `/runtime/v1/events/heartbeat` | 续租（`leases[]` 或单条 deliveryId/claimNonce） |
| GET | `/runtime/v1/groups` | 本成员所在群列表 |
| GET | `/runtime/v1/groups/{groupId}/context?limit=1..100` | 最近消息（默认 40） |
| POST | `/runtime/v1/groups/{groupId}/messages` | 以该 Bot 身份发言 |

成员详情「人格」页的外部运行时面板可签发/撤销令牌、启停局域网服务、切回 LOCAL；**签发令牌时若服务未运行会尝试自动启动**。`EXTERNAL` 且无可用令牌时面板告警（本地泵也不会领取）。默认端口 `8901`，与 WebChat（通常 `8899` + PIN）隔离。

### curl 示例

```bash
BASE=http://192.168.1.20:8901
TOKEN=tx_rt_...

curl -s "$BASE/runtime/v1/health"
curl -s "$BASE/runtime/v1/meta"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/runtime/v1/me"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/runtime/v1/events/pending?limit=16"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/runtime/v1/groups"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/runtime/v1/groups/{groupId}/context?limit=40"

# 最长等待 25s；有事件返回 200 + leases，否则 204
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/runtime/v1/events/claim?waitMs=25000"

# 单条回执
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"deliveryId":"…","claimNonce":"…","status":"completed"}' \
  "$BASE/runtime/v1/events/ack"

# 批量回执（合并轮次）
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"completed","leases":[{"deliveryId":"…","claimNonce":"…"},{"deliveryId":"…","claimNonce":"…"}]}' \
  "$BASE/runtime/v1/events/ack"

# 执行器崩溃前或主动放弃：放回队列
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"deliveryId":"…","claimNonce":"…"}' \
  "$BASE/runtime/v1/events/release"

# 长任务续租（默认 15 分钟无心跳会回收）
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"leases":[{"deliveryId":"…","claimNonce":"…"}]}' \
  "$BASE/runtime/v1/events/heartbeat"

curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"content":"已在电脑上处理完毕"}' \
  "$BASE/runtime/v1/groups/{groupId}/messages"
```

### 参考轮询脚本

跨平台（仅标准库）脚本：[`scripts/external_agent_runtime_poller.py`](../scripts/external_agent_runtime_poller.py)。先探活 `/health`，再打 `/me` 与 `/events/pending`，按 `waitMs` 长轮询 `claim`；默认只打印事件，加 `--ack`（可配 `--ack-status` / `--ack-error`）、`--post` 进度发言，或 `--release` 放回队列；长任务可用 `--auto-heartbeat` / `--heartbeat-secs`。

```bash
export TAIXU_RUNTIME_BASE=http://192.168.1.20:8901
export TAIXU_RUNTIME_TOKEN=tx_rt_...
python scripts/external_agent_runtime_poller.py --wait-ms 25000
python scripts/external_agent_runtime_poller.py --ack --once
python scripts/external_agent_runtime_poller.py --ack --ack-status failed --ack-error "tool crashed" --once
python scripts/external_agent_runtime_poller.py --post "处理中…" --ack --once
python scripts/external_agent_runtime_poller.py --auto-heartbeat
```

长任务可先 `claim` 再在本机执行，期间用 `--auto-heartbeat` / `--heartbeat-secs` 按 `leaseExpiresAt` 续租；`--post` 或直接 `POST …/groups/{id}/messages` 发进度，最终 `ack` / `release`。

## 与 Commonly 的对照

| Commonly | 太墟 |
| --- | --- |
| runtime token + pod install | Bot + hashed token + runtimeMode |
| event claim + delivery nonce | `claimDelivery` / `claimNonce` |
| bounded mention coalesce | 同群 pending 最多 8 条并入一轮 `leases` |
| message post into pod | `postMessage` 入群 |
| CAP HTTP 路由 | `/runtime/v1/*` + Bearer `tx_rt_`；claim 支持 `waitMs` |
| mid-turn / stranded reclaim | `claimedAt` TTL（默认 15m）+ `heartbeat` 续租；冷启动保留 EXTERNAL 租约 |

## 迁移

数据库 89→90：`persona_bots.runtimeMode` 默认 `LOCAL`；新建 `external_agent_runtime_tokens`。历史成员保持本地执行。  
数据库 90→91：`group_deliveries.claimedAt`，用于 EXTERNAL 租约 TTL / 心跳续租。

## 刻意不做

本协议只覆盖「外部执行器以 Bot 身份入群」：不代理工具/模型/审批/HostBridge，不提供 Commonly 式 memory envelope 或 scheduled heartbeat 事件。进度靠群消息，续租靠 `heartbeat`，结果靠 `ack`。

## 安全说明

令牌等同该 Bot 的群收件租约与发言权，不授予宿主权限或其它成员能力。丢失后应撤销并 `restoreLocalRuntime`。HTTP 面与 WebChat PIN 正交，详见 [`SECURITY_SURFACE.md`](SECURITY_SURFACE.md)。
