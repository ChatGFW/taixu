# 改造方法论（历史改造方法归档）

本文件归档**本 fork 相对上游 `wkbin/taixu` 的全部改造方法**，供日后同步上游后快速恢复。

---

## 一、工作流程铁律

用户原话：「每次需要修改时候需要重新拉取我仓库源码，再修改，因为我会放弃修改同步上游更新」

```sh
# 1. 拉取我的仓库最新源码（绝不使用本地旧副本 / 旧 patch 当基线）
cd /workspace/<仓库目录>
git pull origin main

# 2. 还原历史改造：先用补丁（能保住上游新改动）
#    注意：git apply --3way 对「已应用」的补丁也返回 0，不能用 `||` 当回落判据。
#    先跑补丁，再跑 restore.sh 做四态校验：CONFLICT 的才用补丁单文件合。
git apply --3way Update/docs/fork-patches/0001-dynamic-toolchain-paths.patch
sh Update/restore.sh   # 只会报 CONFLICT，不会无名覆盖（除非 FORCE=1）

# 3. 只核验改造点（未改造代码不动）
sh Update/check.sh

# 4. 改完后归一化提交推送
git add -A && git commit -m "..." && git push origin main
```

**硬禁令**：严禁向上游 `wkbin/taixu` 推送；不得添加 upstream remote。

---

## 二、改造原则

| # | 原则 | 原因 |
|---|---|---|
| 1 | 只改必须改的地方，不顺手重构 | 降低与上游冲突概率 |
| 2 | 每个改造点必须能被 `grep` 精确定位 | 同步上游后能快速核对 |
| 3 | 不新增常量、不改结构（阈值类改造） | 冲突面最小 |
| 4 | 脚本不写死版本号，一律动态获取 | 避免「新版被打回旧版」 |
| 5 | 未改造的代码报错一律不动 | 交给上游开发者维护 |
| 6 | 每次改造同步更新 MANIFEST / CHECKLIST / Progress | 可追溯 |

---

## 三、历史改造清单

### 时代一：MCP 响应体积阈值（2026-11-01，commit 57d4902）

**动机**：大结果在 MCP 层被截断/转存，而转存目录在 `context.cacheDir/taixu_mcp_spills`，
`WorkspaceFileAccess.resolve()` 只放行 `/workspace/...`，导致 **工具拿到了但内容读不出来**。
上调内联预算后大结果能走到 `ToolExecutor` 的 `.taixu-outputs/` 归档（工作区内、可读）。

**改法：只改常量值，不改结构**

| 文件 | 常量 | 原值 → 新值 |
|---|---|---|
| `McpResponseSizeLimiter.kt` | `DEFAULT_MAX_INLINE_BYTES` | 512KB → **5MB** |
| 同上 | `DEFAULT_MAX_SPILL_BYTES` | 4MB → **16MB** |
| 同上 | `DEFAULT_HARD_LIMIT_BYTES` | 8MB → **16MB** |
| 同上 | `MAX_SPILL_DIR_BYTES` | 64MB → **128MB** |
| `McpHttpTransport.kt` | `MAX_BYTES`（legacy SSE 单事件） | 4MB → **16MB** |
| `McpStdioTransport.kt` | `MAX_FRAME_CHARS` | 1MB → **5MB** |
| 同上 | `MAX_SPILL_FRAME_CHARS` | 8MB → **16MB** |

同步调整：`McpResponseSizeLimiter.kt` 头部阶梯注释（`< 5MB` / `5MB ~ 16MB` / `> 16MB`）、配额注释、
`McpHttpPayload.kt` 配套处理、`McpLargeMetadataTest.kt` 测试同步。

**核验**：`Update/check.sh` 的 F1~F4。

---

### 时代二：工具链依赖版本动态化（2026-11-06）

**动机**：环境检查/修复/自愈会把已升级的新版本**打回旧版**（硬编码 8.14.2 / 35.0.0 / android-34 / r29）。

**改法分三层**：

| 层 | 做法 |
|---|---|
| 路径判定 | 不再硬编码具体版本目录，改为 `ls -d .../*/ \| sort -V \| tail -n 1` 动态取最高 |
| 脚本安装 | `pick_archive` 通配匹配 + 架构过滤（aarch64/arm64）+ 排除预发布；版本常量从归档名/归档内容推导 |
| 在线下载 | 查官方索引（nodejs.org/dist/index.json、channel-rust-stable.toml、GitHub latest release） |

**不降级保护**：对 rust/jadx/dex-tools/flutter/NDK 等「整目录重装」的组件加
`if [ -e "$目标" ]; then skip; fi`。

**核验**：`Update/check.sh` 的 A1~A8、B1~B5、C1~C2。

---

## 四、兼容性检查方法

**核心原则：只查改造点，未改造代码一律不动。**

```sh
sh Update/check.sh      # 自动核验，分 A~C / E / F 组
```

| 组 | 覆盖 |
|---|---|
| A | 脚本层不写死版本 + 架构过滤（12 项） |
| B | 环境检查/修复不再打回旧版（5 项） |
| C | 工具链路径解析（2 项） |
| E | 语法自检（shell `sh -n` + Kotlin 括号平衡） |
| F | MCP 响应体积阈值（4 项） |

**同步上游后冲突处理优先级**：

1. 上游也改了我们改过的文件 → 以我们的改造意图为主，吸收上游修复，在 `Progress.md` 记一笔
2. 上游删了我们依赖的符号 → 按报错做最小改动，不重构
3. **未改造的文件报错 → 不动**，交给上游
