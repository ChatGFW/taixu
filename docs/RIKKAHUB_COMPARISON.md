# RikkaHub 对照清单

对照日期为 2026-11-06。RikkaHub 使用本地 `C:/work2/rikkahub` 的 `903e058b` 快照，太墟使用 `4c441623` 快照。覆盖 README 公开功能、模块目录，以及主要聊天、设置、Provider 和数据管理入口，归并为 34 组。

本次以静态源码、模型定义及入口调用为依据。实现状态反映代码覆盖范围，运行质量仍需对应测试或真机验收。新候选条目采用“本轮未定位同等原生入口”的口径，外部插件和 Linux 命令实现的相近功能单独看待。

## 汇总

| 类别 | 数量 | 判定方式 |
| --- | --- | --- |
| 已落地 | 9 | 前几轮确定的借鉴范围有对应代码入口 |
| 已有对应 | 8 | 太墟原有架构具备同类能力 |
| 部分覆盖 | 14 | 已有基础功能，仍存在配置、协议或交互范围差异 |
| 新增候选 | 3 | 本轮源码核查发现的独立扩展方向 |

## 逐项对照

### 已落地

| 编号 | 对照项 | 太墟当前覆盖 | 范围说明与后续借鉴点 | 源码证据 |
| --- | --- | --- | --- | --- |
| 01 | 工作区文件补全 | 末尾 @ 补全、忽略规则、目录继续选择、会话环境绑定。 | 当前约定的文件引用交互已覆盖。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/components/ai/completion/WorkspaceCompletionProvider.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/WorkspaceMentionSuggestions.kt) |
| 02 | 工具结果展示分派 | ToolUiRegistry、工具头部和详情/预览扩展。 | 展示扩展机制已覆盖，具体工具种类可按需增加。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTools.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ToolUiRegistry.kt) |
| 03 | Provider 流式解码拆分 | Chat Completions、Responses、Anthropic 独立解码器。 | 协议解码职责与传输分开。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/ai/src/main/java/me/rerere/ai/provider/stream/StreamChunkDecoder.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/ProviderStreamDecoder.kt) |
| 04 | DOCX/PPTX/EPUB/PDF 正文提取 | 独立 core/document、有界解析、附件正文旁路。 | PDF 沿用已确认方案，Android 15+ 用系统能力，旧版提示并保留文件。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/ai/transformers/DocumentAsPromptTransformer.kt) · [太墟](../core/document/src/main/java/top/wkbin/taixu/core/document/DocumentParser.kt) |
| 05 | 排队消息原位编辑 | 编辑占用、固定队列项身份、保留附件与任务关联。 | 当前编辑/取消/恢复流程已覆盖。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/service/MessageQueue.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/QueuedPromptEditor.kt) |
| 06 | Markdown 模块化、缓存与流式增量 | core/markdown 纯 Kotlin、渲染插槽、有界缓存和增量解析。 | 基础解析与渲染解耦已覆盖，公式/图示另列。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Markdown.kt) · [太墟](../core/markdown/src/main/kotlin/top/wkbin/taixu/core/markdown/MarkdownTextCache.kt) |
| 07 | 跨会话消息搜索 | 全局消息命中、字面量匹配、状态与高亮。 | 基础检索链路已覆盖，整理功能另列。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/pages/search/SearchVM.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/SessionMessageSearch.kt) |
| 08 | 会话专属草稿与附件归属 | 按会话管理输入、附件导入和分享，有限文字状态恢复。 | 持久恢复范围为有界文字快照，附件重建范围需独立验收。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/SessionComposerDrafts.kt) |
| 09 | 前台恢复顺序与消息操作归属 | 共享初始化协调、选择代次、过期消息操作校验。 | 覆盖本轮约定的恢复和五类消息按钮隔离。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatMessageActions.kt) |

### 已有对应

