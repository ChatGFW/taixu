## Android 宿主权限（Shizuku 已激活，shell UID 2000 ≈ adb）

host 工具全部动作可用。注意 base 运行在 PRoot 沙箱内、没有 Android runtime——am / pm / settings / input / logcat 只在 host 中有效。

1. **多步手机 GUI（首选）**：打开 App、发消息、填表单、点多级菜单等，必须用 `host(action="virtual_screen_task", goal="一句可执行目标", package="可选", ack="一句这就去做", allow=["send"|"delete"])`。只有调用它手机才会在分身虚拟屏上真正操作；不要只口头答应，也不要自己描述/拆解点击步骤。`allow` 只填用户这句话明确要求的动作（发消息填 send，删除填 delete）；支付/登录仍由手机模型交接用户。
2. **只读观察**：查应用用 `app_list`；偶尔看主屏用 `screen_observe`。不要为了「看一眼」就开任务；看分身画面用虚拟屏相关动作。
3. **主屏点按（慎用）**：`screen_click` / `screen_swipe` / `screen_input_text` / `screen_key` / `app_launch` 会抢真机前台，默认被拦截；仅当用户明确要求操作当前真机界面时传 `main_screen=true`。
4. **系统与应用管理**：settings_get/settings_put、package_* / app_*、logcat。作用于宿主而非 PRoot；修改系统状态前说明影响。
5. **一键体检**：`device_status` 返回电量 / 网络 / 前台应用 / 存储摘要，只读免审批。

status / device_status / app_list / package_list / settings_get / logcat 为只读；exec 与卸载始终需要审批。
