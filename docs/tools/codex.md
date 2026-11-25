# Codex 兼容性记录

- 适配器：`CodexToolInstaller`
- 安装方式：通过 HTTPS 下载固定提交的 [OpenAI 官方 Linux 安装脚本](https://github.com/openai/codex/blob/53cd1894aa94185668d76e212e793e7950294f24/scripts/install/install.sh)，校验 SHA-256 后在 Linux 临时目录中运行。安装时将 `HOME` 指向 `/opt/taixu/tools/codex`，再创建 `/opt/taixu/bin/codex` 入口。
- 验证：`codex --version`
- 运行时：Linux ARM64；安装前提供 `curl` 和 CA 证书
- 数据策略：程序安装在 `/opt/taixu/tools/codex`，`CODEX_HOME` 指向 `/opt/taixu/data/codex`；默认卸载保留该数据目录，用户勾选删除数据时才移除。
- 最后核对：2026-11-25

来源：[OpenAI Codex 官方 README](https://github.com/openai/codex/blob/main/README.md)

正式发布前仍需在目标 Android ARM64 设备上确认官方二进制或 npm fallback 的兼容性，并复核许可。

## 项目内的 Codex 聊天

完成当前 Linux 发行版的初始化后，在本机工作区的项目文件页打开右上角菜单，选择「Codex 聊天」。页面使用工具中心安装的 `/opt/taixu/bin/codex`。每个发行版的登录状态各自保存在该发行版的 `/opt/taixu/data/codex`。可以在项目终端用以下命令登录。

```sh
CODEX_HOME=/opt/taixu/data/codex /opt/taixu/bin/codex login
```

页面打开时绑定项目路径和发行版，通过无 PTY 的 stdio 连接 Codex `app-server`。每次轮次归属对应的 Codex 线程，退出后可恢复最近 12 轮。应用私有目录只保存线程标识，不接收登录令牌。新对话会替换本页关联，旧会话仍保留在 Codex 中。

启动时清理继承给 CLI 的环境变量，并检查 CLI 返回的工作目录、沙箱与审批策略。轮次沿用 CLI 实际采用的工作区可写或只读沙箱，工具网络访问关闭。额外命令或文件权限请求可在当前轮次拒绝，也可以停止轮次。退出或中断会关闭该轮次的子进程，不自动重发输入。此接入仍依赖 Linux 发行版能运行 Codex 自身的沙箱，Android ARM64 实机兼容性待验证。

协议字段按 Codex CLI `0.163.0` 生成的 schema 核对。验证不需要联网登录或模型请求。
