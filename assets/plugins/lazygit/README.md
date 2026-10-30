# Lazygit for TaiXu

官方 Linux ARM64 Lazygit 0.66.2 的离线 `.txplugin` v2 PROCESS 包，使用原生 PTY。

预先安装 Linux Git，然后导入 `dist/plugins/lazygit-0.66.2.txplugin`。
在仓库中执行 `lazygit`；`lazygit --github` 显式复用 GitHub 插件配置。
上游 MIT LICENSE 随包保留。

打包：`python tools/package-workspace-plugins.py --only lazygit`。
来源、固定摘要、使用说明与待完成的真机验收见
[工作区插件说明](../../../docs/WORKSPACE_PLUGINS.md)。
