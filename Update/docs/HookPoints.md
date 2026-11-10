# HOOKPOINTS — 下手点与联动关系

回答两件事：**改某处时下手点在哪、会波及谁**。行号基于 fork 的 `main`（即本存档对应的源码树，
上游改动后会漂移），**用前先按常量名/函数名 grep 复核**。
**用前先按常量名/函数名 grep 复核**。

---

## 一、MCP 响应体积阈值（`harness/.../mcp`）

### 定义点

| 位置 | 符号 | 当前值 | 作用 |
|---|---|---|---|
| `McpResponseSizeLimiter.kt:26` | `DEFAULT_MAX_INLINE_BYTES` | 5MB | 不超过直接内联进上下文 |
| `:29` | `DEFAULT_MAX_SPILL_BYTES` | 16MB | 落盘区上限，超过只留摘要 |
| `:35` | `DEFAULT_HARD_LIMIT_BYTES` | 16MB | 非 SSE 路径熔断线 |
| `:47` | `MAX_SPILL_DIR_BYTES` | 128MB | spill 目录配额（只统计 `mcp_spill_` 前缀，按 lastModified 旧→新淘汰） |
| `:101/102`、`:251/252` | `readResponse(...)` 形参默认值 | 同上 | 改常量不必动；手改字面量必须两处同改 |
| `McpHttpTransport.kt:646` | `MAX_BYTES` | 16MB | legacy SSE 单事件字节校验（`:565` 使用） |
| `McpHttpTransport.kt:649` | `MAX_SSE_LINE_BYTES` | `DEFAULT_HARD_LIMIT_BYTES.toInt()` | **自动跟随**，勿单独改 |
| `McpStdioTransport.kt:296` | `MAX_FRAME_CHARS` | 5MB | stdio 单帧内联字符上限 |
| `McpStdioTransport.kt:298` | `MAX_SPILL_FRAME_CHARS` | 16MB | stdio 单帧落盘字符上限 |

### 四条链路（会分叉）

1. **HTTP tools/call**：`McpHttpPayload.readMcpHttpPayload` → `maxInlineBytes=DEFAULT_MAX_INLINE_BYTES`(5MB) → 超限进 `spillDirectory` → 超 `DEFAULT_MAX_SPILL_BYTES` 熔断
2. **HTTP 非 tools/call**（tools/list 等）：`maxInlineBytes = DEFAULT_MAX_SPILL_BYTES`(16MB)，**未传 maxSpillBytes**，无落盘兜底，超限直接 `IOException`
3. **stdio**：走 `MAX_FRAME_CHARS` / `MAX_SPILL_FRAME_CHARS`，与 HTTP 常量**不共享** → 改 HTTP 侧必须同步
4. **legacy SSE**：`McpHttpTransport:450/:563` 用 `readUtf8LineStrict(MAX_SSE_LINE_BYTES)`；`:455` 熔断文案由常量推导，勿写死

### spill 目录（Koin 注入，不在 mcp 包内）

`harness/.../di/harness/KoinModule.kt:295`（stdio）与 `:305`（HTTP）注入 `File(context.cacheDir, "taixu_mcp_spills")`；
`McpResponseSizeLimiter.kt:197` 是同目录兜底解析（`tmp/taixu_mcp_spills`，含写权限判定）。

**关键约束**：`cacheDir` 不在工作区内，`WorkspaceFileAccess.resolve()` 对绝对路径只放行 `/workspace/...`，
spill 出来的文件 **read 工具读不到** —— 这才是「提高内联阈值」的动机（让大结果落到可读的 `.taixu-outputs/`）。

### 下游限制（不同层，改一层不带动另一层）

`ToolExecutor.kt:376` `MAX_OUTPUT_LENGTH=64K 字符`；`ToolOutputSpillStore.kt:26-29` → `.taixu-outputs` / 7 天 / 64MB / 256 文件；read 工具单次 4MB。

### 改阈值时的同改清单

