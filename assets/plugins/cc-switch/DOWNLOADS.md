# CC Switch 4.0.30 — 太墟 Web 插件

这是官方 [farion1231/cc-switch v4.0.30](https://github.com/farion1231/cc-switch/releases/tag/v4.0.30)
的 Web 移植，固定提交 `3a0b86a26649f61d86dfbc3878d73220199f70b2`。
业务模块和 React 页面来自该提交；`tools/cc-switch-web` 提供 HTTP / SSE 适配，
不再使用旧 `Laliet/cc-switch-web v0.21.0` 二进制。

## 安装与使用

在插件中心导入 `cc-switch-4.0.30.txplugin`，安装后可启动、停止和设置自启动。
启动后点击浏览器入口，使用 **http://127.0.0.1:19870/**，账号为 `admin`。
首次运行会生成独立随机密码，在同一 Linux 发行版的终端查看：

```sh
cat /opt/taixu/data/cc-switch/official-v4/web_password
```

密码不写入日志或 URL。管理页只监听回环地址，不支持局域网链接；旧版工具详情中显示的
LAN 链接和随机 token 不是本插件的登录方式。网页通过同源 HTTP Basic 登录，业务命令走
JSON POST，事件走 SSE。页面可管理该 Linux 发行版内的 CLI 配置。

数据位于 `/opt/taixu/data/cc-switch/official-v4`，不会覆盖旧 Web 分支数据库或
`~/.cc-switch` 符号链接。两条版本线不能假设数据库兼容，需要的旧配置请自行导出后在新页导入。
普通卸载保留用户数据。正常 TERM/INT/HUP 退出会调用官方逻辑恢复 CLI 直连配置；强制终止后由下次启动恢复。

## 兼容范围

- Linux ARM64 / glibc，面向太墟 Debian/Ubuntu PRoot。不是 Android Bionic 二进制。
- 保留官方业务命令、Provider、代理/聚合、隐私过滤、MCP、Skills、会话和设置接口。
- 浏览器承接外链、剪贴板与会话 Markdown 下载。桌面托盘、窗口、系统文件选择器、桌面终端拉起、
  桌面应用自更新不属于 Web 运行环境；对应未适配操作会明确报错，升级从插件中心安装新包。
- 保持 v1 包与 `legacy.tool-cc-switch` 目录身份，已有插件可以识别新版本。
  安装前验证包内 SHA-256 清单；远程分发继续使用插件库签名和整包哈希。

## 构建与打包

详细命令见 [`tools/cc-switch-web/README.md`](../../../tools/cc-switch-web/README.md)。
构建产物不进入 Git；插件库以 Release 附件分发 `.txplugin`。

```sh
python tools/package-cc-switch-plugin.py \
  --server /path/to/aarch64-unknown-linux-gnu/release/cc-switch-server \
  --ui /path/to/renderer/dist-web \
  --build-tree /path/to/prepared-build
```

打包器拒绝非 ARM64 ELF、缺失 UI、错误上游提交、过新的 glibc 需求及超过 96 MiB 的包。
产物包含官方许可证、Cargo 锁文件、上游信息、程序/UI 与逐文件哈希；同一输入生成相同 ZIP 字节。
