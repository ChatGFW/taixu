package top.wkbin.taixu.runtime.droidspaces

import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.model.StorageMountBinding
import top.wkbin.taixu.runtime.EnvironmentResolver
import top.wkbin.taixu.runtime.backend.ModeBackendSelector
import top.wkbin.taixu.runtime.proot.ProotCommandBuilder
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Droidspaces 后端参数构造的纯函数测试。
 *
 * 只验证 argv 形态（可离线复算），不验证真机行为——容器是否真能跑由 P0/P5 门禁与真机验证覆盖。
 */
class DroidspacesCommandBuilderTest {

    private val rootfsDir = File("/data/data/app/files/linux-runtime/distros/ubuntu/rootfs")
    private val workspaceDir = File("/data/data/app/files/linux-runtime/workspace")
    private val optDir = File("/data/data/app/files/linux-runtime/distros/ubuntu/opt/taixu")
    private val tmpDir = File("/data/data/app/files/linux-runtime/tmp")
    private val attachmentsDir = File("/data/data/app/files/linux-runtime/attachments")

    private fun builder() = DroidspacesCommandBuilder(EnvironmentResolver())

    private fun build(commandLine: String = "true"): List<String> = builder().build(
        prootBinary = File("/data/data/app/files/linux-runtime/bin/prisma"),
        rootfsDir = rootfsDir,
        workspaceDir = workspaceDir,
        homeDir = File("/data/data/app/files/linux-runtime/distros/ubuntu/home"),
        optDir = optDir,
        tmpDir = tmpDir,
        attachmentsDir = attachmentsDir,
        command = ShellCommand(commandLine = commandLine),
        mounts = emptyList(),
        emulatorBinary = null,
        hostCredential = emptyMap(),
    )

    @Test
    fun buildWrapsEverythingInSuDashC() {
        val argv = build()
        assertEquals(2, argv.size)
        assertEquals("su", argv[0])
        assertEquals("-c", argv[1])
    }

    @Test
    fun hostScriptRunsAsRootInTheDistroContainer() {
        val script = build()[1]
        assertTrue(script.contains("run -u root --"))
        // 容器名由 distro id 派生，rootfsDir 的父目录名即 distro id
        assertTrue(script.contains("taixu-ubuntu"))
    }

    @Test
    fun containerIsStartedWithRootfsWhenNotYetRunning() {
        val script = build()[1]
        assertTrue("必须先确保容器在跑，否则 run 会失败", script.contains("start --name="))
        assertTrue(script.contains(rootfsDir.absolutePath))
    }

    @Test
    fun guestDirectoryViewMatchesProotBackend() {
        val script = build()[1]
        // 这四项若缺失，插件与工具链在容器内将完全不可见
        assertTrue(script.contains("${workspaceDir.absolutePath}:/workspace"))
        assertTrue(script.contains("${optDir.absolutePath}:/opt/taixu"))
        assertTrue(script.contains("${tmpDir.absolutePath}:/tmp"))
        assertTrue(script.contains("${attachmentsDir.absolutePath}:/attachments"))
    }

