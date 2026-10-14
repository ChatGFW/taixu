# MCP 能力仓库（MCP Catalog）方案

> 目标：把「有哪些 MCP」「怎么装」「怎么更」拆成三层，只对本项目真正有用的条目做精选入驻，并保证离线可用与可校验。
> 配套数据文件：[`mcp-catalog.json`](mcp-catalog.json)（种子清单，每条含实测校验和）。

---

## 1. 结论先行：三层分工，不要合并

| 层 | 承担者 | 职责 | 不能做什么 |
| :--- | :--- | :--- | :--- |
| **发现层** | `punkpeye/awesome-mcp-servers` | 人工浏览「世界上有哪些 MCP」 | 不含包名、安装命令、版本、校验和，无法程序化消费 |
| **安装 / 更新层** | npm registry（走 `registry.npmmirror.com`）、PyPI | 提供 `version` + 校验和 + tarball，是唯一的可复现安装依据 | 不含「适不适合本项目」的判断 |
| **精选层** | 本项目的 `mcp-catalog.json` + 插件包体系 | 白名单、固定版本、校验和、风险等级、依赖声明、生命周期 | 不追求覆盖全量 |

原提问是「把 awesome-mcp-servers 里适合 harness 的摘出来做仓库」。**方向对，但不能把它当仓库源**——只能当发现层的输入。原因见下节实测。

---

## 2. 为什么不能把 awesome-mcp-servers 直接当仓库源

实测（2026-10-13，取 `main` 分支 README）：

| 指标 | 实测值 |
| :--- | :--- |
| README 体积 | 1,433,054 字节（1.4 MB） |
| 行数 | 4,686 |
| 条目总数 | 4,230 |
| 仓库内机器可读数据文件 | **0 个**（只有 README、多语言副本、LICENSE、CONTRIBUTING） |
| 条目字段 | 仓库 URL + 语言/scope/OS 标记 + 一句话描述 |
| 是否含包名 | 否 |
| 是否含安装命令 | 否 |
| 是否含版本约束 | 否 |
| 是否含校验和 | 否 |
| 是否有可信质量信号 | 无（大量条目带 glama.ai 自动徽章，含大量个人小仓库） |

条目真实格式：

```text
- [owner/repo](https://github.com/owner/repo) 📇 🏠 🐧 - 一句话描述
```

结论：这份索引能告诉你「某个仓库存在」，但**告诉不了你怎么把它装起来**。要变成可安装条目，仍须逐个读每个仓库的 README 去推断包名与启动方式——这一步无法可靠自动化。因此它只能作为人工挑选的素材来源。

---

## 3. 筛选口径：四道筛子

### 第一道：运行时可承载（决定能否跑）

- 必须本地运行（`🏠`），纯云条目（`☁️`）不入库
- 语言限 TS/JS（`📇`，走 npx）或 Python（`🐍`，走 pip）；Go/Rust/C#/Java 源码型一律排除（ARM64 无预编译产物，需上机编译）
- 不排除 Linux（无 `🍎`/`🪟` 独占标记）
- 不含 Docker daemon 依赖（PRoot 为用户态，无容器运行时）
- 不含真实桌面浏览器依赖（Chromium / Chrome）

### 第二道：不重复 harness 已有能力

harness 原生已提供 `base` / `process` / `read` / `write` / `edit`，并内置 in-app WebView browser MCP。因此下列类别**原则上不收**：

| 类别 | 索引条目数 | 不收理由 |
| :--- | ---: | :--- |
| Command Line | 26 | 与 `base`/`process` 重复 |
| File Systems | 48 | 与 `read`/`write`/`edit` 重复 |
| Browser Automation | 109 | 与内置 browser MCP 重复，且多数需 Chromium |
| OS Automation | 21 | 宿主侧能力，沙箱内无意义 |

### 第三道：规模收敛（说明为什么必须人工精选）

| 指标 | 数值 |
| :--- | ---: |
| 条目总数 | 4,230 |
| 通过第一道筛子（本地 + npx/pip + 支持 Linux） | **1,945（46%）** |
| 各相关分类条目数 | Developer Tools 313、Knowledge & Memory 233、Security 134、Databases 72、Search & Data Extraction 96、Coding Agents 62 |

宽松筛子就能过 46%，说明**自动化全量导入必然失败**——会得到一个 1,945 条、用户无从选择的清单。必须叠加第二道 + 第四道，收敛到十位数。

### 第四道：可校验（决定能否安全安装）

