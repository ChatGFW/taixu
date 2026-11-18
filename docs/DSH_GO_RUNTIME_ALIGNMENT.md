# DSH Go 运行时对齐评估

> 评估对象：`ccch1mneyyy/dsh-go` @ `de8979d`（浅克隆，仅检出 `runtime/ docs/ tooling/ app/android/`）
> 对照对象：太墟 `main` @ `887572ee`
> 方法：只读源码、构建脚本与设计文档；未在设备上运行任一应用。本文所有"未验证"项不得当作结论使用。

## 一页结论

| 能力 | 太墟现状 | 结论 |
| --- | --- | --- |
| 发行版随 APK 分发（首装零网络） | 不内置，首装需网络或手动导入归档 | **值得抄，唯一真实缺口** |
| 构建输入全量 pin + 校验下载 | 无本地构建链；rootfs 走 OCI digest | **随上一条一起抄** |
| 基础层/引擎层分层 + 事务升级 | 引擎是 APK 内 Kotlin，没有独立引擎层 | 不抄，无对应物 |
| 上游 npm 树补丁治理（192 个补丁） | 没有 Node 引擎树 | 不抄，无对应物 |
| PRoot 免 root + loader（`nativeLibraryDir` 取可执行） | 已具备（`libproot.so` / `libproot-loader.so`） | 已具备 |
| `libtalloc` / `libandroid-shmem` 依赖处理 | 已具备（运行时复制为 `libtalloc.so.2`） | 已具备，手法不同但等价 |
| Direct 启动（绕过 PRoot 直接跑 Node） | 太墟在 PRoot 里跑任意发行版命令 | 不抄，架构不兼容 |
| boot-cache（缓存 PRoot 下的路径查找） | 未做；太墟在 guest 内跑 Node（MCP/npm） | 可选实验，先测收益 |
| 本地控制面 token → cookie 绑定 | 已有 `ToolServiceAuthentication` / `WebChatAuth` | 已具备 |
| rootfs 安装 / 更新 / 回滚 | 已有 staging、previous、pending marker、recover | 已具备，**不要替换** |

一句话：dsh-go 的运行时是"给一个 Node 引擎做定制发行版"，太墟是"给任意发行版做通用运行时"。两者的重心相反，**只有"把发行版做成可复现内置包"这一条能平移**。

## 为什么只有第一条能抄

### dsh-go 的做法

`runtime/build-runtime.sh`（31 KB）＋ `runtime/lib/rtbuild.py`（93 KB）在 x86_64 Linux 主机上离线交叉组装：

- Ubuntu Base 24.04.5 + Node 24.22.1 + DSH npm 树 + pnpm + CPython（python-build-standalone）；
- Termux 的 `proot` / `libtalloc` / `libandroid-shmem` deb，解包后放进 `jniLibs`；
- 所有输入 sha256 pin，未 pin 的版本回落到上游 checksum 元数据；npm 树另有 `package-lock.json` 与 pnpm tarball integrity 双锁；
- `runtimeVersion` 是"配方 + 全部输入摘要"的 12 位 hash，任何输入变化版本即变；
- 固定 `SOURCE_DATE_EPOCH`、`xz -6`（8 MiB 字典，4 线程），产物字节可复现；
- 产物 `rootfs-<abi>.tar.xz` + `manifest.json`（含分层信息与启动模板）打进 APK。

### 太墟的做法

`RootfsInstaller` 已是一条完整且更成熟的链：

- `installOci`：staging → 解层 → `RootfsValidator.validate` → `preserveUserDirectories` → `replaceRootfs` → 写 installed marker；
- `updateOci`：保留 `rootfs.previous` + 写 `rootfs.update.pending`，失败或取消走 `rollbackPendingUpdate`，启动时 `recoverInterruptedUpdate`；
- `restoreFromCacheOrPull`：层已缓存时可零流量离线恢复；
- `importArchive`：支持 tar / tar.gz / tar.xz / tar.zst 本地导入，拒绝 ZIP（保留不了权限与符号链接）。

这条链覆盖了 dsh-go 的"分层 + 事务升级"能提供的全部保障，而且多了多发行版与 OCI digest 校验。**拿 dsh-go 的两层 schema 来替换它是净损失。**

### 差异点在哪

dsh-go 把发行版**打进 APK**，首装不需要网络；太墟 `README` 明确写"RootFS 不内置 APK，首次安装需要网络连接"。这才是缺口，且是产品口径问题，不是技术问题。

## 要抄的部分：内置可复现 rootfs

### 目标

产出一个可在无网络、弱网或 registry 不可达时安装的默认发行版包，同时保留 OCI 路径为默认来源。

