# Term Devtools for TaiXu

离线 `.txplugin` v2 PROCESS 合集，内含：

- [fx 40.0.1](https://github.com/antonmedv/fx/releases/tag/40.0.1) `fx_linux_arm64`（MIT）
- [btop 1.4.8](https://github.com/aristocratos/btop/releases/tag/v1.4.8)
  `btop-aarch64-unknown-linux-musl`（Apache-2.0）

导入 `dist/plugins/term-devtools-1.0.0.txplugin` 后：

```sh
btop
# 管道或交互浏览 JSON
cat response.json | fx
fx data.json
```

工具详情默认启动 `btop`（需交互 PTY）。Android 内核部分指标可能不全。
上游 LICENSE 随包保留。

打包：`python tools/package-workspace-plugins.py --only term-devtools`。
说明见 [工作区插件](../../../docs/WORKSPACE_PLUGINS.md)。