每个入库条目的包名与版本，都必须在 registry 上实机验证过：版本存在、`dist.integrity` 存在、`tarball` 存在。未验证条目一律不入库。

---

## 4. 安装与更新架构：双通道 + 固定版本

### 4.1 通道一：npm（首选）

`registry.npmmirror.com` 同时提供四处所需信息，实测样本：

| 包 | 固定版本 | `dist.integrity` | 解包体积 |
| :--- | :--- | :--- | ---: |
| `open-websearch` | 2.2.0 | sha512-Wv2iZqkn…sdnPTw== | 720 KB |
| `@colbymchenry/codegraph` | 1.6.2 | sha512-hODH/mGn…XzUH6Q== | 1.2 MB |
| `@modelcontextprotocol/server-memory` | 1.0.0 | sha512-UDMtMrw2…admODw== | 55 KB |
| `@modelcontextprotocol/server-sequential-thinking` | 1.0.0 | sha512-ykTmeS4E…Xt0J6w== | 22 KB |
| `@upstash/context7-mcp` | 4.3.0 | sha512-fuDt5nWj…Dp/rPA== | 202 KB |
| `@modelcontextprotocol/server-github` | 2025.4.8 | sha512-8N43bQw9…WkwEw== | 73 KB |

### 4.2 通道二：PyPI（按需）

PyPI 提供 `info.version` + `urls[].digests.sha256`，同样可固定可校验。实测样本：

| 包 | 固定版本 | requires_python |
| :--- | :--- | :--- |
| `mcp-server-git` | 2026.10.10 | >=3.10 |
| `mcp-server-fetch` | 2026.10.10 | >=3.10 |
| `mcp-server-time` | 2026.10.10 | >=3.10 |
| `mcp-pandoc` | 0.11.1 | >=3.11 |
| `mcp-server-duckdb` | 1.1.0 | >=3.10 |
| `markitdown-mcp` | 0.0.1a7 | >=3.10 |

**注意**：官方参考服务器是双通道分裂的——`filesystem` / `memory` / `sequential-thinking` / `everything` 在 npm；`git` / `fetch` / `sqlite` / `time` 只在 PyPI。所以安装器必须同时支持两条通道。

**pip 通道的额外约束**：必须为每个 pip 型 MCP 建独立 venv，否则依赖互相污染；这比 npm 的 store 隔离成本高，也是把 pip 通道限定在「必要条目」的原因。

### 4.3 三个必须守住的约束

1. **禁止 `@latest`。** 一律固定版本号。PyPI 官方服务器用日期式版本号（如 `2026.10.10`），latest 会持续漂移，固定版本更是必需。
2. **安装期落盘，运行期不联网。** 安装时执行 `npm i -g <pkg>@<pin>`（校验 `integrity`）到沙箱本地；运行时只调用本地已装包。若运行时才 `npx -y`，冷启动拉包会导致 `discoverTools()` 超时，`MIGRATION_35_36` 的 120 秒启动阻塞会以新形式复现。
3. **更新是显式动作，不是静默行为。** 提供「检查更新」按钮：比对 registry 上的 latest 与本地固定版本，有差异出角标；用户点击后才升级并重新校验。升级对象仍写入新的固定版本号。这与 `AGENTS.md` 中「构建期禁止自动下载」「固定摘要」的既有铁律一致。

### 4.4 为什么必须坚持白名单 + 校验和

实测中 `server-github`（无 scope 的同名包）返回版本 `0.0.1-security`——这是 npm 的**安全占位包**，意味着该名称历史上发生过抢注或投毒。同理 PyPI 上存在 `mcp-server-memory` 0.0.1 这种与官方重名的可疑包。因此：

- 只允许从白名单包名安装，禁止按名称模糊解析
- 固定版本 + 校验和比对，任一不符即中止
- 绝不使用 `@latest` 或裸包名

---

## 5. 生命周期：四个状态，不可合并

`MIGRATION_35_36` 的教训是：**「已注册但文件不存在」会让 Agent 首次启动阻塞 120 秒**。因此不能用一个 `isEnabled` 字段同时表达「文件在不在」和「用户想不想启用」。

| 状态 | 含义 | 是否进 bootstrap |
| :--- | :--- | :--- |
| 未安装 | 仅存在于 catalog | 否 |
| 已安装 · 未启用 | 包已落盘校验通过，用户未开启 | 否 |
| 已安装 · 已启用 | 两者都满足 | **是** |
| 已安装 · 需初始化 | 包已就绪但工作区索引未建（如 codegraph） | 否，需完成初始化 |

