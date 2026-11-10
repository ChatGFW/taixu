# PROGRESS — 改动历史档

记录本 fork 相对上游 `wkbin/taixu` 的改动：改了什么、为什么、怎么核验。
硬约束：新改动一律从 `origin/main` 最新提交重新拉取，不续用历史存档。
清单见 `MANIFEST.txt`，下手点与联动见 `HookPoints.md`，踩坑见 `PITFALLS.md`。

---

## 2026-11-01 · MCP 响应体积阈值整体上调一档（57d4902）

**动机**：大结果在 MCP 层被截断/转存到 `context.cacheDir/taixu_mcp_spills`，而 `WorkspaceFileAccess.resolve()`
只放行 `/workspace/...` 绝对路径，cacheDir 下的 spill 文件 `read` 读不到。上调内联预算后，大结果能走到
`ToolExecutor` 的 `.taixu-outputs/` 归档（工作区内、可读）。

| 文件 | 常量 | 原值 → 新值 |
|---|---|---|
| `McpResponseSizeLimiter.kt` | `DEFAULT_MAX_INLINE_BYTES` | 512KB → 5MB |
| 同上 | `DEFAULT_MAX_SPILL_BYTES` | 4MB → 16MB |
| 同上 | `DEFAULT_HARD_LIMIT_BYTES` | 8MB → 16MB |
| 同上 | `MAX_SPILL_DIR_BYTES` | 64MB → 128MB |
| `McpHttpTransport.kt` | `MAX_BYTES`（legacy SSE） | 4MB → 16MB |
| `McpStdioTransport.kt` | `MAX_FRAME_CHARS` | 1MB → 5MB |
| 同上 | `MAX_SPILL_FRAME_CHARS` | 8MB → 16MB |

同步调整头部阶梯注释与 `McpLargeMetadataTest.kt` 5 条用例的数据量/断言（`16384KB` = `16777216/1024`，自洽）。
`MAX_SSE_LINE_BYTES` 自动跟随 `DEFAULT_HARD_LIMIT_BYTES`，未改。
`MAX_FRAME_CHARS` 1MB→5MB 是主动对齐：避免「HTTP 能过、stdio 卡旧值」的分叉。

**核验**：旧阈值（512KB/4MB/8MB/64MB）词边界扫描 0 命中（`64 * 1024` 是 `ID_PROBE_CHARS`，无关）；远程 blob sha 与本地一致。
**有意未做**：未编译未安装；未动 `ToolExecutor.MAX_OUTPUT_LENGTH=64K`、`ToolOutputSpillStore`（64MB/256 文件/7 天）、read 4MB。
**遗留观察点**：`McpHttpPayload` 非 `tools/call` 路径内联预算 = `DEFAULT_MAX_SPILL_BYTES`（16MB），该路径无落盘兜底，超限直接 `IOException`。

---

## 2026-11-06 · 工具链版本动态化：不写死、不降级

**动机（源码级定位）**：上游把版本硬编码在多处。用户升级 Gradle / build-tools / platform / NDK 后，
`DevEnvironmentSuite.checkCommand` 仍按 `android-34`、`build-tools/35.0.0`、`gradle-8.14.2` 判定 → 报「缺组件」
→ 触发自愈 → `EnvironmentRepairer` 重写 `/etc/profile.d/taixu-android.sh` → 写回旧版本路径，**新装依赖被还原成旧值**。
用户原话：「所有依赖版本不要硬写死，要根据名字动态检测」。

**原则**
- 探测根目录用太墟原布局：Gradle `/opt/gradle-<ver>`；SDK `/opt/android-sdk`（`build-tools/*`、`platforms/android-*`、`ndk/*`）；
  JDK `/opt/taixu/toolchains/android/jdk` → `/opt/taixu/toolchains/jdk<ver>`；NDK `TAIXU_NDK_PATH` → `ANDROID_NDK_HOME` → `$ANDROID_HOME/ndk/*` → `/opt/taixu/toolchains/android/ndk`