    @Test
    fun commandIsExecutedInRequestedWorkingDirectory() {
        val script = build(commandLine = "ls -la")[1]
        assertTrue(script.contains("cd -- "))
        assertTrue(script.contains("exec ls -la"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun qemuCompatibilityIsRejectedInsteadOfSilentlyFailing() {
        builder().build(
            prootBinary = File("/p"),
            rootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            homeDir = File("/h"),
            optDir = optDir,
            tmpDir = tmpDir,
            attachmentsDir = attachmentsDir,
            command = ShellCommand(commandLine = "true"),
            mounts = emptyList(),
            emulatorBinary = File("/qemu-x86_64"),
            hostCredential = emptyMap(),
        )
    }

    @Test
    fun disabledStorageMountIsNotForwarded() {
        val argv = builder().build(
            prootBinary = File("/p"),
            rootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            homeDir = File("/h"),
            optDir = optDir,
            tmpDir = tmpDir,
            attachmentsDir = attachmentsDir,
            command = ShellCommand(commandLine = "true"),
            mounts = listOf(
                StorageMountBinding(
                    hostPath = "/storage/emulated/0/Download",
                    guestPath = "/mnt/download",
                    enabled = false,
                ),
            ),
            emulatorBinary = null,
            hostCredential = emptyMap(),
        )
        assertFalse(argv[1].contains("/mnt/download"))
    }

    @Test
    fun illegalStorageMountIsSkippedNotCrashing() {
        val argv = builder().build(
            prootBinary = File("/p"),
            rootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            homeDir = File("/h"),
            optDir = optDir,
            tmpDir = tmpDir,
            attachmentsDir = attachmentsDir,
            command = ShellCommand(commandLine = "true"),
            mounts = listOf(
                // 容器路径不在 /mnt 或 /sdcard 内 → 必须被过滤，不能注入 argv
                StorageMountBinding(hostPath = "/storage/emulated/0/Download", guestPath = "/etc"),
            ),
            emulatorBinary = null,
            hostCredential = emptyMap(),
        )
        assertFalse(argv[1].contains("/etc"))
    }

    @Test
    fun interactiveSessionCarriesBannerAndGeometry() {
        val argv = builder().buildInteractive(
            prootBinary = File("/p"),
            rootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            homeDir = File("/h"),
            optDir = optDir,
            tmpDir = tmpDir,
            attachmentsDir = attachmentsDir,
            config = SessionConfig(columns = 120, rows = 40, showBanner = true, usePty = true),
            ptyMarker = null,
            nativePty = false,
            mounts = emptyList(),
            emulatorBinary = null,
            hostCredential = emptyMap(),
        )
        val script = argv[1]
        assertTrue(script.contains(DroidspacesCommandBuilder.GUEST_MOTD))
        assertTrue(script.contains("stty cols 120 rows 40"))
    }

    @Test
    fun protocolSessionStaysOnPlainPipe() {
        val argv = builder().buildInteractive(
            prootBinary = File("/p"),
            rootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            homeDir = File("/h"),
            optDir = optDir,
            tmpDir = tmpDir,
            attachmentsDir = attachmentsDir,
            config = SessionConfig(commandLine = "/bin/sh", usePty = false, showBanner = false),
            ptyMarker = null,
            nativePty = false,
            mounts = emptyList(),
            emulatorBinary = null,
            hostCredential = emptyMap(),
        )
        val script = argv[1]
        // MCP STDIO 会话不得混入 banner 与 stty，否则污染协议输出流
        assertFalse(script.contains(DroidspacesCommandBuilder.GUEST_MOTD))
        assertFalse(script.contains("stty cols"))
    }

    @Test
    fun selectorRoutesToProotUntilRootModeIsSelected() {
        runBlocking {
            val modes = MutableStateFlow(ExecutionMode.PROOT)
            val selector = ModeBackendSelector(
                proot = ProotCommandBuilder(EnvironmentResolver()),
                droidspaces = builder(),
                modes = modes,
            )
            // 模式取自 Flow 的当前值，不依赖任何调度器上的异步收集器，因此无竞态。
            assertEquals("/data/app/proot", sampleArgv(selector).first())

            modes.value = ExecutionMode.ROOT
            assertEquals("su", sampleArgv(selector).first())
        }
    }

    /** 用同一组入参驱动后端，断言「同一份输入在不同模式下走不同后端」。 */
    private fun sampleArgv(selector: ModeBackendSelector): List<String> = runBlocking {
        selector.active().build(
            prootBinary = File("/data/app/proot"),
            rootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            homeDir = File("/h"),
            optDir = optDir,
            tmpDir = tmpDir,
            attachmentsDir = attachmentsDir,
            command = ShellCommand(commandLine = "true"),
            mounts = emptyList(),
            emulatorBinary = null,
            hostCredential = emptyMap(),
        )
    }
}