第四态是 codegraph 这类带 per-workspace 索引的 MCP 特有的，UI 与门控都要能表达。

---

## 6. 种子清单（12 条，全部实机验证）

完整字段见 [`mcp-catalog.json`](mcp-catalog.json)。摘要：

> **本节是规划记录。本期实际入库的条目见 §10.3**——两者差异：`mcp_markitdown` / `mcp_duckdb` 未入库（pip 通道裁决为不开，且 duckdb 原生扩展 ARM64 可得性未实测）；`mcp_git` 维持内置自研脚本而非走 PyPI。

| id | 建议 | 通道 | 固定版本 | 风险 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `mcp_websearch` | 跟上游 | npm | `open-websearch@2.2.0` | medium | 替代自研 `websearch_mcp_server.py` |
| `mcp_codegraph` | 跟上游 | npm | `@colbymchenry/codegraph@1.6.2` | medium | 替代自研 `codegraph_mcp_server.py`，需工作区索引 |
| `mcp_memory` | 补缺口 | npm | `server-memory@1.0.0` | low | 需与 Room `agent_memories` 划清边界 |
| `mcp_sequential_thinking` | 补缺口 | npm | `server-sequential-thinking@1.0.0` | low | 纯计算，可免审批 |
| `mcp_context7` | 补缺口 | npm | `@upstash/context7-mcp@4.3.0` | medium | 依赖境外端点，失败须降级不阻塞 |
| `mcp_github` | 可选 | npm | `server-github@2025.4.8` | high | 需凭证，走加密仓库，未配置则保持未启用 |
| `mcp_git` | 可选 | pypi | `mcp-server-git@2026.10.10` | medium | 与自研 4.2KB 脚本重叠，收益有限 |
| `mcp_fetch` | 补缺口 | pypi | `mcp-server-fetch@2026.10.10` | low | 只抓给定 URL，比 websearch 更可控 |
| `mcp_time` | 补缺口 | pypi | `mcp-server-time@2026.10.10` | low | 零风险，优先级低 |
| `mcp_pandoc` | 补缺口 | pypi | `mcp-pandoc@0.11.1` | low | 需沙箱内 `pandoc` 二进制 |
| `mcp_markitdown` | 补缺口 | pypi | `markitdown-mcp@0.0.1a7` | medium | 仅预发布版，标记实验性 |
| `mcp_duckdb` | 补缺口 | pypi | `mcp-server-duckdb@1.1.0` | low | duckdb 原生扩展 ARM64 可得性待实测 |

### 明确排除

| 候选 | 排除理由 |
| :--- | :--- |
| `@executeautomation/playwright-mcp-server` | 需 Chromium，PRoot ARM64 无法承载 |
| `chrome-devtools-mcp` | 需真实 Chrome；内置 WebView MCP 已覆盖 |
| `@modelcontextprotocol/server-puppeteer` | 同 Playwright |
| `mcp-server-docker` | 需 Docker daemon，PRoot 无容器运行时 |
| `server-github`（无 scope 同名包） | `0.0.1-security` 安全占位包，抢注/投毒信号 |
| `mcp-server-memory`（PyPI） | 0.0.1 版本异常，与官方重名，疑似抢注 |
| `mcp-server-sqlite` | 官方实现停在 2025.4.25，弱于项目自研脚本 |
| 云平台 / 境外 SaaS 类 | 需境外 Key，与「离线开箱即用」定位冲突 |
| Go/Rust/C#/Java 源码型 | ARM64 无预编译产物 |
| 地图 / 地理类 | 涉中国地图数据合规，境内服务须符合国家测绘规范 |

---

## 7. 与现有基建的对接点（不新造物种）

项目已具备落地所需的全部机制，MCP 条目应表述为**插件组件**而非新类型：

