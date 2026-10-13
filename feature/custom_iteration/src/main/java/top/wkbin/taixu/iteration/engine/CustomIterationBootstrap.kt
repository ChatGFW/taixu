package top.wkbin.taixu.iteration.engine

import java.io.File

/**
 * CustomIterationBootstrap — 太墟自定义迭代环境自举引擎。
 *
 * 职责：
 * 1. 在 PRoot 沙盒可写区内准备隔离源码工作区 `~/custom_taixu`；
 * 2. 把模块自带的 `taixu-custom-iteration` Skill 落盘到 Agent 的**真实扫描根**，
 *    使 Agent 无需人工复制即可自动发现该规范；
 * 3. 把 GitHub Actions 工作流落到工作区的 `.github/workflows/`，供 Fork 后直接复用；
 * 4. 生成引导 Agent 执行自迭代开发的规范提示词。
 *
 * 路径契约（与 [top.wkbin.taixu.runtime.RuntimePathManager] / PRoot 绑定一致）：
 * - 宿主 `pathManager.homeDir(<distro>)` 被 PRoot 绑定到 guest `/root`，
 *   因此工作区宿主路径 = `homeDir/custom_taixu`，guest 视角即 `~/custom_taixu`；
 * - Agent Skill 自动扫描根为 `pathManager.attachmentsDir/skills`（guest `/attachments/skills`）
 *   与工作区内 `.agents/skills/`（`SkillScanRoot.standardScanRoots`）。
 *
 * 本类不做任何 `filesDir` 相对路径推断，也直接依赖 assets 内容而非 Context，
 * 全部经 [AssetReader] 与 [CustomIterationPaths] 注入，避免硬编码漂移并保持可测。
 */
object CustomIterationBootstrap {

    const val WORKSPACE_NAME = "custom_taixu"
    const val OFFICIAL_REPO = "https://github.com/wkbin/taixu"
    const val OFFICIAL_REPO_SLUG = "wkbin/taixu"
    const val DEFAULT_BRANCH = "main"

    /** 模块 assets 中的 Skill 名称与工作流文件名。 */
    const val SKILL_NAME = "taixu-custom-iteration"
    const val WORKFLOW_FILE = "taixudev-build.yml"

    /** assets 内源路径（随 feature:custom_iteration 模块合并进 APK）。 */
    const val ASSET_SKILL = "skills/$SKILL_NAME/SKILL.md"
    const val ASSET_WORKFLOW = "templates/workflows/$WORKFLOW_FILE"

    /** 工作区内相对路径。 */
    const val WORKFLOW_RELATIVE = ".github/workflows/$WORKFLOW_FILE"
    const val AGENT_SKILL_RELATIVE = ".agents/skills/$SKILL_NAME/SKILL.md"

    /**
     * Skill 相对路径，用于挂到各扫描根之下。
     *
     * 三个落点对应 `SkillScanRoot.standardScanRoots` 里的真实可见位置：
     * `<attachmentsDir>/skills`（guest `/attachments/skills`）、
     * `<workspaceDir>/skills`（guest `/workspace/skills`），
     * 以及迭代工作区内的项目级 `.agents/skills/` 规范。
     */
    const val SKILL_RELATIVE_IN_SCAN_ROOT = "skills/$SKILL_NAME/SKILL.md"

    /** guest 视角的绝对路径，用于生成提示词。 */
    const val GUEST_HOME = "/root"
    const val GUEST_ATTACHMENTS_SKILLS = "/attachments/skills"

    /** 工作区在 guest 视角的绝对路径，即 PRoot 里 `~/$WORKSPACE_NAME`。 */
    const val GUEST_WORKSPACE = "$GUEST_HOME/$WORKSPACE_NAME"

    /** 工作流模板里的关键锚点，用于校验落盘内容确为可用模板。 */
    private const val WORKFLOW_REQUIRED_MARKER = "TAIXU_DEV_BUILD"

