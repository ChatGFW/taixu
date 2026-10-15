# `.txplugin` v2 包协议

实现入口：`plugin/packages` 的 `PluginPackageProtocol`、`PackageInstallPolicy`；Android 导入与安装由 `tools` 适配。v1 保持兼容，统一目录身份为 `legacy.tool-<toolId>`，原工具详情、启动与卸载入口保留。

## 包与身份

ZIP 根目录只允许 `manifest.json` 和 `payload/`。v2 严格拒绝未知字段；清单最大 1 MiB，解压合计最大 8 GiB，条目最多 20,001。嵌套压缩包不在导入阶段展开。版本目录不可覆盖，升级必须递增版本。

`id` 是旧工具存储键，保持小写字母、数字和连字符；`plugin.id` 是独立的点分插件身份。第三方不能声明 `taixu.*`、`legacy.*` 身份。v2 不能覆盖内置或在线工具，已有 v2 身份不能改名，也不能降为 v1。当前仅支持本地自包含 PROCESS 包；不加载外部原生代码或 Web 插件代码。

最小可安装示例（空 payload，安装时生成脚本）：

```json
{
  "schemaVersion": 2,
  "id": "hello-plugin",
  "name": "Hello plugin",
  "description": "Offline process plugin example",
  "version": "1.0.0",
  "source": "LOCAL",
  "offlineOnly": true,
  "installMethod": "LOCAL_PACKAGE",
  "installSteps": [
    "printf '#!/bin/sh\\necho hello\\n' > \"$TAIXU_TOOL_DIR/bin/hello-plugin\"; chmod 755 \"$TAIXU_TOOL_DIR/bin/hello-plugin\""
  ],
  "launchCommand": "hello-plugin",
  "verifyCommand": "hello-plugin",
  "plugin": {
    "id": "example.hello",
    "api": {"minimum": 1, "maximum": 1},
    "execution": "PROCESS",
    "extensions": [{"id": "launch", "point": "PROCESS"}],
    "dependencies": [],
    "capabilities": [],
    "resources": [],
    "integrity": {}
  }
}
```

实际携带文件时，`integrity` 必须逐一列出 payload 下所有普通文件的相对路径和小写 SHA-256。示例 `payload/run.sh` 对应键 `run.sh`；多一个、少一个或内容不符均拒绝。安装复制时再次校验，v2 不改写换行符或 BOM。v1 保留旧 `.sh` 换行修复。

## 依赖与版本

v2 使用严格 SemVer，含预发布优先级，build metadata 不影响排序。插件依赖与旧 `dependencies` 运行时依赖字段分开：

```json
{"id":"example.sdk","minimumVersion":"1.0.0","maximumVersionExclusive":"2.0.0"}
```

下界包含、上界不包含；省略上界表示无上限。依赖必须先安装，宿主不会自动下载。安装和升级同时复核所有已安装依赖者；缺失、版本不符、循环和身份冲突拒绝。被依赖插件不能卸载。

检查依据是同一发行版 DB `installedVersion` 对应的版本收据，不采用刚导入的新清单。收据保存身份、版本、依赖、能力及资源，不保存环境变量、安装脚本或 Provider 凭证；`packageVersion` 只关联不可变包目录，兼容旧工具把命令输出横幅记作安装版本的情况，旧 v2 收据缺省时使用 `installedVersion`。v2 收据缺失或损坏时拒绝继续猜测。旧工具在导入替换清单前补记兼容收据。

## 能力与资源

能力项为 `{"name":"NETWORK","target":"example.com"}`，当前支持 NETWORK、WORKSPACE_READ、WORKSPACE_WRITE、LOCAL_WEB。确认页展示相对已安装收据新增的请求（含 target 变化），并绑定预览清单。它们是声明，尚未实现逐能力执行授权；脚本仍使用现有 PRoot 环境与 Provider 注入路径，不能视为沙箱。

资源示例：

```json
{"key":"example.sdk","path":"/opt/shared-sdk","sharing":"SHARED","contentSha256":"<64 位小写 SHA-256>","removal":"RETAIN"}
```

允许本插件 `/opt/taixu/tools/<id>`、`/opt/taixu/data/<id>`、`/opt/taixu/shared/*` 或独立 `/opt/*` 路径。独占为默认值；共享必须具有相同 key、完整路径和内容指纹。不同插件的目录祖先/子目录重叠拒绝，同 key 指向不同路径也拒绝。框架自动把程序目录、数据目录和命令入口纳入独占声明，检查跨插件冲突。

声明中的资源目前只接受 RETAIN：卸载不自动删除任意全局路径。v2 禁止自定义卸载脚本，框架清理工具目录、可选数据和收据中的命令入口。暂不允许升级移除原有命令入口；需先卸载旧版本。资源内容指纹是共享一致性声明，不能证明安装脚本实际写入的文件内容；任意脚本也能越出声明路径，因此这些检查面向协作插件，不构成恶意代码隔离。

## 事务与恢复范围

安装/卸载引用检查串行执行；通用安装器固定清单、payload 版本与目标发行版，切换 UI 选择不能重定向安装。v2 验证或命令入口创建失败不会被降级为“文件存在即成功”，成功版本取清单版本而非程序输出横幅。

重装和升级都先保留已有程序快照，再执行候选版本配方与安装后验证，不要求损坏的旧版本预先通过验证。导入只切换安装候选；启动、交互终端、验证、服务端口和卸载按实际安装版本读取对应包清单。安装失败或取消后，即使候选版本仍为新版，旧版本的运行配置也保持原样；对应已安装包清单缺失时拒绝用新版配置替代。

保留各版本包和原有工具目录快照事务，不提供任意版本切换 UI，也不保证任意脚本全局副作用回滚。既有事务只覆盖工具目录，收据在提交程序快照前写入；进程死亡窗口及真实 Linux 文件恢复仍需真机验收。全局路径、系统包、命令脚本和 Provider 环境并未变为完整插件事务。保留的全局文件也不是宿主维护的实时资源盘点。

统一管理页同时显示原生页面插件和当前发行版的进程插件/兼容工具。进程插件由工具详情管理，原生页面开关不控制其后台进程；外部 Web/IPC 扩展及能力代理见 [后续方案](PLUGIN_PLATFORM.md)。