| 需求 | 复用对象 |
| :--- | :--- |
| 版本 / 更新策略 / 安装卸载步骤 / 校验 | `core/model/.../ToolManifest.kt`（`version`、`latestVersion`、`updateStrategy=REINSTALL`、`source`、`installSteps`、`uninstallSteps`、`verifyCommand`、`offlineOnly`） |
| 组件化 + 状态探针 + 卸载清理 | `core/model/.../DevEnvironmentSuite.kt` 的 `PluginBundle` / `PluginComponent`（`checkCommand`、`postInstallSteps`、`dependsOn`、`purgeFiles`、`purgePaths`）—— `android-suite` / `flutter` / `nodejs-suite` 即现成范式 |
| 事务、日志、通知 | `tools/.../InstallTransactionManager.kt`、`InstallLogRepository.kt`、`ToolNotificationNotifier.kt` |
| Node 运行时 | `tools/.../RuntimeBinaryInstaller.kt`（`NODE_VERSION=22.22.3`）；`DevEnvironmentSuite` 的 `nodejs-core`/`nodejs-pkg`（`setup_pnpm.sh` 已指向 `registry.npmmirror.com`） |
| MCP 配置落库 | `mcp_servers` 表 + `BuiltinMcpPresets`（`core/model/.../McpModels.kt`） |
| 凭证安全 | [`SECURITY_SURFACE.md`](SECURITY_SURFACE.md) 的加密仓库与脱敏规则 |
| 离线兜底 | `assets/plugins/*` 的 `offlineOnly: true` + `checksums/SHA256SUMS` 模式（`.txplugin` 侧载） |

**离线兜底必须做**：当前这批能力是躺在 APK 里、断网可用的；改为按需安装后，首次初始化必须有网且要能访问境外源。国内网络下这是最不可靠的一环，因此必须允许把包 tarball 打进 `.txplugin` 侧载安装，对齐既有 `offlineOnly` 模式。

---

## 8. 分阶段

> 阶段一、阶段二已落地（见 §10）；阶段三未开工。

**阶段一 · 止血（无新架构）** ✅

- `BuiltinMcpPresets` 仅保留 `mcp_sqlite`、`mcp_git`、`taixu-browser-builtin`
- `mcp_websearch` / `mcp_codegraph` / `mcp_apktool` 的预设置为未安装 + 未启用，新增「安装状态」维度门控，使其不进 bootstrap
- `mcp_apktool` 的自研脚本保留，改为随 `android-suite` 的「逆向分析与代码审计」子组件下发（其上游 `zinja-coder/apktool-mcp-server` 为 Python + uv 项目、无发布包，引入 uv 比自研脚本更重）

**阶段二 · 能力仓库** ✅

- 在 `BuiltinPluginBundles` 新增 `mcp-catalog` bundle，条目来自 `mcp-catalog.json`
- 补安装器：npm 通道 `npm i -g <pkg>@<pin>` + 校验 `integrity`；pip 通道建独立 venv + 校验 sha256（**pip 通道本期裁决为不开，见 10.2**）
- ⬜ 补「检查更新」入口与角标，升级需用户显式触发
- 修 `discoverTools()` 对未安装条目的跳过逻辑，杜绝启动超时

**阶段三 · 上游版本通道** ⬜ 未开工

- catalog 增加 `latestVersion` 与变更日志抓取（读 registry 元数据即可，无需爬 GitHub）
- 评估 `mcp_apktool` 是否最终也要迁到上游（取决于上游是否发 pip 包）

---

## 9. 待决问题（已裁决）

> 三条都在落地时做了取舍，裁决理由见第 10 节。这里保留问题本身，避免后来者重复讨论。

1. **`mcp_memory` 与 Room `agent_memories` 的边界**：谁写谁读，是否需要同步，若不划清会出现双写不一致。
   → **裁决：职责分离，不做同步**（见 10.1）。
2. **pip 通道是否值得开**：需要 per-MCP venv 与 `python3-pip`，成本高于 npm。若只保留 npm 通道，则 `git`/`fetch`/`time`/`pandoc`/`markitdown`/`duckdb` 六条全部放弃（官方在这些包上只有 Python 实现）。
   → **裁决：本期不开**（见 10.2）。
3. **`mcp_codegraph` 的索引时机**：装配期建索引会显著拉长安装时长；会话期建则首轮对话要等。需要定一个默认策略。
   → **裁决：都不做，交给 CLI 显式初始化**（见 10.3）。

---

## 10. 已接入实现（本期落地记录）

### 10.1 落地结构：目录条目（纯数据）+ 组件（复用装配流水线）

实现刻意**没有引入任何新物种**，全部沿用既有两条链路：

