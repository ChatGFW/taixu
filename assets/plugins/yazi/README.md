# Yazi for TaiXu

官方 Linux aarch64-musl Yazi 26.9.1 的离线 `.txplugin` v2 PROCESS 包，使用原生 PTY。

导入 `dist/plugins/yazi-26.9.1.txplugin` 后，在交互终端执行 `yazi`；
可选路径参数：`yazi /workspace/my-project`。辅助 CLI 为 `ya`。
默认配置关闭镜像预览（PTY/无 Kitty 协议），首次复制到
`/opt/taixu/data/yazi/config/`，升级不覆盖用户配置。
上游 MIT LICENSE 随包保留。

打包：`python tools/package-workspace-plugins.py --only yazi`。
来源、固定摘要、使用说明与待完成的真机验收见
[工作区插件说明](../../../docs/WORKSPACE_PLUGINS.md)。
