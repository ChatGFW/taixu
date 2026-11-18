# 内置 RootFS 构建（bundled-rootfs）

状态：**已随 APK 携带，并在真机完成内置包首装**（debug 包，未混淆）。归档缺失时行为与接入前完全一致。

## 边界

- 不修改 `app/src/main/assets/`，不改变 APK 体积与首装行为；
- 不替换 `RootfsInstaller.installOci` / `importArchive` / 离线层恢复这条既有链；
- 只覆盖 Ubuntu Base 的最小发行版，**不等价于** OCI 默认镜像 `buildpack-deps:noble-scm`。

## 用法

要求 Linux x86_64，`curl`、`tar`、`xz`、`python3`、`sha256sum`。

```bash
# 单次构建：out/rootfs-arm64.tar.xz + out/manifest.json
bash tools/bundled-rootfs/build-ubuntu-rootfs.sh

# 连跑两次并比较归档字节，验证可复现
bash tools/bundled-rootfs/build-ubuntu-rootfs.sh --verify-reproducible
```

可选环境变量：`UBUNTU_VERSION`、`BASE_ARCH`（arm64 / amd64）、`XZ_PRESET`、`XZ_THREADS`、`SOURCE_DATE_EPOCH`、`OUT`、`CACHE`。
未在 `BASE_PIN` 中登记校验值的 Ubuntu 版本会直接失败，不会下载未经验证的 Base 包。

## 实测结果

构建环境：云主机 Ubuntu 22.04.5、GNU tar 1.34、XZ Utils 5.2.5（2026-11-17）。

| 项 | 值 |
|---|---|
| 发行版 | Ubuntu 24.04.5（arm64 / `ubuntu-base`） |
| 归档 | `rootfs-arm64.tar.xz`，17,937,512 字节（17.1 MiB） |
| 解压后 | 103,239,680 字节（约 98.5 MiB），3415 个条目 |
| 归档 SHA-256 | `2aaa8f089615e8d1cb32336f03e383aec53e62d795323293ce376010eadf27a7` |
| `assetId` | `9ea6f5d765b3c09e`（由配方与输入摘要派生，改脚本即变） |
| 可复现性 | 两次独立构建归档字节完全一致 |

归档 SHA 只对"同一 Base 包 + 同一归一化 + 同一 tar/xz 版本与参数"成立。`manifest.json` 记录了 `xzVersion` / `tarVersion` / `xzPreset` / `xzThreads` / `sourceDateEpoch`，跨主机复现前必须先对齐这些字段。

## 关键约束

1. **`/etc/os-release` 必须实体化。** Ubuntu Base 里它是指向 `../usr/lib/os-release` 的相对符号链接；太墟 `RootfsValidator.resolveGuestPath` 对链接目标中的 `..` 直接拒绝，原样打包会在 PRoot 启动前就校验失败。构建脚本把链接替换为实体文件，`lib/manifest.py validate` 会拦住同类问题。
2. **不含 SCM 工具链。** 内置包只有 Base 系统（含 bash、dash、apt、coreutils），没有 `git` / `curl` / 编译链。OCI 路径的 `buildpack-deps:noble-scm` 自带这些；若内置包要作为等价默认，需要在构建时追加固定版本的 deb 闭包，目前未做。
3. **apt 源保持上游原样。** `DistroConfigurator` 会在安装时改写为 TUNA 镜像，构建阶段不预置镜像，避免两处配置漂移。
4. **未验证项**：真机安装、解压耗时、解压峰值内存、安装后 `apt update` 行为。这些必须在设备上测，本文不提供估算。

## 接入状态

已接线（`architectureCheck`、`:runtime` 单测、`:app:compileDebugKotlin` 均通过）：

- `RootfsInstaller.importArchive` 支持传入 `version` / `digest`，默认值保持原 `local-import` 语义；
- `BundledRootfsProvisioner` 负责选源：按清单 SHA-256 校验归档，通过后走既有 `importArchive` 的 staging / validate / replace 流程，失败则回退 `installOci`；
- `AssetBundledRootfsSource` 从 APK 的 `assets/rootfs/` 读取 `manifest.json` 与归档，目录缺失、清单不合法、ABI/发行版不匹配时返回 null，不抛异常；
- `LinuxRuntimeImpl.initialize`（首装向导与设置页共用）优先内置包，缺失或损坏时自动回退 OCI；
- 安装 marker 记录 `version=<发行版版本>`、`digest=bundled:<assetId>`，与 OCI digest 区分；
- `app/build.gradle.kts` 已为 `xz` 关闭二次压缩。

## 真机首装实测（2026-11-17）

设备 vivo V2419A / Android 17（arm64-v8a），debug 包 `top.wkbin.taixu.debug`：

| 项 | 结果 |
|---|---|
| APK | 71.8 MB（debug、未混淆）；v1.12.0 release 携带内置包为 43.5 MB，同版本不含内置包的 v1.11.0 release 为 24.4 MB |
| APK 内归档 | `assets/rootfs/rootfs-arm64.tar.xz`，ZIP 存储方式为 stored（`compress_type=0`），未二次压缩 |
| 首装耗时 | 点击"一键初始化沙箱"到"沙箱已准备就绪"约 6 秒 |
| marker | `rootfs-version=24.04.5`、`rootfs-digest=bundled:9ea6f5d765b3c09e`（确认走内置包，未下载 OCI 镜像） |
| 安装占用 | 110 MB（含 103 MB RootFS 与 metadata） |
| guest 验证 | `uname -a` 返回 aarch64 Android 内核；`PRETTY_NAME="Ubuntu 24.04.5 LTS"`；`id` 为 uid=0；`/usr/bin` 下 355 个可执行文件 |

首启时系统弹出"16 KB 对齐"警告（`libproot.so`、`libtermux.so`、`libtalloc.so` 等既有 native 库未对齐），与内置包无关，是 debug 包在该机型上的既有现象。

内置包安装阶段的进度文案已独立为"校验内置 RootFS 完整性"与"解压并安装内置 RootFS"，回退时显示"内置包不可用，回退 OCI 镜像下载"，未检测到内置包时显示"未检测到随包内置 RootFS，使用 OCI 镜像下载"。该阶段约数秒，本轮真机窗口未捕捉到文案切换。

尚未做：

1. 内置包缺失或损坏时回退 OCI 的真机验证（按用户决定暂不测试）；
2. 已有 rootfs 升级、离线层恢复两条真机路径（按用户决定暂不测试）；
3. 归档入库已定为不入库：`.gitignore` 已忽略 `app/src/main/assets/rootfs/`，构建产物由本目录脚本重建后放入制品。

## 来源

构建模式（输入全量 pin、镜像回退、固定 epoch、xz 线程参数）参考 `ccch1mneyyy/dsh-go` 的 `runtime/build-runtime.sh`（AGPL-3.0）。本目录脚本为独立重写，未复制其源码；Ubuntu Base 的 SHA-256 取自官方 `SHA256SUMS`，与该项目 pin 表一致。
