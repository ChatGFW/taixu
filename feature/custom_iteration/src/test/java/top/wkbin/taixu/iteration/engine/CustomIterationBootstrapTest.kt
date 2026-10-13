package top.wkbin.taixu.iteration.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CustomIterationBootstrap 纯 JVM 单元测试：
 * 工作区落点、Skill / 工作流落盘、幂等、锚点校验与资产缺失失败关闭。
 */
class CustomIterationBootstrapTest {

    private fun tempRoot(prefix: String): File =
        File(System.getProperty("java.io.tmpdir"), "taixu-it-$prefix-${System.nanoTime()}").also { it.mkdirs() }

    private val skillBytes = "# taixu-custom-iteration\n\n真实 Skill 内容\n".toByteArray()

    private val workflowText = """
        name: Build TaiXuDev APK
        env:
          TAIXU_DEV_BUILD: "1"
        packages: 'platform-tools'
    """.trimIndent().toByteArray()

    private fun assets(
        skill: ByteArray? = skillBytes,
        workflow: ByteArray? = workflowText,
    ) = AssetReader { path ->
        when (path) {
            CustomIterationBootstrap.ASSET_SKILL -> skill
            CustomIterationBootstrap.ASSET_WORKFLOW -> workflow
            else -> null
        }
    }

    private fun paths(home: File, attachments: File, workspace: File) =
        CustomIterationPaths(hostHomeDir = home, attachmentsDir = attachments, workspaceDir = workspace)

    @Test
    fun `bootstrap creates the workspace under the proot bound home dir`() {
        val home = tempRoot("home")
        val attachments = tempRoot("attachments")
        val ws = tempRoot("ws")

        val result = CustomIterationBootstrap.bootstrap(
            assets = assets(),
            paths = paths(home, attachments, ws),
        )

        assertTrue("bootstrap must succeed: ${result.errorMessage}", result.success)
        val expected = File(home, CustomIterationBootstrap.WORKSPACE_NAME)
        assertTrue("workspace dir must exist at $expected", expected.isDirectory)
        assertEquals(expected.absolutePath, result.workspacePath)
        // guest 视角必须是 ~/custom_taixu，与 PRoot 的 homeDir→/root 绑定一致
        assertTrue(result.prompt.contains("/root/custom_taixu"))
    }

    @Test
    fun `skill and workflow land on the real scan roots`() {
        val home = tempRoot("home")
        val attachments = tempRoot("attachments")
        val ws = tempRoot("ws")

        assertTrue(CustomIterationBootstrap.bootstrap(assets(), paths(home, attachments, ws)).success)

        // 扫描根 1：<attachments>/skills → guest /attachments/skills
        val globalSkill = File(attachments, CustomIterationBootstrap.SKILL_RELATIVE_IN_SCAN_ROOT)
        assertTrue("global skill must exist: $globalSkill", globalSkill.isFile)
        assertTrue(globalSkill.readBytes().contentEquals(skillBytes))

        // 扫描根 2：<workspaceDir>/skills → guest /workspace/skills
        val workspaceRootSkill = File(ws, CustomIterationBootstrap.SKILL_RELATIVE_IN_SCAN_ROOT)
        assertTrue("workspace-root skill must exist: $workspaceRootSkill", workspaceRootSkill.isFile)
        assertTrue(workspaceRootSkill.readBytes().contentEquals(skillBytes))

        // 扫描根 3：迭代工作区内项目级 .agents/skills/<name>/SKILL.md
        val projectSkill = File(home, "${CustomIterationBootstrap.WORKSPACE_NAME}/${CustomIterationBootstrap.AGENT_SKILL_RELATIVE}")
        assertTrue("project skill must exist: $projectSkill", projectSkill.isFile)

        // 工作流落到 .github/workflows，Fork 后可直接被 Actions 识别
        // 工作流属于被克隆的迭代仓库：~/custom_taixu/.github/workflows/
        val workflow = File(
            home,
            "${CustomIterationBootstrap.WORKSPACE_NAME}/${CustomIterationBootstrap.WORKFLOW_RELATIVE}",
        )
        assertTrue("workflow must exist: $workflow", workflow.isFile)
        val text = workflow.readText()
        assertTrue(text.contains("packages: 'platform-tools'"))
        assertTrue(text.contains("TAIXU_DEV_BUILD"))
    }

