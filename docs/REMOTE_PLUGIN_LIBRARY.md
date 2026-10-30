# 远程插件库

设置首页仅保留「插件中心」：顶部提供「下载插件」「导入文件」，默认查看当前系统「已安装」列表。「下载插件」进入仓库和 GitHub / Gitee 渠道切换；设置搜索支持“仓库 / 下载插件 / GitHub / Gitee”直达。旧页面开关管理改名「内置功能」，与「网页插件」作为中心的次级入口；不再并列展示“插件管理 / 工具中心 / 插件仓库”三个入口。渠道选择由 `PluginLibraryPreferences` 持久化。
GitHub 读取 `taixu-lab/taixu-library` main 的 `catalog.json` 和 `.sig`，Gitee 读取 `wang_ke_bin/taixu-library` main 的 `catalog-gitee.json` 和 `.sig`。两者分别登记实际已发布附件，同一固定公钥验签，按渠道保存最高 revision。下载/预览/安装期间禁止切换，不静默跨渠道回退。
初次使用需先推送插件库配置和签名目录。当前 GitHub、Gitee 均只发布不超过 96 MiB 的小插件；Android 全栈与 Node.js 大套件不上传。

下载进度、百分比、已下载/总大小和「停止下载」位于对应插件卡片；顶部只显示目录加载状态。停止会取消 HTTP 请求，已校验分片可复用。PROCESS 卡片按当前系统的 ToolManager 安装记录显示「已安装 / 正在安装 / 可更新」，版本格式无法解析不等于未安装；WEB 卡片按已导入包显示安装版本。

`PluginCenterCatalogPolicy` 隐藏已迁移插件的旧推荐安装卡片（包括 RTK、llama.cpp 及其旧标识），下载统一进入仓库；实际已安装项仍在「已安装」中管理，进行中和失败的安装保留进度、修复入口。这只是展示策略，不删除工具清单、安装记录、收据或本地包，也不影响本地 LLM 引擎服务。

本地插件安装记录使用 `manifest.version`（插件包版本），验证命令输出的 RTK 等程序版本仅作为安装日志，避免用程序版本反复比较包版本。刷新目录时，旧 v1 记录只依据原安装收据的 `packageVersion` 和对应本地清单修正；不推测当前选中的候选包已安装。先复制原收据到正确版本键，再更新数据库；原收据、能力与资源声明均保留。此过程与安装共用互斥锁，不重装或执行插件脚本。

## 分发与安装

- `RemotePluginCatalog`：1 MiB 有界目录、Ed25519 验签、严格稳定 SemVer、API 兼容筛选、单文件/有序分片。
- 验签直接使用 Bouncy Castle lightweight Ed25519 API，严格检查 RFC 8410 公钥格式；不经过 Android JCA Provider 选择或 AndroidKeyStore，不生成密钥、不修改系统 Provider。工具清单共用该实现。
- `RemotePluginLibrary`：APK assets 固定公钥；私有目录按渠道保存成功验签的最高 revision，拒绝同渠道回退；不使用在线公钥建立信任。
- `RemotePluginDownloader`：最多 8 GiB，64 片；流式下载、逐片/整包 SHA-256、精确大小限制、临时文件提交。缓存按整包指纹隔离；完成分片可复用，未完成片重下。取消同时关闭 HTTP 调用。
- 下载完成后在交给任何导入 UI 前验证包内 manifest 的身份、版本、执行类型。PROCESS 继续 ToolManager 原生预览/能力增量确认/安装事务；WEB 继续 WebPluginRepository 预览确认/静态包导入，不执行 Linux 配方。
- PROCESS 更新比较当前本地发行版的安装版本，WEB 比较当前导入版本；下载不改写已安装版本、不授予能力，安装时继续依赖/资源/目标发行版检查。已经导入但安装失败的同版本可在工具详情重试。

## 存储与任务

下载缓存位于 App cache/remote-plugins。空间预检覆盖分片与合并包（2 倍包大小加 64 MiB），不代表安装空间充足；实际解压和程序快照仍由安装器处理。合并完成后删除分片，整包保留在系统可回收的缓存中。

下载由仓库页面的 ViewModel 持有，旋转继续；返回退出该页面、销毁 ViewModel 后取消，重开可复用完成分片。不是后台 WorkManager 下载服务，不承诺进程死亡后自动续跑。网络断开可重试；不实现单片内部 HTTP Range 续传。

## 发布端

`C:/work2/taixu-library/scripts/library.py` 提供 keygen / prepare / publish / publish-gitee / sync-gitee，详见同仓库 README。兼容已打包 v1 PROCESS，通过 legacy.tool-<id> 对照本地插件；v2 继续检查包内身份。小于 2 GiB 单文件，否则默认 512 MiB 切片；Gitee 不接收大包。prepare 不联网，publish 显式创建 Release；Gitee 上传后再公开下载核对哈希，成功后才更新对应签名目录，不自动 Git 提交/推送。发布令牌仅取自环境，不写入目录或日志。

插件库 checkout 的 origin 配置 GitHub、Gitee 双 push URL，新 checkout 可用 configure_remotes.py 恢复；一次提交后 push origin main 依次推送两端，非跨平台原子事务。Release 附件不随 git push 同步，须使用发布脚本。

修改公钥必须同步发布 APK；私钥只保存在仓库外，不进入包、日志、App 或 Git。