| 编号 | 对照项 | 太墟当前覆盖 | 范围说明与后续借鉴点 | 源码证据 |
| --- | --- | --- | --- | --- |
| 10 | PRoot/SSH 工作区与终端 | 已有 Linux Runtime、SSH ExecutionEnvironment、原生 PTY。 | 可比较细节体验，继续沿用当前执行环境边界。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceRepository.kt) · [太墟](../runtime/src/main/java/top/wkbin/taixu/runtime/remote/SshExecutionEnvironment.kt) |
| 11 | MCP、OAuth 与 Skills | 已有 MCP 管理、OAuth、技能安装和工具审批链路。 | 会话级启用选择归入配置快照条目。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpManager.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/mcp/McpManager.kt) |
| 12 | 消息分支与回退 | 不可变会话树、Lane、检查点及回退分支。 | 继续复用 SessionTreeStore 与既有回退协议。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/model/ConversationFork.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/session/SessionTreeStore.kt) |
| 13 | 上下文压缩、历史与记忆召回 | 已有预算、压缩、历史读取和召回工具。 | 后续可比较摘要展示与召回体验。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/ContextMemoryToolBackend.kt) |
| 14 | Web 多端聊天 | REST/SSE、共享会话控制、任务关联与完成事件。 | 已有本项目原生实现。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/service/WebServerService.kt) · [太墟](../runtime/src/main/java/top/wkbin/taixu/runtime/webchat/WebChatRunProtocol.kt) |
| 15 | 本地备份与恢复 | 数据库/附件/可移植偏好、恢复收据与原子日志。 | 云端传输和备份提醒另列，恢复继续复用现有协调器。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/sync/BackupManager.kt) · [太墟](../tools/src/main/java/top/wkbin/taixu/core/tools/backup/LocalBackupService.kt) |
| 16 | Token 与用量统计 | 会话、模型、供应商等维度的使用汇总。 | 供应商余额查询另列。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/pages/stats/StatsVM.kt) · [太墟](../feature/settings/src/main/java/top/wkbin/taixu/ui/settings/stats/StatsRepository.kt) |
| 17 | Material 3 Expressive 与自适应布局 | 统一主题、动态色、暗色和聊天多窗格布局。 | 后续可在具体页面做体验对照。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt) · [太墟](../feature/theme/src/main/java/top/wkbin/taixu/ui/theme/Theme.kt) |

### 部分覆盖