1. `McpResponseSizeLimiter.kt`：四个常量 + 头部阶梯注释 + 配额注释 + `N × <spill值>` 推算注释
2. `McpHttpPayload.kt`：顶部硬上限口径（引用 `DEFAULT_MAX_SPILL_BYTES`）
3. `McpHttpTransport.kt`：`MAX_BYTES`（`MAX_SSE_LINE_BYTES` 自动跟随）
4. `McpStdioTransport.kt`：`MAX_FRAME_CHARS` + `MAX_SPILL_FRAME_CHARS` 及注释
5. `McpLargeMetadataTest.kt`：5 条用例的数据量与断言（MB→KB 换算必须自洽）

---

## 二、工具链版本动态化链路

```
[App 进程]                                  [PRoot 沙箱]
DevEnvironmentSuite.checkCommand  ──执行──>  sh -c "<探测脚本>"
BuildEnvironmentPreflight.command ──执行──>  sh -c "<预检脚本>"
ToolchainInspector.buildProbeScript ─执行─>  sh -c "<探针脚本>"
EnvironmentRepairer 生成 profile ──写盘──>  /etc/profile.d/taixu-android.sh ──source──> GRADLE_HOME / TAIXU_AAPT2_PATH / JAVA_HOME / TAIXU_NDK_PATH
```

**唯一事实来源** `core/model/.../ToolchainPathResolver.kt`：常量（`ANDROID_SDK_ROOT` `/opt/android-sdk`、`TAIXU_TOOLCHAINS`
`/opt/taixu/toolchains`、`GRADLE_PARENT` `/opt`、`GRADLE_PREFIX` `gradle-`、`BUILTIN_JDK`、`BUILTIN_NDK`、`PRERELEASE_PATTERN`）
+ `SHELL_PRELUDE`（`_tx_latest_dir` / `_tx_ndk_version` / `_tx_ndk_dir` / `_tx_jdk_dir`）。

| 下手点 | 说明 |
|---|---|
| `SHELL_PRELUDE` | 探测算法本体：排序规则、预发布过滤、优先级 |
| `PRERELEASE_PATTERN` | 排除预发布的正则，改这里影响所有调用方 |
| `EnvironmentRepairer` 的 heredoc | profile 脚本模板（`<< 'EOF'`，`$` 不展开） |

| 改动 | 波及 |
|---|---|
| `SHELL_PRELUDE` 新增/改名函数 | `EnvironmentRepairer`（自带内联副本，**必须同步**）、`ToolchainInspector`（自动注入）、`SandboxToolchain.candidatePaths` |
| `PRERELEASE_PATTERN` | 上述全部 + `DevEnvironmentSuite` 的 `grep -Eiv` 内联副本 |
| 路径根（如换 SDK 根） | `DevEnvironmentSuite`、`SandboxToolchain`、`BuildEnvironmentPreflight`、`app/src/main/assets` 下各构建脚本 |

⚠️ **已知重复**：`EnvironmentRepairer` heredoc 与 `DevEnvironmentSuite.checkCommand` 各有一份**内联副本**
（要写进 shell 文本，无法直接引用 Kotlin 常量）。改探测逻辑时三处同改：
`ToolchainPathResolver.SHELL_PRELUDE` → `EnvironmentRepairer` heredoc → `DevEnvironmentSuite.checkCommand`。

### 有意保留的写死项（不要「顺手清理」）

| 位置 | 为什么保留 |
|---|---|
| `BuildEnvironmentPreflight` 的 `if (qemu)`（`/opt/taixu/compat/x86_64`、`android-34`） | x86_64 兼容层是固定目录，非版本化安装 |
| `DevEnvironmentSuite.purgePaths` 的 `/opt/gradle-8.14.2` | 卸载危险，故意写死单版本避免通配误删 |
| `BUILTIN_JDK` / `BUILTIN_NDK` 目录名 | 内置目录本身不含版本号（版本体现在 `java -version` / `source.properties`） |
| `setup_termux_ndk.sh` 的 `r29` + SHA256 | 本机完全无 NDK 时的内置兜底，已不强制生效 |

---

## 三、防降级通道清单（14 条，全查全改）

用户要求：「守卫 / 修复 / 更新之类功能不能把新版本依赖还原成旧版本」。

