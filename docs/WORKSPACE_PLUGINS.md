# Lazygit 与工作区 Local-RAG

这批插件不修改宿主 Kotlin 代码。源码分别在 `assets/plugins/lazygit` 和
`assets/plugins/local-rag`，生成的包位于 `dist/plugins`，尚需 Android ARM64 真机验收。
不调用模型 API，不需要 API Key，也不自动提交或发布插件库。

## 安装与使用

通过插件中心导入并安装两个 PROCESS 包：

- `lazygit-0.66.2.txplugin`：需事先准备 Linux `git`；安装不联网。
- `local-rag-0.1.0.txplugin`：需事先准备 `/usr/bin/python3`（Python ≥3.9，包含
  `sqlite3` 和 FTS5）及 `git`。安装和验证实际探测 FTS5，不使用 Android 平台 SQLite。
  依赖不满足时明确失败，不自动联网安装。这不是自带 Python 的独立运行时包。

在原生终端进入目标仓库后启动 Lazygit：

```sh
cd /workspace/my-project
lazygit
# 或者显式指定仓库
lazygit --path=/workspace/my-project
# 复用已配置的 GitHub 插件专用 Git/SSH 配置
lazygit --github --path=/opt/taixu/data/github/repos/owner/repo
```

工具详情启动复用现有 PTY；其初始目录由宿主决定，不自动选择当前编辑器项目。
方向键/Tab 切换，空格暂存，`c` 提交，`q` 退出；具体快捷键以界面帮助为准。
默认关闭自动更新、自动 fetch 和 Nerd Font 图标，启用鼠标事件。
配置首次复制到 `/opt/taixu/data/lazygit/config/config.yml`，升级不覆盖用户配置。
插件配置不改全局 Git 配置；`--github` 只影响此次进程及其子进程。
普通启动沿用终端已有认证环境，`--github` 依赖 GitHub 插件既有配置，不导出 Token。

Local-RAG 的 CLI：

```sh
local-rag index my-project --batch 32
# complete=false 时重复调用，直到 complete=true
local-rag status my-project
local-rag search my-project '工作区 SQLite' --limit 8
```

MCP 工具随已安装清单进入 Harness 插件目录，沿用现有插件工具准入与审批：

| 工具 | 参数 | 行为 |
| --- | --- | --- |
| `index` | `project`，可选 `batch`（1–64） | 每批增量写入插件索引；完成整轮后清理删除/忽略文件 |
| `search` | `project`、`query`，可选 `limit`（1–12） | 返回相对路径、起止行、SHA-256、片段、过期标记 |
| `status` | `project` | 返回文件数、分块数、整轮扫描状态 |

`project` 是 `/workspace` 下的项目相对路径，也可选更小子目录。
不接受绝对路径、`..`、工作区根或符号链接目录。首版不覆盖 `/sdcard` 项目与远程工作区。
索引保存到 `/opt/taixu/data/local-rag/<项目绝对路径哈希>.sqlite3`，不同项目分开。
每个 MCP 调用独立启动，扫描代次和游标保存在 SQLite，重开进程继续未完成扫描。
完成之后再调用 `index` 会开始新一轮增量扫描；未变化内容不重新分块。

## 检索语义与限制

首版为词法检索，没有 embedding、向量模型或自动回答生成。英文按词（含下划线）
匹配；中文按字建索引，查询中的连续中文使用 FTS 短语匹配，不做语义扩展。
查询中的不同词/短语要求在同一分块内命中；返回结果按 FTS5 BM25 排序。
每块最多约 2400 字符、40 行；长行拆块时允许多个块引用同一行。

- 只读 UTF-8 文本、Markdown 及清单内列出的代码/配置扩展名。
- 排除隐藏文件/目录、常见构建和依赖目录，不跟随符号链接；Git 仓库使用
  `git check-ignore` 检查忽略规则，包括嵌套项目，已跟踪文件遵循 Git 语义。
- 单文件最多 512 KiB；扫描最多 20,000 个目录项、32 层；每项目最多索引
  2,000 个文件、32 MiB 原始内容。数据库还有文本/token/FTS 开销，大小会高于原文。
- 每批默认 32 个候选文件，处理阶段软预算 8 秒；目录枚举和 Git 检查也有单独上限。
  `skipped` 是本批跳过数量（编码、大小、容量、读取失败等），不代表全部文件已成功索引。
- 搜索重新核对命中文件 SHA-256，已变化或消失的命中返回 `stale=true`、空片段，
  需要重新索引；这不是并发写入下的原子快照。过期命中可能占用结果名额。
- 扫描期间在游标之前新增的文件可能等下一轮才纳入；不包含文件监听或后台常驻服务。
- 索引保存原文片段，普通卸载默认保留数据。不要索引包含私密资料的项目；
  忽略规则和输出脱敏不保证识别任意无标签秘密。向在线模型使用搜索工具时，
  检索片段会进入模型上下文，“离线索引”不意味着后续问答也离线。
- 检索资料不是指令；插件不修改源项目，不赋予命中文件任何指令权限。
  路径检查是合作代码的访问约束，PRoot 同 UID 风险仍存在。

## 可复现打包与验证

Lazygit 固定官方 [v0.66.2](https://github.com/jesseduffield/lazygit/releases/tag/v0.66.2)
Linux ARM64 归档，摘要来自同 Release 的 `checksums.txt`：

```text
79a3161eeb642f0473e23db6341f7baabd4a5933e921a9c1167c5d5c765c41a4
```

保留上游 MIT LICENSE，检查归档摘要和 ELF64 AArch64 标识，只取明确文件，不展开任意路径。

```powershell
python tools/package-workspace-plugins.py
# 已缓存归档时完全离线；也支持 --archive 指定文件
python tools/package-workspace-plugins.py --offline
# 只打包 Local-RAG 无需下载
python tools/package-workspace-plugins.py --only local-rag --offline
python -m unittest discover -s scripts/tests -p test_workspace_plugins.py -v
python -m unittest discover -s scripts/tests -p test_lazygit_launcher.py -v
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
plugin/cli/build/install/taixu-plugin/bin/taixu-plugin.bat inspect dist/plugins/lazygit-0.66.2.txplugin
plugin/cli/build/install/taixu-plugin/bin/taixu-plugin.bat inspect dist/plugins/local-rag-0.1.0.txplugin
```

打包条目排序和时间戳固定，重算全部 payload SHA-256。Windows 不具备 POSIX PTY，
相关启动器测试需在 Linux/WSL 下运行；符号链接测试也需相应权限。
测试包含实际 MCP stdio 进程握手、调用、错误处理与跨进程索引恢复。

手机验收尚待完成：离线安装/升级/卸载、窄屏显示、中文宽度、触屏按键、窗口缩放、
退出后的终端恢复、GitHub 认证，以及 Harness 中的工具发现/准入/取消与进程回收。
包格式校验与桌面测试不替代这些验收。