| 关注点 | 落地位置 |
| :--- | :--- |
| 条目声明（版本 / 校验和 / 风险 / 依赖 / 索引需求） | `core/model/.../McpCatalog.kt`（纯数据 + 纯函数，不碰文件系统、不执行命令） |
| 安装 / 探针 / 卸载执行 | `core/model/.../DevEnvironmentSuite.kt` 的 `mcp-catalog` bundle，每条能力由 `McpCatalog.component(entry)` 生成一个 `PluginComponent` |
| 装配实现 | `app/src/main/assets/scripts/setup_mcp_catalog.sh`（镜像查询 `dist.integrity` → 逐字比对 → 全局安装 → 写落位标记） |
| 状态唯一真相 | 组件探针 `setup_mcp_catalog.sh probe <id> <packageName>`：核对本地标记、实际包版本与入口。**安装状态不落库** |
| 状态 → 启停投影 | `McpServerRepository.syncInstalledCapabilities()`：仅同步默认启停；保留 `userToggled = 1` 的偏好。执行层每次在当前环境重新核验，以「默认或用户允许且包就绪」决定实际可用性 |
| 提示词区分 | `harness/prompt/McpCapabilitySection.kt`：未安装 / 未启用 / 已启用三态分述，未安装时明确要求"引导去安装"而不是"请求切开关" |
| UI 门控 | `McpCatalogViewModel` + `McpServerItemCard`：未安装能力的开关锁死，卡片直给"需先安装「X」" |

**为什么安装状态不落库**：这正是 `MIGRATION_35_36` 的教训——一旦数据库独立记一份"已安装"，就会出现「数据库说装了、文件其实不在」的第二真相，而 Agent 启动期会照着它去 `discoverTools()`，白等一个超时（120 秒启动阻塞）。执行层不直接相信数据库启停投影或落位标记；发现、调用与 STDIO 通道创建前核验实际包，探针失败即不可用。

### 10.2 三条裁决

1. **`mcp_memory` 与 Room `agent_memories`：职责分离，不做同步。**
   上游 `server-memory` 是「模型自管的知识图谱」（实体/关系 JSON），`agent_memories` 是工程记忆。两者写入者、读取者、生命周期都不同；强行同步只会产生双写不一致。条目备注里已显式写明「不回写工程记忆」，因此**不开同步通道**。
2. **pip 通道本期不开。**
   理由与 §4.2 的约束一致：每个 pip 型 MCP 必须独占 venv，成本高于 npm 的 store 隔离。因此 `mcp_fetch` / `mcp_time` / `mcp_pandoc` 三条以 `available = false` + `unavailableReason` 留在 catalog 里，**可见但不进开发套件**（`McpCatalog.installable` 天然把它们排除），既暴露了规划项，又不会让用户点到装不上的按钮。
   `mcp_git` 维持内置自研脚本（与官方 PyPI 包功能重叠，收益不足）；`mcp_apktool` 同样维持内置（上游 `zinja-coder/apktool-mcp-server` 是 Python + uv 项目、无发布包，引入 uv 比自研脚本更重）。
3. **codegraph 索引：装配期不建、会话期不建，交给 CLI 显式初始化。**
   条目带 `requiresWorkspaceIndex = true`（会渲染进组件描述），但装配步骤里**没有**建索引动作；`code_graph` 技能与 `code-navigation.md` 都改成了「索引不再自动刷新，缺失时用 `base` 执行一次 CLI 初始化」。理由是索引是 per-workspace 的、随时会脏，把它的生命周期绑到"安装"这个一次性动作上是错配。

### 10.3 本期实际入库清单

**可一键安装（npm，`available = true`，共 5 条）**

| id | 包 + 固定版本 | 风险 | 备注 |
| :--- | :--- | :--- | :--- |
| `mcp_websearch` | `open-websearch@2.2.0` | medium | 新版不再内置 Playwright，正文抓取仅 request 模式 |
| `mcp_codegraph` | `@colbymchenry/codegraph@1.6.2` | medium | `requiresWorkspaceIndex`；`better-sqlite3` 未命中 ARM64 预编译时回落 WASM |
| `mcp_memory` | `@modelcontextprotocol/server-memory@1.0.0` | low | 与 `agent_memories` 职责分离 |
| `mcp_sequential_thinking` | `@modelcontextprotocol/server-sequential-thinking@1.0.0` | low | 纯计算，可免审批 |
| `mcp_context7` | `@upstash/context7-mcp@4.3.0` | medium | 依赖境外端点，失败只应降级、不得阻塞会话 |

**保留但不可安装（共 4 条）**

| id | 原因 |
| :--- | :--- |
| `mcp_github` | 需要个人访问令牌；凭证仓库接入后再开放（`available = false`，非 PYPI） |
| `mcp_fetch` / `mcp_time` / `mcp_pandoc` | pip 通道未启用（见 10.2 第 2 条） |

