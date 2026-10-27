# Agent2API 本地插件

本适配使用上游 Agent2API **v3.0.8** 的 Linux ARM64 无界面服务端及原版 Web 管理界面，
外加一个太墟专用适配器（`agent2api-host`，Go 静态编译）。不运行 Docker、Tauri 或 Node.js。
离线导入、安装不下载依赖；实际调用模型需要联网。

交付物为单个可本地导入的包：`dist/plugins/agent2api-3.0.9.txplugin`（PROCESS 后端：ARM64 服务端 + 适配器 + 原版 UI）。
专用管理页不需要额外包——它是宿主内置页面（`taixu.tool-service`），页面字节在运行时从服务的 `/taixu/` 读取。

## 为什么需要适配器

上游服务端只能被原版面板驱动，直接暴露给 WebView 会带来两个问题：管理接口没有太墟侧的鉴权与逐次确认，
页面也无法通过宿主受限通道安全访问。适配器因此只做三件事：

- **单一入口**：监听 `127.0.0.1:3065`，把非 `/taixu/` 路径原样反向代理给上游私有端口 `127.0.0.1:3066`，
  原版面板、`/v1` 网关、`/health` 全部照旧可用。
- **原生签名通道**：`/taixu/` 只接受太墟宿主用进程管道下发的每进程密钥做的 HMAC-SHA256 签名请求，
  绑定时间戳、一次性 nonce、方法、路径与请求体摘要，响应同样签名。浏览器、WebView 内容或其他进程无法伪造。
- **管理员重设**：专用页面只能请求「创建／重设管理员」，用户名限 `[A-Za-z0-9_.-]{3,32}`，强制 `preserveAccounts=true`；
  密码由本机安全随机生成、只在当前服务内短暂保留，不经由页面提交，也不写入日志。

上游的固定启动参数：`AGENT2API_HOST=127.0.0.1`、`AGENT2API_PROXY_PORT=3066`、
`AGENT2API_ALLOW_NO_KEY=0`、`AGENT2API_SELF_UPDATE=0`。启动前会清掉继承环境里所有
`AGENT2API_ADMIN_*` 等危险变量，避免旧密码留在长期存活的子进程环境里。

## 使用

1. 安装本次构建的太墟 APK。旧版可以导入 PROCESS 包，但没有本次补齐的持续运行调度与专用页面。
2. 确保本地 Linux 发行版已就绪，使用兼容 glibc 2.36+ 的 ARM64 RootFS，保留标准 CA 证书库。
3. 在工具中心导入 `agent2api-3.0.9.txplugin`，确认包信息并等待安装验证完成。
4. 在 Agent2API 详情点击启动。之后有两个入口：
   - **打开插件管理页面**（详情卡片按钮）：服务状态、模型列表、创建／重设管理员。重设需逐次确认。
   - **外部浏览器打开**：`http://127.0.0.1:3065/` 打开原版完整面板，用于添加服务商账号、创建网关 Key。
5. 首次使用**没有默认账号密码**。原版面板显示「创建账号并进入面板」时，自行设置管理员（密码至少 8 位）；
   也可以直接在专用页面创建，用户名默认 `admin`。
6. 在太墟模型设置中使用本地分组、OpenAI 兼容协议、Base URL `http://127.0.0.1:3065/v1`，
   填入在原版面板创建的网关 Key；模型名称使用 `/v1/models` 实际列出的 ID。
7. 在详情开启「自启动与持续运行」。关闭浏览器和详情页面不会停止网关。

两个易混淆点：

- 太墟详情页的 `?token=…` **不是** Agent2API 的管理员密码，也不能用于登录它的面板。
- 插件固定只监听本机，详情页里「已绑定 0.0.0.0」这类通用文案不适用于它。

部分提供商支持网页登录，部分只能粘贴凭证。手机不会读取电脑上的客户端登录态。
插件不预装账号、Token 或管理员密码。端口固定 3065，端口冲突时应先停止占用者。

## 管理员重设

忘记原管理员密码时，用专用页面的「重设管理员」：保留模型账号与网关 Key，只替换面板账号。
适配器会用官方环境变量覆盖方式写入新的 bcrypt 哈希，然后**再重启一次且不带密码**，
确保长期运行的子进程里不残留明文。重设会短暂中断进行中的请求。

## 后台与恢复语义

- 复用既有 `tool_settings.autoStart`，默认关闭；只有用户启用后才自动启动和恢复。
- Runtime 前台服务每 5 秒检查当前本地发行版，检查托管进程是否存活且服务端口可连接。
  连续 3 次不健康才恢复；恢复尝试使用指数退避，最长 5 分钟，连续健康约 30 秒后重置。
  这不是 HTTP 业务健康检查，无法识别仍监听端口但业务已经卡死的服务。
- 每次恢复使用实际安装版本的清单和包租约，与安装/卸载互斥；命令固定到捕获的发行版。
  未安装、安装中、缺失收据、停用的包，以及远程执行环境均不会获得自动启动。