    @Test
    fun `bootstrap is idempotent and refreshes changed content`() {
        val home = tempRoot("home")
        val attachments = tempRoot("attachments")
        val ws = tempRoot("ws")
        val p = paths(home, attachments, ws)

        assertTrue(CustomIterationBootstrap.bootstrap(assets(), p).success)

        val globalSkill = File(attachments, CustomIterationBootstrap.SKILL_RELATIVE_IN_SCAN_ROOT)
        val stamp = globalSkill.lastModified()

        // 内容一致 → 不改动 mtime
        assertTrue(CustomIterationBootstrap.bootstrap(assets(), p).success)
        assertEquals(stamp, globalSkill.lastModified())

        // 内容被外部改动 → 覆盖回资产内容，保证随应用版本升级同步
        globalSkill.writeText("tampered")
        assertTrue(CustomIterationBootstrap.bootstrap(assets(), p).success)
        assertTrue(globalSkill.readBytes().contentEquals(skillBytes))
    }

    @Test
    fun `missing skill asset fails closed`() {
        val home = tempRoot("home")
        val attachments = tempRoot("attachments")
        val ws = tempRoot("ws")

        val result = CustomIterationBootstrap.bootstrap(
            assets = assets(skill = null),
            paths = paths(home, attachments, ws),
        )

        assertFalse("must not report success when the skill asset is missing", result.success)
        assertTrue(result.errorMessage?.contains("缺少内置资产") == true)
        assertTrue(result.errorMessage?.contains(CustomIterationBootstrap.ASSET_SKILL) == true)
        assertTrue("prompt must stay empty on failure", result.prompt.isEmpty())
    }

    @Test
    fun `workflow without the build marker is rejected`() {
        val home = tempRoot("home")
        val attachments = tempRoot("attachments")
        val ws = tempRoot("ws")

        val result = CustomIterationBootstrap.bootstrap(
            assets = assets(workflow = "name: not-the-dev-workflow\n".toByteArray()),
            paths = paths(home, attachments, ws),
        )

        assertFalse("a wrong workflow must not be accepted", result.success)
        assertTrue(result.errorMessage?.contains("TAIXU_DEV_BUILD") == true)
    }

    @Test
    fun `unwritable skill location fails closed instead of pretending success`() {
        val home = tempRoot("home")
        val attachments = tempRoot("attachments")
        val ws = tempRoot("ws")

        // 用普通文件占据 <attachments>/skills，mkdirs 必然失败
        File(attachments, "skills").writeText("blocker")

        val result = CustomIterationBootstrap.bootstrap(assets(), paths(home, attachments, ws))

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("缺少内置资产") == true)
    }

    @Test
    fun `prompt carries the fork repo and stays renderable`() {
        val prompt = CustomIterationBootstrap.bootstrapPrompt(
            guestWorkspace = CustomIterationBootstrap.GUEST_WORKSPACE,
            forkRepo = "haotaoran520/taixu",
        )
        assertTrue(prompt.contains("haotaoran520/taixu"))
        assertTrue(prompt.contains("https://github.com/wkbin/taixu"))
        assertTrue(prompt.contains(".github/workflows/taixudev-build.yml"))
        // 模板插值必须已全部展开
        assertFalse(prompt.contains("\${"))
        assertFalse(prompt.contains("OFFICIAL_REPO_SLUG"))
        assertFalse(prompt.contains("WORKSPACE_NAME"))
    }
}