| # | 通道 | 触发时机 | 处置 |
|---|---|---|---|
| 1 | `taixu-build-guard:7` 旧默认值污染 `requested_gradle_home` | 每次构建 | 去掉默认值 |
| 2 | `taixu-build-guard` aapt2 兜底 35.0.0 | 每次构建 | 动态探测 |
| 3 | `taixu-build.sh` GRADLE_HOME / aapt2 / android-34 校验 | 每次构建（权威脚本） | 动态探测 |
| 4 | `build_android.sh` REQUIRED_BUILD_TOOLS + aapt2 白名单 | Android 构建 | 动态探测 + 白名单通配 |
| 5 | `build_flutter.sh` d8 / aapt2 / 白名单 / platform / wrapper | Flutter 构建 | 动态探测 |
| 6 | `template_gradlew.sh` 生成的 gradlew | 新项目首次构建 | `TX_GRADLE_DIR` |
| 7 | `setup_flutter.sh` platform 校验 | Flutter 装配 | 动态探测 |
| 8 | `setup_termux_ndk.sh` 严格版本校验 → 重下 r29 | NDK provision | 跟随已有 ARM64 NDK |
| 9 | `install-android-suite.sh` 无条件覆盖 NDK/Rust/JADX/dex-tools/Flutter | 套件安装、修复 | 已有则跳过 |
| 10 | `verify-android-suite.sh` 固定版本校验 → 误判 → 自愈 | 套件校验 | 动态探测 |
| 11 | `EnvironmentRepairer` 重写 profile 写旧值 | 环境自愈 | 动态探测 |
| 12 | `WorkshopSettingsViewModel` 默认 gradle / aapt2 路径 | 设置页重置 | 默认置空 |
| 13 | `bin/gradle` 包装器 `GRADLE_HOME` 默认 8.14.2 | 直接调 gradle | 取 `/opt` 下最高正式版 |
| 14 | `taixu-build-analyze.sh` compileSdk 上限硬编码 34 | 构建分析 | 取本地最高 platform |

**共性根因**：这些脚本都由 `syncAssetsToDistro` 写入 `/opt/taixu/`，改源码后需重新编译 APK 才生效；
在源码未更新的旧 APK 上，只能靠 `/etc/profile.d/taixu-android.sh` 存在来避免被重写。

---

## 四、核验口径（改完必跑）

1. 语法：抽出的 shell 跑 `sh -n`；Kotlin 括号平衡（`check.sh` E 组自动做）
2. 探测正确性：实跑比对 `gradle --version` / `ls build-tools` / NDK `source.properties`
3. 零残留扫描（Kotlin/脚本侧手工写死）：
   ```sh
   rg -n "platforms/android-[0-9]+|build-tools/[0-9]+\.|gradle-[0-9]+\.[0-9]+|r29" \
      app/src/main/assets assets/plugins --glob '!*.md' | \
      grep -vE "qemu|setup_android_core|install-android-suite.sh:(12[0-9]|14[0-9]|19[0-9]|2[0-1][0-9])|uninstall-"
   ```
   应为空。
4. 自动化：`sh Update/check.sh` —— 39 项（A 脚本层 / B 环境检查 / C 路径解析 / D1–D10 存档自洽 / E 语法 / F MCP 阈值）
5. 重放：`sh Update/restore.sh`（逐文件判定，冲突打印两边 hunk，退出码 2）+ `git apply --3way Update/docs/fork-patches/0001-dynamic-toolchain-paths.patch`（52 段全量）

## 工具链目录（合并后）

- 决策：`StorageManager` 的 `environment` 分类不再遍历 `toolchains`，只列 `runtimes/`、`compat/`、镜像内 `/opt`；
  `taixuToolsDir` 及其子目录统一归 `plugins`。原因：源码已统一以 `tools` 为唯一工具链根，
  再按旧根遍历会让同一目录在两处重复出现、环境分类被工具链条目占满。
- 约束：`StorageManager.kt` 受行数棘轮约束（`.architecture-baseline.json` 记 422 行，只许下调），
  `:architectureCheck` 会在 CI 拦下超限；改写该文件时不能靠加注释解释，说明写在这里。

---

## 五、aapt2 / zipalign 动态入口与制品替换

### 为什么不再需要兼容包装层

