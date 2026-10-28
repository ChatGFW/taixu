# GitHub 本地插件

无需把 GitHub 业务代码编译进 APK。宿主需包含 `SERVICE_WRITE` 与认证服务协议，需先更新本次 APK。请使用 **1.0.1**，停用旧 1.0.0 后端；旧版公开 nonce 的校验不足以保护本地 HTTP 写入。

## 安装

1. 在本地 Linux 发行版准备 `python3`、`git`、`openssh-client`、`ca-certificates`。Debian/Ubuntu 可在终端执行 `apt-get update && apt-get install -y python3 git openssh-client ca-certificates`。插件安装配方本身不下载、不调用包管理器。
2. 设置 → 本地插件/工具中心，导入 `dist/plugins/github-1.0.1.txplugin` 并安装；在工具中心启动 GitHub 服务。
3. 设置 → 应用功能插件 → 外部 Web 插件，导入 `dist/plugins/github-web-1.0.1.txplugin`，打开页面。页面使用 `TOOL_SERVICE` 连接已运行的认证服务，关闭页面不会停止服务和任务。停止服务请回到工具中心操作。
4. 允许读取本页服务，在 Token 表单输入 GitHub Token，点击验证并保存，原生确认本次服务写入。页面不返回 Token。
5. 设置 Git 用户名和邮箱。克隆 `owner/repo`，在终端/编辑器修改文件，再在页面创建新分支、提交全部修改并推送，最后创建 PR。

## Token 与 SSH

- Fine-grained PAT：选择目标仓库，按需授予 Contents 与 Pull requests 读写。组织仓库可能还需要组织审批。`gh` 使用 `GH_TOKEN`，兼容细粒度 Token，不强制 classic scopes。列仓库仅显示 Token 可访问的结果。
- SSH：生成 Ed25519 密钥，将公钥上传或手动复制到 `https://github.com/settings/ssh/new`，同步 GitHub 主机公钥后选择 SSH 克隆。主机公钥来自 GitHub HTTPS `/meta`，不关闭主机校验。私钥无口令，生成前页面明确说明，不覆盖已有密钥。
- SSH 只用于 Git 传输；GitHub API 与 PR 仍需要 Token。自动上传 SSH 公钥通常需要 classic Token 的 `admin:public_key` 权限；权限不足可手动上传，不要求为此扩大仓库 Token 权限。
- Token 明文保存在 `/opt/taixu/data/github/token`，权限 0600；数据目录 0700。这不是 AndroidKeyStore，也不是同 UID 的隔离边界。私钥位于该目录 `ssh/id_ed25519`，只返回 `.pub`。
- 删除本地 Token 不会吊销 GitHub 上的 Token，也不会删除 SSH 私钥或 GitHub 上的公钥。普通卸载默认保留数据；需吊销时到 GitHub 设置处理。

## 终端

`gh` 是官方 GitHub CLI 的封装；`gh-git` 是使用相同 Token、SSH 和插件专用 Git 配置的 Git 封装。普通 `git` 不会自动使用插件配置。

```sh
gh repo list
gh pr list --repo owner/repo
cd /opt/taixu/data/github/repos/owner/repo
gh-git status
gh-git push -u origin feature/my-change
gh pr create --title '变更标题' --body '变更说明'
```

提交并推送按钮创建一个新分支，包含该仓库全部未忽略修改；不覆盖已有分支，不强推。网络失败可能保留本地分支/提交，使用终端检查并继续推送，勿重复点击新建分支。无修改时提交会失败。PR 使用当前已推送分支和远端默认目标分支。克隆范围限插件数据目录；不接受任意路径、URL 或 Shell。

## 运行边界

- 服务只监听 `127.0.0.1:18765`；静态资源通过原生来源代理，页面不直接联网，不放宽 CSP。
- 页面通过 `host.service-write` 提交 `相对路径 + 换行 + JSON`。必须在 WEB 包声明 `SERVICE_WRITE/service`，每次原生确认，正文可能含凭据所以确认只展示操作路径，消息最多 1024 UTF-16 单元。
- PROCESS 声明 `LOCAL_WEB/authenticated-v1`，工具中心为每个进程生成 256 位密钥，只通过 stdin 启动握手下发。全部 HTTP 资源、读取与写入均需 HMAC-SHA256；签名绑定时间、随机请求 ID、方法、路径和正文摘要，服务拒绝过期与重放。响应也签名并绑定请求 ID，宿主拒绝伪造服务内容。密钥不进入环境、URL、页面、响应或日志；仅知道端口、旧 nonce 或自定义头无法访问服务。仍不隔离同 UID 恶意代码。
- 后端仅支持白名单操作；POST 另要求原生写入头且无 Origin，不开放 CORS、任意文件读取、Token 导出或 Shell API。页面写入仅支持工具中心托管的认证服务，不能回退到未认证服务或页面自启服务。
- 长任务由工具中心服务后台执行，关闭配置页后可重开继续查看。最近一项任务的 ID、状态和结果以 0600 原子保存到 `last-job.json`，不保存表单、Token 或私钥。服务重启时，未完成任务标记为“结果待核对”，不自动重放；完成结果可恢复。原生撤销不能回滚已接受或完成的 Git/GitHub 操作，不承诺远端 exactly-once。
- 轮询使用 1/2/4/8/10 秒退避，保留页面配额余量；达到余量时提示安全重开页面，服务继续运行。长时间未完成时可稍后刷新，勿重复提交。
- 已通过离线 mock 回归和包校验；真实 ARM64 PRoot、WebView、GitHub 授权/推送需在手机验收。本地没有使用真实 Token、账号或付费模型。

## 可复现打包

固定官方 gh **2.103.3**，Linux ARM64 归档 SHA-256：
`ee32df2323c8847f5fac6cb6b4b69ca75828e9329a8f22f677021f7b3306ce63`。

来源：`https://github.com/cli/cli/releases/tag/v2.103.3`。随包包含上游 MIT LICENSE。

```powershell
python tools/package-github-plugin.py
# 有缓存时不联网；也可 --archive 指定已下载的官方 tar.gz。
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :plugin:cli:installDist
plugin\cli\build\install\taixu-plugin\bin\taixu-plugin.bat inspect dist/plugins/github-1.0.1.txplugin
plugin\cli\build\install\taixu-plugin\bin\taixu-plugin.bat inspect dist/plugins/github-web-1.0.1.txplugin
python -m unittest discover -s scripts/tests -p test_github_plugin.py -v
node scripts/tests/test_github_web.cjs
node scripts/tests/test_github_web_tasks.cjs
```