| 编号 | 对照项 | 太墟当前覆盖 | 范围说明与后续借鉴点 | 源码证据 |
| --- | --- | --- | --- | --- |
| 18 | 会话级配置快照 | 会话已绑定模型 ID、variant、工作区和审批/运行模式。 | 推理参数仍更新共享模型档案，MCP/Skills 等选择还需统一会话快照。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/model/ConversationConfig.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatViewModel.kt) |
| 19 | 原生 Provider 协议与账户接入 | 原生三协议注册和外部 Provider 扩展已具备。 | RikkaHub 另有 Google/Gemini、ChatGPT 账户等专用适配。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/ai/src/main/java/me/rerere/ai/provider/ProviderManager.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/plugins/BuiltinHarnessPlugins.kt) |
| 20 | 自定义 HTTP 头与请求体 | 模型档案支持 customHeaders。 | RikkaHub 的 customBodies 支持结构化请求体补充。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt) · [太墟](../core/database/src/main/java/top/wkbin/taixu/core/database/AiModelEntity.kt) |
| 21 | 多模态输入与媒体生成任务 | 图片输入、附件原文件交付和媒体产物展示已具备。 | RikkaHub 另有 Audio/Video 消息部件和独立图片/视频生成任务服务，能力开关需与真实协议实现区分。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/ai/src/main/java/me/rerere/ai/ui/UIMessagePart.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/session/ApiMessageProjector.kt) |
| 22 | 语音输入与多 Provider TTS | 系统 TTS 可朗读已完成的新回复，并随生命周期停止。 | RikkaHub 另有 ASR、多 TTS Provider、音频播放与分段控制。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/speech/src/main/java/me/rerere/asr/ASRController.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatSpeech.kt) |
| 23 | 数学公式与 Mermaid | 基础段落、代码、表格、图片等渲染已具备。 | 当前块模型及原生渲染入口还需扩展公式、行内数学与 Mermaid。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Markdown.kt) · [太墟](../core/markdown/src/main/kotlin/top/wkbin/taixu/core/markdown/MarkdownBlock.kt) |
| 24 | 网络搜索 Provider | 有 WebSearchProvider 接口，内置装配 Bing。 | RikkaHub 有 Tavily、Exa、Brave、SearXNG 等多源配置。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/search/src/main/java/me/rerere/search/SearchService.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/di/harness/ToolBackendModule.kt) |
| 25 | 模型档案迁移与二维码 | 已有模型档案 JSON 导入导出和凭据处理策略。 | RikkaHub 另有二维码传输及 Chatbox/Cherry Studio 导入适配。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/components/ui/QRCodeScanner.kt) · [太墟](../tools/src/main/java/top/wkbin/taixu/core/tools/AiProfileTransferFormat.kt) |
| 26 | 文件记忆与后台整理 | 已有上下文记忆、Bot 隔离的 Git 版本化记忆及冲突控制。 | RikkaHub 的空闲整理调度、跨会话待整理清单和整理状态值得单独比较。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/ai/MemoryConsolidationScheduler.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/bot/memory/BotMemoryManager.kt) |
| 27 | 翻译入口与历史 | 已有 ML Kit 离线翻译，聊天思考区有翻译入口。 | RikkaHub 另有独立翻译页、目标语言选择和历史分页。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/pages/translator/TranslatorVM.kt) · [太墟](../core/common/src/main/java/top/wkbin/taixu/core/common/translation/TranslationManager.kt) |
| 28 | 模型能力目录与供应商额度 | 已有模型发现、能力元数据和连接检测。 | RikkaHub 另有 ModelsDevCatalog 与供应商 quota 查询接口。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/ai/src/main/java/me/rerere/ai/provider/QuotaQueryRunner.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/model/ModelMetadataRegistry.kt) |
| 29 | 离线协议轨迹与语义快照 | 三协议合成 SSE fixture、分块回放、EOF 与工具参数检查已存在。 | RikkaHub 的 events.jsonl/expected.json 规范可用于扩充 usage、metadata、签名及图像等语义覆盖。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/ai/src/test/resources/stream-traces/README.md) · [太墟](../harness/src/test/resources/sse/README.md) |
| 30 | 会话导出 | 已有当前消息列表导出 Markdown 和系统分享。 | RikkaHub 另有选择消息、导出选项及长图导出。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ConversationExport.kt) · [太墟](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatExportMarkdown.kt) |
| 31 | 主聊天助手预设、模板与 Lorebook | 已有 Bot/子智能体角色、快捷短语和系统提示词变量。 | RikkaHub 将主聊天助手、消息模板、正则及 Lorebook 绑定在同一设置入口。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/prompt/PromptVariableResolver.kt) |

### 新增候选