**维持内置不动**：`mcp_sqlite`、`mcp_git`、`mcp_apktool`、`taixu-browser-builtin`。

**引导页默认不勾选**：`mcp-catalog` bundle 显式 `defaultSelected = false`——MCP 属于按需能力，新用户第一次进 App 不该被强制走一次 npm 下载，也不该把"镜像可达性"变成引导流程的失败点。

### 10.4 契约测试

`core/model/src/test/.../McpCatalogTest.kt` 7 条，每条对应一条硬约束：版本必须钉住、安装步骤必须把 id/包规格/校验和交给脚本、绑定的组件必须真实存在、生成的卸载脚本必须能过白名单校验、内置预设的启动声明必须与 catalog 一致、不可用条目必须给理由、探针结果必须正确映射、被排除的抢注包名不得出现。`LocalMcpProtocolTest` 额外断言"改为上游包的内置能力在安装前必须保持关闭"。

### 10.5 仍未完成（已知缺口）

- **「检查更新」入口与角标未做**：catalog 目前只声明当前 pin，升级仍需改代码。这是最初诉求「不再内置以便跟上游」的最后一公里，建议下期补：读 registry 的 `dist-tags.latest` 与 `dist.integrity`，有差异出角标，用户点击后写入新的固定版本号。
- **运行时不联网补装**：`run <id> <packageName>` 与组件探针复用本地包版本和入口核验，直接 `exec node` 已装包的入口；缺失或不匹配直接拒绝。
- **`.txplugin` 离线侧载未做**：§7 要求的离线兜底（把 tarball 打进离线包）尚未实现，首次初始化仍必须有网。
- **`mcp_apktool` 仍未随 `android-suite` 的「逆向分析与代码审计」子组件下发**：目前仍是 `android-re` 组件随附的自研脚本，`android-re` 组件描述里已写明 python3 是其运行依赖。§8 阶段一第 3 条的措辞是按"改由该子组件下发"写的，实际保持了原有归属——因为 `apktool_mcp_server.py` 本来就由 `assets/scripts/` 随 APK 下发，不需要额外的安装动作。
- **已通过编译与单测验证**（2026-10-13，`JAVA_HOME` 指向 Android Studio JBR / Gradle 9.7.0）：
  - `:core:model:test` ✅（含新增 `McpCatalogTest` 7 条契约 + `PluginBundleScriptsTest` 全绿）
  - `:harness:compileDebugKotlin` / `:tools:compileDebugKotlin` / `:feature:settings:compileDebugKotlin` ✅
  - `:harness:testDebugUnitTest --tests "*LocalMcpProtocolTest*"` ✅
  - `:app:compileDebugKotlin` ✅（需加 `-x architectureCheck`，原因见下）
  - `architectureCheck` 对本轮全部文件**零违规**（SystemPromptBuilder 617≤617、ToolCenterScreen 1212≤1212、McpSettingsScreen 1176≤1176，新增文件均 ≤400）
  - `setup_mcp_catalog.sh` 通过 `sh -n`，版本守卫实测：3 条真实规格放行、`裸包名` / `@latest` / `@*` / `@scope/pkg` / `@next` 全部拒绝
- **注意**：工作区里另有 6 个文件（`harness/mcp/McpStdioTransport.kt`、`feature/chat` 的 5 个文件）已超棘轮基线，属其他并行改动的存量问题。它们挂在 `app:preBuild → architectureCheck` 上，会让整个构建失败，需要单独处理——**与本轮改动无关**。




### 审查修复与回归

五个可安装条目统一生成内置服务预设，避免组件安装成功但没有服务行。`userToggled` 保留用户选择；`withInstalledComponents` 将当前环境观察值投影为实际启用状态，重启后的默认能力无需进入设置页即可恢复，卸载不抹掉用户选择但立即阻止运行。提示词区别已安装但关闭、未安装和状态未确认。聊天推荐的按需能力跳转工具中心，不能直接写开启偏好。

离线回归覆盖服务注册完整性、默认启用、关闭偏好、卸载阻断、环境隔离、取消与探针资源回收、提示词分支以及真实 sh/Node 下的包删除、版本错配、入口缺失和参数透传。脚本测试：`python scripts/tests/test_mcp_catalog_runtime.py`（需 Bash 和 Node，不需要 API Key 或 npm 联网）。
