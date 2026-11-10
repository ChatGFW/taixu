# 兼容性检查清单（只覆盖改造点）

**原则：只核验下表列出的改造点。未改造的代码一律不动，由上游开发者维护。**

同步上游后按顺序执行：

```sh
cd <仓库根目录>
sh Update/restore.sh          # 1. 先还原改造
sh Update/check.sh            # 2. 跑本清单的自动核验
```

---

## A. 脚本层：不写死版本号 + 架构过滤

| # | 改造点 | 核验命令 | 期望 |
|---|---|---|---|
| A1 | `pick_archive` 存在且带架构过滤 | `grep -c 'pick_archive()' assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh` | `1` |
| A2 | 归档引用不再写死版本 | `grep -E 'ARCHIVES/[a-z-]+-[0-9]' assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh` | 无输出 |
| A3 | 预发布正则排除 rc/alpha/beta | `grep -c 'canary\[0-9\]\*' assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh` | `1` |
| A4 | NDK 版本动态查询 | `grep -c 'termux-ndk/releases/latest' app/src/main/assets/scripts/setup_termux_ndk.sh` | `1` |
| A5 | Rust 版本动态查询 | `grep -c 'channel-rust-stable.toml' app/src/main/assets/scripts/setup_rust.sh` | `1` |
| A6 | JADX 版本动态查询 | `grep -c 'skylot/jadx/releases/latest' app/src/main/assets/scripts/setup_jadx.sh` | `1` |
| A7 | build-tools 跟随已装 | `grep -c 'TAIXU_BUILD_TOOLS_VERSION' app/src/main/assets/scripts/setup_android_core.sh` | `1` |
| A8 | 四处不降级保护 | `grep -c '不降级保护' assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh` | `4` |
| A9 | smali/baksmali 入口动态扫描 | `grep -c 'sort -V \| tail -n 1' app/src/main/assets/bin/smali app/src/main/assets/bin/baksmali` | 各 `1` |
| A10 | apktool 入口动态扫描 | `grep -c 'sort -V \| tail -n 1' app/src/main/assets/bin/apktool` | `1` |
| A11 | 入口脚本不写死版本号 | `cat app/src/main/assets/bin/smali app/src/main/assets/bin/baksmali app/src/main/assets/bin/apktool \| grep -E 'exec .*(smali-[0-9]\|apktool_[0-9])'` | 无输出 |

## B. 环境检查 / 修复：不再打回旧版

| # | 改造点 | 核验命令 | 期望 |
|---|---|---|---|
| B1 | 环境检查动态探测 | `grep -c '_tx_latest_dir' core/model/src/main/java/top/wkbin/taixu/core/model/DevEnvironmentSuite.kt` | `≥1` |
| B2 | 环境检查不再硬编码 35.0.0 | `grep -c 'build-tools/35.0.0' core/model/src/main/java/top/wkbin/taixu/core/model/DevEnvironmentSuite.kt` | `0` |
| B3 | 修复环境 profile 动态 | `grep -c '_tx_latest_dir' runtime/src/main/java/top/wkbin/taixu/runtime/doctor/EnvironmentRepairer.kt` | `≥1` |
| B4 | Node 仅缺失时安装 | `grep -c 'NODE_BIN' runtime/src/main/java/top/wkbin/taixu/runtime/doctor/EnvironmentRepairer.kt` | `≥2` |
| B5 | Node LTS 动态查询 | `grep -c 'nodejs.org/dist/index.json' runtime/src/main/java/top/wkbin/taixu/runtime/doctor/EnvironmentRepairer.kt` | `1` |

## C. 工具链路径解析

| # | 改造点 | 核验命令 | 期望 |
|---|---|---|---|
| C1 | 解析器文件存在 | `test -f core/model/src/main/java/top/wkbin/taixu/core/model/ToolchainPathResolver.kt && echo ok` | `ok` |
| C2 | 架构常量 | `grep -c 'TARGET_ARCH' core/model/src/main/java/top/wkbin/taixu/core/model/ToolchainPathResolver.kt` | `≥1` |

## D. 恢复通道（同步上游后必看）

| # | 检查项 | 核验命令 | 期望 |
|---|---|---|---|
| D1 | 补丁可干净应用 | `git apply --check Update/docs/fork-patches/0001-dynamic-toolchain-paths.patch` | 无输出 |
| D2 | 补丁段数 | `grep -c '^diff --git' Update/docs/fork-patches/0001-dynamic-toolchain-paths.patch` | `30` |
| D3 | restore.sh 含四态判定 | `grep -c 'CONFLICT\|ALREADY_OK' Update/restore.sh` | `≥4` |
| D4 | restore.sh 不无条件覆盖 | `grep -c 'FORCE' Update/restore.sh` | `≥1` |
| D5 | README 首选打补丁 | `grep -c '方式 1（推荐）：打补丁' Update/README.md` | `1` |

> D2 为什么是 30 不是 42：补丁只表达「生成时点改动过的 30 个文件」。差额 12 个中 10 个是补丁生成后新增/追加的
> 资产与配置（`bin/gradle`、两个 `*_qemu.sh`、两份 `taixu-android-ndk.gradle`、`taixu-build-analyze.sh`、
> 两个 MCP 服务 .py、套件 `manifest.json` 与 `gradle.properties`），另 2 个是 `McpHttpPayload.kt` 与
> `ProjectCompatibilityAnalyzer.kt`。这 12 个由 `restore.sh` 的整文件快照恢复，不依赖补丁。

## E. 语法自检（全部改造文件）

```sh
# shell 脚本
for f in $(find Update/files -name '*.sh' -o -name 'taixu-build-guard'); do sh -n "$f" || echo "FAIL $f"; done

# Kotlin 括号平衡
find Update/files -name '*.kt' | while read -r f; do
  python3 -c "
s=open('$f',encoding='utf-8').read()
b=s.count('{')-s.count('}'); p=s.count('(')-s.count(')')
print(('BAD ' if b or p else 'OK  ')+'$f')"
done
```

---

## 上游冲突时的处理

| 情况 | 做法 |
|---|---|
| 上游改了**同一行**（如 `BUILD_TOOLS_VERSION="35.0.0"`） | 保留我们的动态化写法，把上游新增逻辑合并进来 |
| 上游**新增**了我们已改造的功能 | 以我们的实现为主，吸收上游修复；在 `Progress.md` 记一笔 |
| 上游删除了我们依赖的符号 | 按报错修最小改动，不要重构 |
| 未改造的文件报错 | **不动**，交给上游；只在确实因我们的改动导致时才处理 |
