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
| 合并 | 同一群内连续未领取收件最多合并 8 条进一轮；执行中新到的消息仍排队到下一轮 |
| 发言 | 仅允许向该成员所在群发 `BOT` 消息 |
| HTTP | 独立 LAN 口 `0.0.0.0:8901`，**只认** `Authorization: Bearer tx_rt_…`，不接受 Web 协作台 PIN |
| 不包含 | 工具代理、模型调用、审批代签、HostBridge；外部副作用仍由执行器本机负责 |

## 服务入口

`ExternalAgentRuntimeService`（harness）：

1. `issueToken(botId, label, expiresAt?)` → 切换 EXTERNAL 并返回明文令牌  
2. `authorize(rawToken)` → `ExternalAgentPrincipal`  
3. `claimNextDelivery(principal)` → 事件；`leases` 含本轮全部租约，`content` 为合并后的提示正文  
4. `acknowledge(principal, ack)` → 必须对 **每个** lease 匹配 bot、nonce 与活跃状态  
5. `postMessage(principal, groupId, content)`  
6. `revokeToken` / `restoreLocalRuntime` → 收回令牌并把成员切回 LOCAL  
7. `listTokens(botId)` → 成员详情面板展示未撤销令牌  

## LAN / HTTP 适配

`ExternalAgentRuntimeServer`（runtime）经 `ExternalAgentRuntimeGateway`（app：`TaiXuExternalAgentRuntimeGateway`）调用上述契约：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/runtime/v1/me` | 令牌身份 |
| POST | `/runtime/v1/events/claim?waitMs=0..30000` | 领取下一轮；空队列 `204`；`waitMs` 长轮询 |
| POST | `/runtime/v1/events/ack` | 回执单条 lease（`deliveryId` / `claimNonce` / `status`） |
| POST | `/runtime/v1/groups/{groupId}/messages` | 以该 Bot 身份发言 |

成员详情「人格」页的外部运行时面板可签发/撤销令牌、启停局域网服务、切回 LOCAL。默认端口 `8901`，与 WebChat（通常 `8899` + PIN）隔离。

### curl 示例

```bash
BASE=http://192.168.1.20:8901
TOKEN=tx_rt_...

curl -s -H "Authorization: Bearer $TOKEN" "$BASE/runtime/v1/me"

# 最长等待 25s；有事件返回 200 + leases，否则 204
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/runtime/v1/events/claim?waitMs=25000"

# 对 leases[] 中每一项各回执一次
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"deliveryId":"…","claimNonce":"…","status":"completed"}' \
  "$BASE/runtime/v1/events/ack"

curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"content":"已在电脑上处理完毕"}' \
  "$BASE/runtime/v1/groups/{groupId}/messages"
```

## 与 Commonly 的对照

| Commonly | 太墟 |
| --- | --- |
| runtime token + pod install | Bot + hashed token + runtimeMode |
| event claim + delivery nonce | `claimDelivery` / `claimNonce` |
| bounded mention coalesce | 同群 pending 最多 8 条并入一轮 `leases` |
| message post into pod | `postMessage` 入群 |
| CAP HTTP 路由 | `/runtime/v1/*` + Bearer `tx_rt_`；claim 支持 `waitMs` |

## 迁移

数据库 89→90：`persona_bots.runtimeMode` 默认 `LOCAL`；新建 `external_agent_runtime_tokens`。历史成员保持本地执行。

## 安全说明

令牌等同该 Bot 的群收件租约与发言权，不授予宿主权限或其它成员能力。丢失后应撤销并 `restoreLocalRuntime`。HTTP 面与 WebChat PIN 正交，详见 [`SECURITY_SURFACE.md`](SECURITY_SURFACE.md)。
