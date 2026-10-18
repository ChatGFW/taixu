# 静态 Web 页面与能力授权（P5 首片）

`plugin:ui` 提供 `WebPagePlugin`，将宿主注册的静态 Web 页面接入既有 PAGE 目录。`plugin:runtime` 的 `PluginCapabilitySession` 管理当前文档的授权与请求。注册只构造描述，不加载 WebView、静态文件或执行器。

当前 `.txplugin` v2 仍只接受 PROCESS；此接口不是外部 Web 包导入器，也不加载远程页面。先前的原生页面与 Harness 扩展规则不变。

## 使用与验证入口

设置 → 应用功能插件 → Web 插件示例 → 打开。点击“读取应用信息”后，在应用原生弹窗中选择单次允许、当前页面 5 分钟允许或拒绝。授权后只返回应用包名、Android API 与插件 API 版本；顶部可撤销。关闭页面再打开需要重新确认。

入口工厂：[`WebPluginDemo`](../plugin/ui/src/main/java/top/wkbin/taixu/plugin/ui/web/WebPluginDemo.kt)。工厂由 `NativePluginPlatform` 装配，页面停用后遵循 P2 的入口和代次规则。

## 编译时接入

在宿主装配代码中创建 `WebPagePlugin` 并调用 `create()`，把返回的 `NativePagePlugin` 加入现有目录：

```kotlin
WebPagePlugin(
    id = "example.info",
    version = "1.0.0",
    title = { "信息面板" },
    description = { "读取应用公开信息" },
    capabilities = listOf(WebPluginCapability(
        PluginCapability("host.app-info", "application"),
        title = { "允许读取应用信息？" },
        description = { "仅返回应用包名。" },
    ) { context -> context.packageName }),
    loadAssets = { context ->
        listOf("index.html", "plugin.js").associateWith { name ->
            context.assets.open("info-panel/$name").use { it.readBytes() }
        }
    },
).create()
```

`loadAssets` 与执行器都是受信任的宿主代码。实际权限由执行器负责，不得登记绕过现有工具审批、PLAN 或 Android 系统权限的实现。执行器在页面协程中调用；阻塞操作须切换 dispatcher 并协作取消。执行器异常只返回固定错误码；返回文字不自动脱敏，含敏感数据的实现须在返回前按所属业务边界脱敏。

资源必须包含 `index.html`，最多 64 项、合计 2 MiB，路径只能由普通文件名和目录组成；不接受路径穿越、编码路径、查询串或 URL。宿主保留 `theme.css`，提供当前 MaterialTheme 颜色变量 `--surface`、`--on-surface`、`--primary`、`--on-primary`、`--container`，供页面样式消费。主题变量是页面创建时的快照。

HTML 使用外部脚本和样式，不使用内联脚本、iframe 或 Worker。当前 CSP 不允许网络连接、表单提交和内联脚本。每个页面使用独立随机 HTTPS 来源；所有资源请求由静态快照响应，不回退真实网络。

## 消息协议

仅在支持 `WEB_MESSAGE_LISTENER` 的 Android System WebView 上启用，缺失时显示更新提示，不降级为无来源校验的桥。协议使用 AndroidX WebKit 的 [addWebMessageListener](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener(android.webkit.WebView,java.lang.String,java.util.Set,androidx.webkit.WebViewCompat.WebMessageListener))。

主文档使用 `window.taixuPlugin.postMessage()` 提交 JSON 字符串：

```json
{"id":"request-1","action":"host.app-info","target":"application"}
```

监听 `window.taixuPlugin.onmessage` 接收：

```json
{"id":"request-1","success":true,"text":"result"}
```

消息最多 4096 字符，只接受上述三个字符串字段，禁止嵌套结构、未知字段和授权/身份/执行环境字段。来源与主帧标记取自 WebView 回调，不取自消息。返回文本最多 32768 字符；固定失败码包括 `origin_denied`、`capability_denied`、`replayed_request`、`busy`、`permission_denied`、`execution_timeout`、`execution_failed`、`revoked`。

同一页面最多受理 256 个请求 ID，每个 ID 只能受理一次，包括拒绝、失败和取消的请求；超限需关闭再打开。每页最多一个在途请求，避免排队或同时弹出任意数量的授权窗。授权等待最多 60 秒，操作执行默认最多 10 秒，均为协作式超时。

## 授权与撤销

身份由宿主固定为插件 ID、版本、运行代次、随机文档来源与 `android:local` 环境；动作和目标必须精确匹配登记的能力。不开放文件通配符、任意参数、Shell、凭证或全权能力。

原生授权弹窗携带随机令牌；旧弹窗令牌不能批准后来的请求。单次授权只执行被冻结的当前请求；持续授权限定同一文档、同一动作与目标，按单调时钟最多 5 分钟，不落盘。新页面、新版本、新代次、进程重启没有旧授权。插件停止时同步关闭代次准入，尚未销毁的页面也不能新发起调用或批准旧弹窗。

撤销清空授权、关闭待确认窗口并取消在途请求；页面关闭永久作废该文档会话并销毁其专用 WebView。执行器不配合取消时，不保证按时停止；撤销不能回滚已提交的副作用，也不保证底层动作 exactly-once。这里提供应用层代理门控，不是同 uid 恶意代码隔离。

共享 HostBridge 的旧 key、进程插件、Agent 与浏览器 MCP 不使用此文档会话；本片不改变这些路径的权限。外部 Web 包、受管理本地服务、长期授权、开发者全权模式及共享 key 迁移仍待实现。

## 本地验证

```powershell
$env:JAVA_HOME="D:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :plugin:runtime:test :plugin:ui:testDebugUnitTest :feature:navigation:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug architectureCheck --continue --console=plain
```

测试覆盖来源/目标越权、主帧限制、单次/限时授权、版本/代次/环境隔离、旧令牌、请求重放、并发和容量、撤销取消、超时、错误信息、有界静态资源及实际导航装配。Robolectric 资源测试不运行 Chromium；WebView 消息回调、CSP、真机页面与渲染进程退出仍需设备验收。测试不调用模型 API。

2026-10-18 上述专项命令通过，新增 25 项测试通过；Debug APK 和架构检查通过。另尝试全库 `test`，因既有 DataStore API 29 测试下载 Robolectric 镜像阻塞而中止，尚无本轮全库通过结论。示例 JS 的中英文请求、去重、成功/拒绝处理已通过本地模拟消息通道冒烟检查。