历史原因：`build-tools` 里的 aapt2 若是 2.19 一类旧构建，`optimize --resource-path-shortening-map=` 会以未知参数静默 `rc=0` 且不写产物，
故曾用 `/opt/taixu/tools/aapt2-wrap/aapt2` 把该参数剥掉再补一个空映射文件，并由
`/root/.gradle/gradle.properties` 的 `android.aapt2FromMavenOverride` 指向包装层。

现役 aapt2 为 **2.20**，原生识别该参数（实测能正常产出映射文件与压缩包），包装层已无必要且会把映射退化成空文件。
包装层已删除（备份 `/root/.taixu/backup/aapt2-wrap-removed-*`），`aapt2FromMavenOverride` 改为直接指向 ARM64 制品。

### 动态入口（跟随最新 build-tools）

- 软链：`/opt/taixu/tools/android-sdk/build-tools/0-current -> <最新版本目录>`（`/opt/android-sdk` 为其别名，两者等价）。
- 维护者：`/etc/profile.d/zz-taixu-pin.sh` 的 aapt2 段，每次加载幂等重建软链。

```sh
_tx_bt=$(_taixu_latest '/opt/android-sdk/build-tools/*')
if [ -n "$_tx_bt" ] && [ -x "$_tx_bt/aapt2" ]; then
    # 维护 current 软链，使 gradle.properties 等静态入口自动跟随最新 build-tools
    _tx_btlink=/opt/android-sdk/build-tools/0-current
    _tx_cur=$(basename "$(readlink "$_tx_btlink" 2>/dev/null)" 2>/dev/null)
    if [ "$_tx_cur" != "$(basename "$_tx_bt")" ]; then
        ln -sfn "$(basename "$_tx_bt")" "$_tx_btlink" 2>/dev/null
    fi
    TAIXU_AAPT2_PATH="$_tx_btlink/aapt2"
    export TAIXU_AAPT2_PATH
fi
```

**必须用 `0-current` 而不是 `current`**：`_taixu_latest` 等探测用 `sort -V` 取尾部，字母名会排在数字版本之后而被当成“最新版本”，
导致软链指向自身形成自指环（`current -> current`，读文件报 `Too many levels of symbolic links`）。
`0-` 前缀使其在版本序中恒排最前，`build_android.sh:147` 与 `taixu-android.sh:10` 的探测仍正确返回真实版本目录。

### 两处必须同源

`/root/.gradle/gradle.properties` 的 `android.aapt2FromMavenOverride` 与 `TAIXU_AAPT2_PATH` 必须取同一路径，
因为 `setup_android_core.sh` 会断言 `grep -Fqx "android.aapt2FromMavenOverride=$TAIXU_AAPT2_PATH"`；不一致时安装自检直接 MISS。
`build_android.sh` 的白名单 `/opt/android-sdk/build-tools/*/aapt2` 也能匹配 `0-current/aapt2`，并会再校验架构字节 `b700` 与 `aapt2 version` 可执行。

### 制品替换与持久性

| 工具 | 实体路径 | 重装/更新是否被还原 |
| :--- | :--- | :--- |
| aapt2 | `/opt/taixu/tools/aapt2-dl/aapt2` | 否（该目录非插件安装产物） |
| zipalign | `/opt/taixu/tools/android/sdk-tools/artifacts/db1cea2c…/build-tools/zipalign` | **是**，重装 Android 套件会按配方还原，需重放 |

替换前务必备份到 `/root/.taixu/backup/bin-replace-<时间戳>/`；两个二进制均须为 aarch64 静态 ELF（架构字节 18 偏移处 `183`，即 `b700` 小端）。

### 核验口径

```sh
A=/opt/android-sdk/build-tools/0-current/aapt2
od -An -tu2 -j18 -N2 $A | tr -d ' '      # 期望 183
$A version                              # 期望 aapt 2.20 直接返回
/opt/android-sdk/build-tools/0-current/zipalign -c 4 <未对齐.apk>; echo $?   # 期望 1（能检出）
/opt/android-sdk/build-tools/0-current/zipalign -c 4 <已对齐.apk>; echo $?   # 期望 0
```

注意：`zipalign -c … | head` 会把 `$?` 变成 `head` 的退出码（恒 0），判定未对齐必须不接管道。
