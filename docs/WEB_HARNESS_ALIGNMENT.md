# Web 协作台与 Harness 对齐

Web 使用 `WebChatAgentGateway` → `SessionControl` 复用原生会话受理、工具执行、审批与任务状态，不另建 Agent 循环。

## 已接入

- 会话创建、删除、历史消息和 SSE 更新。
- `next_run`、`steer`、`follow_up` 三种输入模式；执行期间仍可发送。队列语义由 Harness 决定，页面展示受理回执，不把排队视为执行完成。
- 每个会话独立跟踪多个请求；追加请求失败不会清掉原任务，迟到的 HTTP 回执不会复活已结束任务。
- 停止请求沿用现有会话级取消语义，等待完成事件后结束运行显示。
- 会话列表返回当前 Web 跟踪请求 ID；刷新或 SSE 重连后重新加载会话、消息与审批。此恢复仅覆盖本次服务进程仍在跟踪的 Web 请求，不承诺恢复原生入口启动的任务或服务重启前的观察器。
- 工具审批与拒绝、回答的 `up` / `down` 反馈及可选说明；反馈写入原生反馈仓储，重新打开会话读取既有反馈。
- 工具调用关联最新结果，展示成功、失败、待审批、延期审批及耗时；空闲会话没有结果的调用显示中断，不虚构成功。
- 当前会话工作区展示、图片输入、已有工作区文件操作与 PTY 终端。非图片文件须从工作区上传，聊天入口不再把被后端忽略的文件显示为已发送。
- 阅读历史时不强制滚到底部；回到底部后恢复自动跟随。

## 尚未由 Web 网关开放

Harness 的澄清问题答复、队列编辑、分支切换、消息编辑重发、重新生成、工具重试、检查点回退、模型/审批模式修改及 MCP 推荐管理仍需原生入口。它们需要各自的会话归属与状态校验接口，不能以发送一段提示词代替控制操作。

## 验证

- `webchat`：`node --experimental-strip-types --test tests/*.test.ts`、`tsc --noEmit`、`vite build`。
- Runtime：`WebChatRunProtocolTest`、`WebChatRunTrackerTest`。
- App：`top.wkbin.taixu.webchat.*`，使用假 Provider / 本地数据库，不调用付费模型。
- 构建产物同步至 `app/src/main/assets/webchat`，随 APK 提供；部署后仍需真机验证 LAN 配对、SSE 与 PTY。