- 自动恢复的启动等待最多 30 秒；超时或取消回收启动实例。手动启动继续使用现有工具启动超时。
- 详情的「停止」会持久关闭该服务的自启动，避免停止后立即重启。关闭自启动开关本身不停止已经运行的进程。
  卸载也会先关闭自启动。通知的「停止」关闭所有已安装发行版的工具自启动并停止沙箱进程。
- 应用进程被系统终止后，Runtime 前台服务重新创建时检查已保存发行版中的安装记录和自启动选择，
  仅当存在已安装且已开启自启动的工具时恢复已有沙箱，再恢复服务；不需要创建详情页面，不下载新 RootFS。
  不新增开机广播或绕过系统限制的拉起路径；强行停止应用后，需要用户重新打开太墟。
- 沙箱前台服务声明 `specialUse` 并给出用途说明，避免将长期服务托管误作 `dataSync`。
  活跃前台服务在唤醒锁的 8 小时兜底到期前续租；停止后释放租约，续租不会累积引用计数。
  Android 对 `dataSync` 的后台累计时限见 [官方说明](https://developer.android.com/develop/background-work/services/fgs/timeout)，
  `specialUse` 的用途声明与分发审核要求见 [服务类型说明](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)。
  仍受系统内存回收、厂商省电策略和用户强行停止影响，不能保证永不终止。

在权限向导中允许通知，并按手机系统设置允许后台运行和自启动。没有通知权限时，现有 Runtime 服务入口不会启动，
自动恢复调度也不会运行。

## 文件与升级

- 程序、适配器和原版界面：`/opt/taixu/tools/agent2api/`
- 专用页面资源：`/opt/taixu/tools/agent2api/web/`
- 数据库、账号和配置：`/opt/taixu/data/agent2api/`，默认保留，目录使用私有权限。
- 安装验证使用工具目录内的临时空数据库、独立端口 13065，不读取真实账号；退出后回收临时服务和目录。
- 启动脚本固定回环监听、保留无 Key 拒绝推理的行为，并关闭上游原地自更新。
  更新需重新制作递增版本的插件，走太墟安装事务，避免绕过包完整性和安装收据。
- 上游自行保存的账号凭证不经过太墟 SecretManager；不要分享数据目录或包含账号的导出文件。

上游完整 LICENSE（包含其附加使用声明）与 Go 标准库 LICENSE 原样随包保留。参见
[v3.0.8 LICENSE](https://github.com/aimod-cc/Agent2API/blob/v3.0.8/LICENSE)。

## 可复现打包

```powershell
# 1. 构建 ARM64 适配器（需要 Linux 上的 Go 1.26，源码在 assets/plugins/agent2api/adapter）
cd assets/plugins/agent2api/adapter
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -buildvcs=false -ldflags="-s -w -buildid=" -o ../../../../build/agent2api-adapter/agent2api-host .

# 2. 打包插件
python tools/package-agent2api-plugin.py
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat :plugin:cli:installDist --console=plain
plugin/cli/build/install/taixu-plugin/bin/taixu-plugin.bat check assets/plugins/agent2api
python scripts/tests/test_agent2api_plugin.py
```

打包器只访问固定上游 Release 和 tag 的 LICENSE，分别核对写死的 SHA-256；也可以事先把下载文件放入
`build/agent2api-download/` 后离线打包。`payload/vendor/` 为可重建产物，不纳入源码版本控制。
ZIP 文件排序、时间戳、清单和各文件摘要固定。同一输入得到同一输出（`-buildid=` 保证适配器也可复现）。

## 验证边界

已通过：

- v2 CLI 包完整性检查、离线打包回归、Shell 语法检查。
- Go 适配器单元测试：签名绑定方法/路径/体、重放拒绝、bootstrap 严格解析、重设拒绝未知字段与破坏性载荷、
  危险环境变量清除。
- 适配器端到端冒烟（原生架构 + 桩上游，`scripts/tests/smoke_agent2api_adapter.py`）：仅签名可读页面、
  未签名与重放 403、路径绑定、`/taixu/api/status` 与模型列表、重设生成 32 位密码并两次重启上游、
  最后一次重启环境不含明文、SQLite 文件保留、跨 `/taixu/` 与原生路径互不泄漏。
- 恢复退避、取消回收、唤醒锁续租等宿主单元测试，以及 `test`、`architectureCheck`、Debug APK 构建。
- WSL + 外置 QEMU + ARM64 libc 实际运行包内 ARM64 服务端：`/health`、原版 UI、空账号状态、
  无 Key 拒绝推理及同一数据库重启；测试只请求本机服务，不调用上游付费模型。

可重复冒烟验证（Linux/WSL，测试工具自行提供，不打入插件）：

```sh
# 包内 ARM64 服务端
python3 scripts/tests/smoke_agent2api_arm64.py --qemu /path/to/qemu-aarch64 --sysroot /path/to/aarch64-linux-gnu
# 适配器协议（先用同一源码构建本机架构）
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o build/agent2api-adapter/agent2api-host-amd64 ./assets/plugins/agent2api/adapter
python3 scripts/tests/smoke_agent2api_adapter.py
```

QEMU 验证不等价于 Android PRoot 真机验证。当前没有连接 ADB 设备，仍需在手机上验收首次导入、升级、
息屏长时间运行、进程被终止后的恢复和厂商后台策略。