### 复用清单（按文件）

| dsh-go 文件 | 复用内容 | 太墟落点 |
| --- | --- | --- |
| `runtime/build-runtime.sh` 的 `fetch` / `pin_of` / `sha256` | 校验缓存 + 多镜像回退 + 失败不落半份 | `tools/bundled-rootfs/lib/fetch.sh` |
| 同文件的 Ubuntu base 与 deb 闭包解析（gpgv → InRelease → Packages.xz → deb） | 信任链与 `runtime/locks/*.json` 锁定 | `tools/bundled-rootfs/lib/ubuntu.sh` |
| 同文件的 `XZ_PRESET` / `SOURCE_DATE_EPOCH` / `xz -T` 说明 | 手机内存友好的压缩参数与可复现字节 | 同上 |
| `runtime/lib/rtbuild.py` 的 `assemble` | 只取"归一化文件树 + 权限/链接 + tar 写出 + 报告"部分 | `tools/bundled-rootfs/lib/assemble.py` |
| `runtimeVersion` 的输入摘要算法 | 内置包的版本身份 | `manifest.json` 的 `assetId` |

**不要照搬**：DSH 补丁应用、pnpm/npm 树安装、bridge 注入、`/opt/dsh-*` 布局、`ENGINE_PATHS` 的 15 路径事务、`schema 2` 分层字段。太墟的引擎在 APK 里，这些都没有消费者。

### 消费端改动

1. `app/src/main/assets/runtime/` 增加 `rootfs-arm64.tar.xz` 与 `manifest.json`（是否入包由构建开关决定，默认关闭）；
2. `RootfsInstaller` 增加 `installBundled()`：把 asset 复制到 cache 后走已有 `importArchive` 同一套 staging/validate/replace 流程，不新写第二条安装链；
3. 首装向导：内置包可用时优先，失败或无内置包时回退 `installOci`；
4. 版本比较沿用 `assetId`，与 OCI digest 分开记录，不混用两个身份。

### 必须先拍板的两件事

1. **APK 体积换离线首装**是否接受。内置包 xz 后的实际体积、首装解压耗时必须在目标机型上实测，本文不给估算数字。
2. 内置默认发行版选哪个。太墟默认 Ubuntu 24.04，与 dsh-go 一致，可直接对齐；但多发行版共存时"内置"只应有一个，不能每个发行版都内置。

## 可选实验：Node boot-cache

`runtime/boot/boot-cache.mjs` 用 `--import` 预加载，把 PRoot 下的 `lstat` / `realpath` / `package.json` 读取按路径分量缓存，只在启动阶段生效。

太墟在 guest 内确实跑 Node（MCP STDIO、npm/pnpm）。但：

- 收益取决于启动期路径查找占比，dsh-go 自己的注释也只声称"启动阶段"有效，没有给倍数；
- 需要给每次 Node 启动注入 shim，属于跨所有 Node 调用点的改动；
- 太墟 `docs/RUNTIME_BENCHMARKS.md` 已写了生产门槛（多内核、真实负载、一致收益）。**先按那份门槛测，再决定是否接。**

## 许可证

- 太墟：GPL-3.0；dsh-go：AGPL-3.0，`sdk/` 与 `examples/` 为 MIT。
- GPLv3 §13 允许与 AGPLv3 组合，组合作品按 GPLv3 传播；来自 dsh-go 的部分仍受 AGPL 第 13 条约束（网络提供服务时须可获取对应源码）。
- "不商用"不构成豁免：AGPL 对非商业使用同样生效。太墟本身已公开源码，实际义务主要是**保留版权、标注修改、在 `docs/THIRD_PARTY_LICENSES.md` 增条目**。
- 作者在 README 中提出的"改造前联系、标注主仓库、禁止未授权套皮"属于协作请求；其中**名称、标识、Deepy、鲸娘不在 AGPL 授权范围内**，代码里不要带入这些素材。

## 落地顺序

1. 拍板体积与默认发行版（见上）。
2. `tools/bundled-rootfs/`：先只做 Ubuntu Base + 最小 deb 闭包，产出 tar.xz + manifest，能在 Linux 主机复现两次字节一致。
3. CI 增加一条"内置包 manifest 与 APK asset 摘要一致"的检查，不替代真机安装验证。
4. `RootfsInstaller.installBundled()` + 首装向导分支，失败回退 OCI。
5. 真机验证：内置首装、内置失败回退、已有 rootfs 升级、离线层恢复四条路径。

## 未验证边界

- 未在设备上运行 dsh-go 或太墟，未测任何启动/解压耗时；
- 未实测内置包体积与解压峰值内存；
- dsh-go 的 Android 安装与升级路径只读代码，未执行；
- `runtime/patches` 实际 192 个 `.diff`、`series` 192 行，与其架构文档写的 139 不一致，本文以仓库实际文件为准。

