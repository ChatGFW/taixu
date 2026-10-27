# Agent2API for TaiXu

上游 Agent2API v3.0.8（Linux ARM64 无界面服务端 + 原版 Web UI）的太墟适配，外加一个 Go 静态适配器
提供宿主签名通道和管理员重设。

执行 `python tools/package-agent2api-plugin.py` 生成可本地导入的
`dist/plugins/agent2api-3.0.9.txplugin`。运行文件与原版 Web UI 从上游固定版本下载，
并按打包器内固定摘要核验；完整 LICENSE 随包保留。

专用管理页不需要单独的包：它是宿主内置页面，从已启动服务的 `/taixu/` 读取字节，
入口在工具详情的「打开插件管理页面」。
使用方式、后台恢复语义与验证边界见 [完整说明](../../../docs/AGENT2API_PLUGIN.md)。
