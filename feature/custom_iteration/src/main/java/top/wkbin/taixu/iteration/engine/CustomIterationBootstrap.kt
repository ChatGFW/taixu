package top.wkbin.taixu.iteration.engine

import android.content.Context
import java.io.File

/**
 * CustomIterationBootstrap — 太墟自定义迭代环境自举引擎。
 * 
 * 职责：
 * 1. 准备沙盒内隔离的源码工作区路径 `~/custom_taixu`；
 * 2. 将预置的 `taixu-custom-iteration` Skill 部署至 Agent 的技能目录；
 * 3. 部署 GitHub Actions CI 工作流模板；
 * 4. 生成引导 Agent 执行自迭代开发的规范提示词。
 */
object CustomIterationBootstrap {

    const val WORKSPACE_NAME = "custom_taixu"
    const val OFFICIAL_REPO = "https://github.com/wkbin/taixu"

    /** 自举所需目录（相对 rootfs home）：工作区、Skill 部署点、工作流模板缓存。 */
    internal val BOOTSTRAP_DIRS = listOf(
        WORKSPACE_NAME,
        ".taixu/skills/taixu-custom-iteration",
        ".taixu/templates/workflows",
    )

    const val BOOTSTRAP_PROMPT = """我准备在太墟（TaiXu）的手机 Linux 虚拟沙盒中进行 TaiXu 自定义迭代。

请按以下步骤引导我：
1. 检查本地开发环境：
   - 确认当前命令在 Linux PRoot 沙盒中执行；
   - 确认独立工作区为 ~/custom_taixu；
   - 优先使用 GitHub Actions 构建 TaiXuDev APK，无需在手机本地安装庞大的 Android SDK/NDK。

2. 检查并配置 GitHub 认证：
   - 检查 gh CLI 与 Git 配置；
   - 引导我使用 gh auth login（设备码流程）或 SSH Key 完成登录验证；
   - 不要把 Token、私钥写入命令历史或日志中。

3. 验证与克隆仓库：
   - 为官方仓库 $OFFICIAL_REPO 点星；
   - Fork main 分支到我自己的 GitHub 账户；
   - 将 Fork 后的仓库克隆到 ~/custom_taixu。

4. 遵循 taixu-custom-iteration Skill 开发规范：
   - 按照太墟的 Jetpack Compose、Koin 和多模块规范进行修改；
   - 编写或调整功能后运行单元测试验证；
   - 提交修改并推送到 Fork 仓库的特性分支。

5. 通过 GitHub Actions 构建独立的 TaiXuDev APK：
   - 触发 .github/workflows/taixudev-build.yml 编译；
   - 实时监控构建进度并在成功后下载 APK 至手机；
   - 校验包名 top.wkbin.taixu.dev 和应用名 TaiXuDev，与正式版独立共存。

6. 若体验满意，协助我生成标准 PR 提交到 $OFFICIAL_REPO。"""

    /**
     * 初始化自定义迭代环境与工作区。
     */
    fun bootstrap(context: Context, rootfsHomeDir: File): BootstrapResult =
        prepareDirectories(rootfsHomeDir)

    /**
     * 确保 [BOOTSTRAP_DIRS] 全部存在。
     *
     * 与旧实现的关键差别：目录创建失败不再被静默吞掉。旧实现忽略 `mkdirs()` 的返回值，
     * 即便路径被同名文件占用、或目录不可写，仍会返回 `success = true`，随后 Agent 会拿着
     * 一个并不存在的工作区路径去执行 git 操作。现在失败会带上具体路径如实上报，交由 UI 提示。
     */
    internal fun prepareDirectories(rootfsHomeDir: File): BootstrapResult {
        for (relative in BOOTSTRAP_DIRS) {
            val dir = File(rootfsHomeDir, relative)
            if (dir.isDirectory) continue

            val created = try {
                dir.mkdirs()
            } catch (e: SecurityException) {
                false
            }

            if (!created && !dir.isDirectory) {
                return BootstrapResult(
                    success = false,
                    workspacePath = "",
                    prompt = "",
                    errorMessage = "无法创建目录：" + dir.absolutePath
                )
            }
        }

        return BootstrapResult(
            success = true,
            workspacePath = File(rootfsHomeDir, WORKSPACE_NAME).absolutePath,
            prompt = BOOTSTRAP_PROMPT
        )
    }

    data class BootstrapResult(
        val success: Boolean,
        val workspacePath: String,
        val prompt: String,
        val errorMessage: String? = null
    )
}