## 落地记录（2026-11-17）

已按上面的结论实现构建侧工具 `tools/bundled-rootfs/`（未接入 APK，未改任何现有安装路径）。

| 项 | 结果 |
|---|---|
| 构建环境 | 云主机 Ubuntu 22.04.5，GNU tar 1.34，XZ Utils 5.2.5 |
| 产物 | `rootfs-arm64.tar.xz` 17,937,512 字节；3415 条目；解压 103,239,680 字节 |
| 归档 SHA-256 | `2aaa8f089615e8d1cb32336f03e383aec53e62d795323293ce376010eadf27a7` |
| 可复现 | 两次独立构建字节一致 |
| 结构校验 | 通过：`/bin/bash` AArch64，`PT_INTERP` `/lib/ld-linux-aarch64.so.1` 可解析到 `/usr/lib/aarch64-linux-gnu/...` |

构建过程中发现一处**真实的兼容性问题**：Ubuntu Base 的 `/etc/os-release` 是指向 `../usr/lib/os-release` 的相对符号链接，而 `RootfsValidator.resolveGuestPath` 对链接目标里的 `..` 直接抛错。也就是说，未经归一化的 Ubuntu Base tar 包在太墟上会在 PRoot 启动前就校验失败。构建脚本现已把该链接实体化；`lib/manifest.py validate` 复刻了 `RootfsValidator` 的判定，用来在主机侧提前拦住同类问题。

仍未做：内置包入 APK、首装向导分支、真机四条路径验证（内置首装 / 内置失败回退 / 已有 rootfs 升级 / 离线层恢复）。内置包不含 `git` / `curl` / 编译链，与 OCI 默认的 `buildpack-deps:noble-scm` 不等价，是否补齐 deb 闭包需另行决定。

**第二轮落地（同日）**：把内置包接进首装链路。仓库 `architectureCheck` 对存量文件的行数棘轮"只许降"，因此按拆文件实现，未抬高任何基线：

- 新增 `BundledRootfs.kt`（清单模型/解析、SHA-256）、`AssetBundledRootfsSource.kt`（读 APK `assets/rootfs/`）、`BundledRootfsProvisioner.kt`（选源、校验、回退）、`RootfsImportLayout.kt`（导入归档顶层目录归一）；
- `RootfsInstaller.importArchive` 增加可选 `version` / `digest`（默认仍为 `local-import` 语义），`normalizeImportedRoot` 拆到 `RootfsImportLayout.kt`，文件 436 → 431 行；
- RootFS 相关 Koin 绑定从已到 400 行上限的 `KoinModule.kt` 拆到 `RootfsModule.kt`，KoinModule 降至 385 行；
- `LinuxRuntimeImpl.initialize`（首装向导与设置页共用）改调 `BundledRootfsProvisioner`，文件维持 838 行基线；
- 安装 marker 记录 `version=<发行版版本>`、`digest=bundled:<assetId>`，与 OCI digest 区分；
- 验证：`architectureCheck` 通过；`BundledRootfsManifestTest` 4 项通过；`:app:compileDebugKotlin` 通过。`assets/rootfs/` 仍为空，行为与接入前一致。

**真机首装（同日）**：把 `out/` 归档与清单放进 `app/src/main/assets/rootfs/`（归档 SHA-256 与清单一致），`app/build.gradle.kts` 增加 `androidResources.noCompress += "xz"`；`assembleDebug` 产物 71.8 MB，归档在 ZIP 内为 stored。v1.12.0 release 同样以 stored 携带该归档，APK 43.5 MB（v1.11.0 不含内置包时为 24.4 MB）。设备 vivo V2419A / Android 17：点击"一键初始化沙箱"约 6 秒进入"沙箱已准备就绪"，marker 为 `rootfs-version=24.04.5` / `rootfs-digest=bundled:9ea6f5d765b3c09e`，安装占用 110 MB；guest 内 `uname -a` 返回 aarch64 Android 内核、`PRETTY_NAME="Ubuntu 24.04.5 LTS"`、uid=0、`/usr/bin` 下 355 个可执行文件。首启出现该机型的"16 KB 对齐"警告，来自既有 native 库，与内置包无关。

剩余：内置包缺失/损坏回退 OCI、已有 rootfs 升级、离线层恢复三条真机路径按用户决定不再测试；内置包安装阶段已补独立进度文案（校验 / 解压 / 回退提示）；归档已加入 `.gitignore`，由 `tools/bundled-rootfs` 重建后放入制品，不入库。