- 只认正式版：目录名含 `rc`/`alpha`/`beta`/`preview`/`snapshot`/`canary` 跳过；版本比较 `sort -V` 取最高
- 解析逻辑写成 **shell 函数**而非 Kotlin：App 进程看不到 PRoot 沙箱内的 `/opt`，只有注入沙箱的脚本才能真探测

**三层改法**：路径判定动态取最高 / 脚本安装统一走 `pick_archive <目录> <通配> [auto|require|any]`（find + 排除预发布 + 架构过滤 + `sort -V`，版本常量从归档名推导）/ 在线下载查官方索引，失败才回落内置值。

唯一事实来源 `core/model/.../ToolchainPathResolver.kt`：常量（根路径/前缀/预发布正则）+ `SHELL_PRELUDE`
（`_tx_latest_dir` / `_tx_ndk_version` / `_tx_ndk_dir` / `_tx_jdk_dir`）。

**不降级**：NDK / Rust / JADX / dex-tools / Flutter 等「整目录重装」组件统一 `if [ -e "$目标" ]; then skip; fi`；
`setup_termux_ndk.sh` 改为「SDK 下已有可用 ARM64 NDK 就跟随」；`EnvironmentRepairer` 的 Node 段只在
`command -v node` 完全找不到时安装。12 条通道见 `HookPoints.md`「防降级通道清单」，此处不重复以免两份漂移。

**有意保留的写死项**（不要「顺手清理」）：QEMU 兼容根 `/opt/taixu/compat/x86_64`（固定目录非版本化安装）；
`platforms/android-34` 安装目标（只写自己那版）；`uninstall-android-suite.sh` 的 `rm -rf /opt/gradle-8.14.2`（防通配误删）；
`setup_termux_ndk.sh` 的 `NDK_VERSION / r29 / SHA256`（本机完全无 NDK 时的兜底定义）。

**核验（实测，非编译）**：抽出的 profile 逻辑在沙箱实跑 → `GRADLE_HOME=/opt/gradle-9.8.2`、
`BUILD_TOOLS=/opt/android-sdk/build-tools/37.0.0`、`NDK=30.0.16248370`、`test -x aapt2` 通过；
动态查询实测 Node LTS `v26.13.0`、Rust `1.99.1`、JADX `v1.5.6`；
架构过滤实测：`gradle-10.0-rc1-bin.zip` 被排除取 9.8.2、`ninja-linux-x86_64.zip` 返回空、纯 Java 制品走白名单命中。

**前提**：`app/src/main/assets` 与 `assets/plugins` 的资产由 `syncAssetsToDistro` 写入 `/opt/taixu/`，
脚本类改动必须重新编译 APK 才会同步到沙箱。

---

## 2026-11-08 · 工具链下拉实体去重 + 内置 JDK 标签修正

**现象**：设置 → 工作坊设置页 Java 下拉 5 条、NDK 2 条，多项是同一物理目录的别名；内置 JDK 标签写 17，实体实际是 Temurin 21。
**根因**：`/usr/lib/jvm/java-1.21.0-openjdk-arm64` 是 `java-21-openjdk-arm64` 的别名；
`/opt/android-sdk/ndk/<ver>` 是 `setup_termux_ndk.sh` 给 AGP 建的 `toolchains/ndk/current` 同实体软链。两侧都不做实体判等，
宿主扫描还放进没有 `bin/java` 的空壳目录。