    /**
     * 生成引导 Agent 执行自迭代开发的提示词。
     *
     * 路径与仓库名全部来自调用方，Fork 后不再写死官方仓库：
     * 云端构建用 `-R <主人账户>/taixu`，PR 才回官方仓库。
     */
    fun bootstrapPrompt(guestWorkspace: String, forkRepo: String): String =
        """我准备在太墟（TaiXu）的手机 Linux 虚拟沙盒中进行 TaiXu 自定义迭代。

请按以下步骤引导我：
1. 检查本地开发环境：
   - 确认当前命令在 Linux PRoot 沙盒中执行（guest 视角工作区 = $guestWorkspace）；
   - 确认独立工作区为 ~/$WORKSPACE_NAME；
   - 优先使用 GitHub Actions 构建 TaiXuDev APK，无需在手机本地安装庞大的 Android SDK/NDK。

2. 检查并配置 GitHub 认证：
   - 检查 gh CLI 与 Git 配置；
   - 引导我使用 gh auth login（设备码流程）或 SSH Key 完成登录验证；
   - 不要把 Token、私钥写入命令历史或日志中。

3. 验证与克隆仓库：
   - 为官方仓库 $OFFICIAL_REPO 点星；
   - Fork $DEFAULT_BRANCH 分支到我的 GitHub 账户（本机已配置为 $forkRepo）；
   - 将 Fork 后的仓库克隆到 ~/$WORKSPACE_NAME。

4. 遵循 $SKILL_NAME Skill 开发规范：
   - 按照太墟的 Jetpack Compose、Koin 和多模块规范进行修改；
   - 编写或调整功能后运行单元测试验证（./gradlew testDebugUnitTest）；
   - 提交修改并推送到 Fork 仓库的特性分支。

5. 通过 GitHub Actions 构建独立的 TaiXuDev APK：
   - 触发 $WORKFLOW_RELATIVE 编译（仓库 = $forkRepo，Fork 仓库才有 workflow_dispatch 权限）；
   - 实时监控构建进度并在成功后下载 APK 至手机；
   - 校验包名 top.wkbin.taixu.dev 和应用名 TaiXuDev，与正式版独立共存。

6. 若体验满意，协助我生成标准 PR 提交到 $OFFICIAL_REPO。"""

    /**
     * 初始化自定义迭代环境与工作区。
     *
     * @param assets 资产读取源（生产环境为 APK assets）
     * @param paths 运行时路径集合（来自 `RuntimePathManager`）
     * @param forkRepo 主人的 Fork 仓库（`owner/name`），用于云端构建目标
     */
    fun bootstrap(
        assets: AssetReader,
        paths: CustomIterationPaths,
        forkRepo: String = OFFICIAL_REPO_SLUG,
    ): BootstrapResult {
        val missing = mutableListOf<String>()
        return try {
            // 1. 隔离工作区：宿主 homeDir/custom_taixu ←PRoot bind→ guest /root/custom_taixu
            val workspace = File(paths.hostHomeDir, WORKSPACE_NAME)
            if (!workspace.isDirectory && !workspace.mkdirs()) {
                return BootstrapResult(
                    success = false,
                    workspacePath = "",
                    prompt = "",
                    errorMessage = "无法创建迭代工作区：${workspace.absolutePath}",
                )
            }

            // 2. Skill 落盘到全部真实扫描根：全局 attachments、Agent 工作区、迭代工作区项目级
            deploy(assets, ASSET_SKILL, File(paths.attachmentsDir, SKILL_RELATIVE_IN_SCAN_ROOT), missing)
            deploy(assets, ASSET_SKILL, File(paths.workspaceDir, SKILL_RELATIVE_IN_SCAN_ROOT), missing)
            deploy(assets, ASSET_SKILL, File(workspace, AGENT_SKILL_RELATIVE), missing)

            // 3. 工作流落盘：Fork 后直接可被 GitHub Actions 识别
            deploy(assets, ASSET_WORKFLOW, File(workspace, WORKFLOW_RELATIVE), missing)

            if (missing.isNotEmpty()) {
                // 资产缺失属于打包错误，必须显式失败而不是静默给出「就绪」的假象
                BootstrapResult(
                    success = false,
                    workspacePath = workspace.absolutePath,
                    prompt = "",
                    errorMessage = "缺少内置资产：${missing.joinToString(", ")}",
                )
            } else {
                BootstrapResult(
                    success = true,
                    workspacePath = workspace.absolutePath,
                    prompt = bootstrapPrompt(GUEST_WORKSPACE, forkRepo),
                )
            }
        } catch (e: Exception) {
            BootstrapResult(
                success = false,
                workspacePath = "",
                prompt = "",
                errorMessage = e.message ?: "Bootstrap failed",
            )
        }
    }

    /**
     * 把 assets 中的 [assetPath] 写为 [target]。
     *
     * 内容一致时跳过写入（不改 mtime）；内容不同则覆盖，
     * 保证 Skill / 工作流随应用版本升级同步。模板还会校验关键锚点，
     * 防止把空壳或错误文件当成可用工作流。
     */
    private fun deploy(
        assets: AssetReader,
        assetPath: String,
        target: File,
        missing: MutableList<String>,
    ) {
        val bytes = assets.read(assetPath)
        if (bytes == null || bytes.isEmpty()) {
            missing += assetPath
            return
        }
        if (assetPath == ASSET_WORKFLOW &&
            !String(bytes).contains(WORKFLOW_REQUIRED_MARKER)
        ) {
            missing += "$assetPath（缺少 $WORKFLOW_REQUIRED_MARKER 锚点）"
            return
        }
        if (target.isFile && target.length() == bytes.size.toLong()) {
            val existing = runCatching { target.readBytes() }.getOrNull()
            if (existing != null && existing.contentEquals(bytes)) return
        }
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            missing += "$assetPath（无法创建目录 ${parent.absolutePath}）"
            return
        }
        target.writeBytes(bytes)
    }

    data class BootstrapResult(
        val success: Boolean,
        val workspacePath: String,
        val prompt: String,
        val errorMessage: String? = null,
    )
}