| 编号 | 对照项 | 太墟当前覆盖 | 范围说明与后续借鉴点 | 源码证据 |
| --- | --- | --- | --- | --- |
| 32 | 统一消息转换契约与 OCR 扩展 | 已有请求投影、文档处理和系统提示词拼装等独立链路。 | 可借鉴输入转换、流式视觉转换、生成结束处理三阶段契约，以及能力感知 OCR 的显式入口。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt) · [太墟](../harness/src/main/java/top/wkbin/taixu/harness/session/ApiMessageProjector.kt) |
| 33 | 会话文件夹与消息收藏 | 已有置顶、搜索、工作区和 Bot 会话分组。 | 本轮未定位同等的用户自定义会话文件夹及消息收藏实体/管理页。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/repository/FavoriteRepository.kt) · [太墟](../core/database/src/main/java/top/wkbin/taixu/core/database/HarnessSessionEntity.kt) |
| 34 | WebDAV/S3 备份传输与提醒 | 已有本地归档、恢复和偏好设置。 | 本轮未定位设置页对应的原生 WebDAV/S3 备份客户端，RikkaHub 另有间隔备份提醒设置。 | [RikkaHub](https://github.com/rikkahub/rikkahub/blob/903e058b9361bff71d0b79f4ef2d8ac23d6597e6/app/src/main/java/me/rerere/rikkahub/data/sync/webdav/WebDavSync.kt) · [太墟](../core/datastore/src/main/java/top/wkbin/taixu/core/datastore/BackupPreferences.kt) |

## 应优先处理的配置隔离

RikkaHub 的 `ConversationConfig` 保存聊天模型、`reasoningLevel`、搜索开关、MCP 集合、工作区和 Skills 集合。太墟的 `HarnessSessionEntity` 已保存模型档案 ID、具体 variant、工作区、执行目标及审批/运行模式。

太墟当前 `ChatViewModel.updateActiveModelReasoning()` 查找当前会话绑定的模型档案，再调用 `aiModelDao.updateReasoning(profile.id, mode, effort)`。`ProviderModelResolver.resolveConfigured()` 重新从共享模型档案读取配置，再叠加全局推理偏好。因此，使用同一模型档案的其他会话会读取该档案的新推理设置。

建议在现有会话模型及解析入口中增加会话级覆盖，首次绑定时确定默认值，并保留明确的“跟随全局”选项。MCP/Skills/搜索等选择按同一套归属规则核查。凭据继续通过现有模型档案及安全仓储解析。

验收应覆盖两个会话绑定同一档案、分别调整推理参数、会话切换、进程重建、旧数据库记录和模型档案更新。

## 建议顺序

| 顺序 | 内容 | 验收重点 |
| --- | --- | --- |
| P1 | 会话配置快照及参数修改归属 | 同档案的 A/B 会话分别保持自己的选择，旧会话兼容迁移 |
| P2 | 公式与 Mermaid 渲染 | 普通文本/代码回退、长内容与流式未闭合输入、导出及主题一致性 |
| P2 | 文件夹和消息收藏 | 重命名、删除、跨分支定位、搜索入口及备份恢复 |
| P2 | 统一消息转换契约 | 复用现有投影链路，区分持久内容和视觉内容，明确顺序与取消语义 |
| P2 | 扩充离线协议 fixture 的语义覆盖 | 已有三协议 SSE 回放继续保留，增加 metadata/usage 等稳定断言 |
| P3 | 多搜索源、ASR/多 TTS、长图导出、云备份及专用 Provider | 根据实际使用需求逐项选择，并复用现有扩展与设置入口 |

## 已有能力的覆盖说明

- 文档正文提取沿用用户确认的系统 PDF 路线。Android 15+ 系统提取和旧系统保留文件属于本次约定范围。
- Markdown 导出已有真实分享入口。后续导出方向集中在选消息、选字段及长图。
- 三协议 SSE fixture 已进入当前仓库，并覆盖不同 HTTP 分块及截断。更丰富的语义快照属于继续扩展的内容。
- 系统 TTS、离线翻译、本地备份和 Bot 文件记忆均有现成实现，后续按各自差异继续比较。
- 系统提示词变量及快捷短语已有实现。消息级模板、正则、视觉变换和 Lorebook 需要单独设计归属及扩展契约。

## 范围与验证记录

本次增加对照文档，结论以所列两个 Git 快照为准。逐项链接已校验到本地对应文件，主要差异还结合字段和调用入口核查。功能级结论来自静态代码对照，界面交互、ROM 差异、实际供应商响应和设备性能需要独立验收。