| 位置（`WorkshopSettingsViewModel.kt`，429 → 439 行） | 改动 |
|---|---|
| 探针 JDK（127） | 显式列内置 JDK 与 `/opt/jdk*`；`/usr/lib/jvm` 用 `find ... -exec test -x {}/bin/java \; -exec readlink -f {} +` 只留有 `bin/java` 的实体名，`sort -u` 合并 |
| 探针 NDK（129） | `ls -d .../toolchains/ndk/current`；`find -L /opt/android-sdk/ndk -maxdepth 1 -mindepth 1 ! -samefile .../current` 排除同实体别名 |
| 宿主扫描 JDK（211–214） | `listFiles → map { canonicalFile } → filter { bin/java 存在 } → distinctBy { path }` |
| 宿主扫描 NDK（224–229） | 按 `canonicalPath` 判等跳过别名；canonical 解析失败时保留条目（不误删） |
| 标签（172 / 248） | `Adoptium OpenJDK 17 (内置)` → `… 21 (内置)` |

**实测**：JDK 5 条 → 2 条（`/opt/taixu/toolchains/android/jdk` + `/usr/lib/jvm/java-21-openjdk-arm64`）；NDK 2 条 → 1 条。
**约束**：按物理实体去重而非路径字符串（两个软链都不能删，只在渲染层隐藏其一）；
raw string 内不能用单引号（提前闭合外层 `sh -c '...'`）也不能有裸 `$标识符`（Kotlin 模板）；
`BuildGuardAssetTest.kt` / `BuildEnvironmentPreflightTest.kt` 断言 `/opt/taixu/toolchains/ndk/current` 字面量，不可删。
**静态核验**：439 行 = 基线、`{}` 128/128 `()` 322/322 `[]` 1/1、`check-architecture-baseline.py` 0 violations；未编译未安装。

---

## 2026-11-08 晚 · 存档机制：同步上游后「按改动点重放」而不是整文件覆盖

**问题**：旧 `restore.sh` 用 `cp -f` 整文件覆盖快照，上游改过同一文件时会把作者的新行静默抹掉（实测可复现）。
**现在**：
- 基线双来源——`FORK_BASE` 提交（`745c82d9`）；对象不存在时自动退化到 `Update/base/`（38 份基线文件快照）
- 补丁补齐到 **42 段**，与 `Update/files` 42 个文件一一对应（含 4 个我方新增文件，清单 `BASE-ABSENT.txt`）
- `restore.sh` 逐文件判定：`ALREADY_OK` 跳过 / `SAFE` 覆盖 / `CONFLICT` **拒绝覆盖**并打印「上游相对基线的改动 + 我方 hunk」
- 退出码 `0` = 就绪，`2` = 有冲突待处理
- `check.sh` D 组扩到 D1–D10（新增 base+新增=files 总数、补丁文件集=快照文件集等自洽断言），**33 项全绿**
**实测（模拟上游改 `taixu-build.sh`）**：该文件判 CONFLICT 未被覆盖，上游 hotfix 行保留；其余 37 个文件安全重放；
`git apply --3way` 在该文件留下 `ours/theirs` 冲突块，双方改动都在，等人取舍。

---

## 2026-11-09 · 工具链根收敛：toolchains 并入 tools

**问题**：`/opt/taixu` 下 toolchains 与 tools 两套工具链根并存，源码里多处按根分支，
排查、备份与卸载审计都要记两套；磁盘上还留着无引用的旧版本残留。

**现在**：
- `tools` = 唯一工具链根，源码路径统一指向 `tools`
- 设备内引用面同步收敛到 `tools`，旧路径引用清零
- 源码改造文件全部走 `tools` 路径；`StorageManager` 合并后统一由 plugins 分类列出，
  `environment` 不再遍历 toolchains（否则同一目录在两处重复出现，环境分类被工具链条目占满）
- 保留 `toolchains/llvm`：NDK 内部真实布局，物理路径不可动
- 移除旧版本残留（旧 node 二进制与头文件、旧 aapt2 备份）；`/usr/local` 本身软链指向
  usr-local，属活安装，不在清理范围

**实测**：补丁 / files / base 三方自洽，`check.sh` 全绿，`restore.sh` 全 ALREADY_OK；
java/javac、rustc、go、gradle、aapt2、adb、node 冒烟全过。未编译未安装。
