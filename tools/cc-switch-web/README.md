# 官方 CC Switch 的 Web 适配

固定官方 v4.0.30 提交 `3a0b86a26649f61d86dfbc3878d73220199f70b2`。
`prepare.py` 验证干净的上游 checkout，生成独立构建目录，不修改上游工作树。
保留业务 Rust 模块和官方 React UI，替换 Tauri GUI 依赖与进程入口。

## 结构

- `overlay/headless`：状态注入、事件和 IPC Channel 的服务端适配。
- `overlay/command`：把官方 command 签名转换为 JSON 参数调用；保留结构化业务错误，
  同步命令进入阻塞线程池，HTTP 只允许官方 `generate_handler!` 注册的命令。
- `overlay/src/web.rs`：本机监听、随机密码、Host/Origin 校验、JSON 调用、SSE 和静态 UI；
  数据目录独占锁、退出恢复。没有 Tauri WebView、GTK 或 X11 依赖。
- `frontend`：Vite 同源传输映射；浏览器剪贴板、外链和会话导出。窗口/托盘功能不模拟业务成功。
- `smoke.py`：临时 HOME、空凭据环境的真实服务进程检查，不调用付费模型。
- `browser-smoke.mjs`：隔离测试实例的手机宽度浏览器检查。

## 准备

```sh
git clone --depth 1 --branch v4.0.30 https://github.com/farion1231/cc-switch.git dist/cc-switch-port/upstream
python tools/cc-switch-web/prepare.py dist/cc-switch-port/upstream dist/cc-switch-port/build
```

仅当明确要刷新本工具生成的目录时添加 `--refresh`，不要在生成目录中维护手工修改。
编辑适配器后重新准备。上游版本变化必须重新审查平台依赖和启动顺序。

## 前端

在生成目录 `renderer` 中运行：

```sh
pnpm install --frozen-lockfile
node node_modules/vite/bin/vite.js build
```

沿用上游锁文件及构建脚本白名单（esbuild），不要运行未审查的依赖脚本。
产物为 `renderer/dist-web`。需要 Node 20+。

## 后端与 ARM64

在 Linux/WSL 使用 Rust stable（本次验证 1.99.1）、Cargo Zigbuild 0.24.1 和 Zig 0.14.1。
Zig Linux x86_64 官方归档 SHA-256：
`24aeeec8af16c381934a6cd7d95c807a8cb2cf7df9fa40d359aa884195c4716c`。
请使用 GNU 2.31 目标，直接用新 Ubuntu 的 GCC 交叉编译会引入旧 RootFS 不支持的 glibc 符号。

```sh
rustup target add aarch64-unknown-linux-gnu
# 将 zig 和 cargo-zigbuild 加入 PATH，然后进入生成目录
CARGO_PROFILE_RELEASE_OPT_LEVEL=s CARGO_PROFILE_RELEASE_STRIP=symbols \
  cargo zigbuild --release --target aarch64-unknown-linux-gnu.2.31 --bin cc-switch-server --locked
```

宿主测试构建：`cargo build --bin cc-switch-server --locked`。
只对独立适配测试运行 `cargo test --manifest-path headless/Cargo.toml`；上游桌面测试依赖 Tauri GUI，
不能把移植构建冒充为上游全量桌面回归。

```sh
python tools/cc-switch-web/smoke.py /path/to/host/cc-switch-server /path/to/renderer/dist-web
```

浏览器检查使用隔离 HOME 的测试实例，端口 19871、测试账号 `admin/isolated-ui-password`；
这些固定值只属于测试，不写入插件。测试依赖 playwright，使用本机 Edge 的无头模式。

## 发布

先通过服务测试、ARM64 启动和打包哈希检查，再运行 `tools/package-cc-switch-plugin.py`。
产物输出至 `dist/plugins/cc-switch-4.0.30.txplugin`，旁边的 JSON 记录大小、摘要和上游提交。
插件库的 `scripts/library.py prepare` / `publish` 负责签名目录与 Release 附件。
旧 Web 分支数据保留于原路径；正式包不包含测试数据或任何用户凭据。
