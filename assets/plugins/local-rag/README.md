# Workspace Local-RAG for TaiXu

SQLite FTS5 词法检索的离线 `.txplugin` v2 PROCESS/MCP 包，无 embedding、模型或网络依赖。
需预先安装 Python ≥3.9（含 sqlite3/FTS5）和 Git，安装时探测，不自动下载。

导入 `dist/plugins/local-rag-0.1.0.txplugin` 后，Harness 可发现 `index`、`search`、`status`。
CLI 示例：`local-rag index my-project`，重复到 `complete=true`，然后
`local-rag search my-project '离线检索'`。

打包：`python tools/package-workspace-plugins.py --only local-rag --offline`。
索引保存原文片段，默认卸载保留数据；向在线模型使用搜索工具会把检索片段送入模型上下文。
完整语义、资源限制与待完成的真机验收见
[工作区插件说明](../../../docs/WORKSPACE_PLUGINS.md)。
