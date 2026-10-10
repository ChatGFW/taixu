package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

/**
 * 🔧 沙箱工具链探针定义 (Sandbox Toolchain Probe)
 *
 * 描述「沙箱里某个可执行工具」的期望存在性与最低版本要求。
 * 本文件是纯数据 + 纯函数模块：不依赖 Android、不依赖沙箱，
 * 因此可以在 JVM 单元测试里完整覆盖版本比较与状态判定逻辑。
 */
@Serializable
data class ToolchainProbe(
    val id: String,
    /** 展示名（中文），例如 "CMake"。 */
    val displayName: String,
    /** 用途说明，展示在面板里让用户知道为什么需要它。 */
    val purpose: String,
    /** 命令名探测：任一候选命令存在即视为已安装。 */
    val commands: List<String>,
    /** 候选绝对路径（离线套件优先），与 [commands] 任一命中即算就绪。 */
    val candidatePaths: List<String> = emptyList(),
    /** 取版本号的命令模板；{cmd} 替换为探测到的可执行路径，{path} 替换为候选路径。 */
    val versionCommand: String? = null,
    /** 最低可接受版本号（数字段比较，如 "3.22" 表示 >= 3.22）。 */
    val minVersion: String? = null,
    /** apt 包名，供一键补齐时安装。 */
    val aptPackages: List<String> = emptyList(),
    /** 该工具是否必须 root 才能实际驱动（工具本体可装，运行时才需要特权）。 */
    val requiresRootAtRuntime: Boolean = false,
    /** 分类，用于面板分组。 */
    val group: ToolchainGroup,
)

/** 工具链分组，面板按此渲染分区。 */
@Serializable
enum class ToolchainGroup(val displayName: String) {
    NATIVE_BUILD("原生构建链"),
    REVERSE_ENGINEERING("逆向分析"),
    DEBUG_INSPECT("调试与追踪"),
}

/** 单个工具的检测结论。 */
@Serializable
data class ToolchainProbeResult(
    val probe: ToolchainProbe,
    /** 实际命中的可执行路径，未命中为 null。 */
    val resolvedPath: String? = null,
    /** 解析出的版本号原文（未安装或无法解析时为 null）。 */
    val version: String? = null,
    val status: ToolchainStatus,
    /** 面向用户的中文结论。 */
    val summary: String,
)

/**
 * 工具状态。
 *
 * [MISSING] / [OUTDATED] 均视为「可自动补齐」——这正是本功能的核心：
 * 沙箱里缺工具、工具版本落后，都要能在 App 内一键补齐，而不是让用户自己去猜。
 * [NEEDS_ROOT] 表示工具本体可以装好，但真正驱动它需要 root 特权（PRoot 伪 root 不算），
 * 如实标注避免用户以为装完就能跑。
 */
@Serializable
enum class ToolchainStatus {
    READY,
    OUTDATED,
    MISSING,
    NEEDS_ROOT,
    UNKNOWN,
}

/** 一次全量检测的汇总。 */
@Serializable
data class ToolchainReport(
    val results: List<ToolchainProbeResult> = emptyList(),
    val checkedAt: Long = System.currentTimeMillis(),
) {
    val readyCount: Int get() = results.count { it.status == ToolchainStatus.READY }
    val outdatedCount: Int get() = results.count { it.status == ToolchainStatus.OUTDATED }
    val missingCount: Int get() = results.count { it.status == ToolchainStatus.MISSING }
    val needsRootCount: Int get() = results.count { it.status == ToolchainStatus.NEEDS_ROOT }
    val unknownCount: Int get() = results.count { it.status == ToolchainStatus.UNKNOWN }

    /** 需要补齐的工具（缺失 + 版本落后），按清单固定顺序展示。 */
    val repairable: List<ToolchainProbeResult>
        get() = results.filter { it.status == ToolchainStatus.MISSING || it.status == ToolchainStatus.OUTDATED }

    /** 补齐后依然需要 root 才能驱动的工具，单独提示，避免用户误以为已可用。 */
    val rootRequired: List<ToolchainProbeResult>
        get() = results.filter { it.status == ToolchainStatus.NEEDS_ROOT }

    val isAllReady: Boolean get() = results.isNotEmpty() && repairable.isEmpty()
}

/**
 * 🧰 沙箱工具链清单 —— 全量探针表（原生构建 / 逆向 / 调试）。
 *
 * 清单集中在此处声明，与 [BuiltinPluginBundles] 的组件探针互补：
 * 插件中心管「套件装没装」，这里管「工具在不在、版本够不够新」，两者口径独立。
 */
object SandboxToolchainCatalog {

    val probes: List<ToolchainProbe> = listOf(
        ToolchainProbe(
            id = "cmake",
            displayName = "CMake",
            purpose = "Android 原生模块（externalNativeBuild）构建器，缺失时 C/C++ 代码无法编译",
            commands = listOf("cmake"),
            candidatePaths = listOf("/opt/taixu/tools/android-suite-offline/cmake/bin/cmake"),
            versionCommand = "{cmd} --version",
            minVersion = "3.22",
            aptPackages = listOf("cmake", "ninja-build"),
            group = ToolchainGroup.NATIVE_BUILD,
        ),
        ToolchainProbe(
            id = "ninja",
            displayName = "Ninja",
            purpose = "CMake 的默认构建后端，缺失时 native 构建无法落地产物",
            commands = listOf("ninja"),
            candidatePaths = listOf("/opt/taixu/tools/android-suite-offline/cmake/bin/ninja"),
            versionCommand = "{cmd} --version",
            minVersion = "1.10",
            aptPackages = listOf("ninja-build"),
            group = ToolchainGroup.NATIVE_BUILD,
        ),
        ToolchainProbe(
            id = "patchelf",
            displayName = "patchelf",
            purpose = "改写 ELF 头信息（库名/rpath/解释器），SO 修补与注入必用",
            commands = listOf("patchelf"),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("patchelf"),
            group = ToolchainGroup.NATIVE_BUILD,
        ),
        ToolchainProbe(
            id = "aapt2",
            displayName = "aapt2",
            purpose = "资源打包与解析，资源表（arsc）分析必用",
            commands = listOf("aapt2", "aapt"),
            candidatePaths = listOf("/opt/android-sdk/build-tools/35.0.0/aapt2"),
            versionCommand = "{cmd} version",
            group = ToolchainGroup.NATIVE_BUILD,
        ),
        ToolchainProbe(
            id = "apksigner",
            displayName = "Apksigner",
            purpose = "APK 签名与重签名，自改包安装前必须过这一关",
            commands = listOf("apksigner"),
            candidatePaths = listOf(
                "/opt/android-sdk/build-tools/35.0.0/apksigner",
                "/usr/local/bin/apksigner",
            ),
            versionCommand = "{cmd} version",
            group = ToolchainGroup.NATIVE_BUILD,
        ),
        ToolchainProbe(
            id = "zipalign",
            displayName = "Zipalign",
            purpose = "APK 对齐优化，影响安装效率与体积",
            commands = listOf("zipalign"),
            versionCommand = "{cmd} 2>&1 | head -1",
            group = ToolchainGroup.NATIVE_BUILD,
        ),
        ToolchainProbe(
            id = "jadx",
            displayName = "JADX",
            purpose = "APK/Dex 反编译为可读 Java 源码，逆向主力工具",
            commands = listOf("jadx"),
            candidatePaths = listOf(
                "/opt/taixu/tools/android-suite-offline/jadx/bin/jadx",
                "/opt/jadx/bin/jadx",
            ),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("openjdk-17-jdk-headless"),
            group = ToolchainGroup.REVERSE_ENGINEERING,
        ),
        ToolchainProbe(
            id = "apktool",
            displayName = "Apktool",
            purpose = "APK 资源与 Smali 回编译，改包名/资源/流程必用",
            commands = listOf("apktool"),
            candidatePaths = listOf("/opt/taixu/tools/android-suite-offline/lib/apktool.jar"),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("apktool"),
            group = ToolchainGroup.REVERSE_ENGINEERING,
        ),
        ToolchainProbe(
            id = "baksmali",
            displayName = "baksmali/smali",
            purpose = "Dex 与 Smali 互转，字节码级修改的基础",
            commands = listOf("baksmali", "smali"),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("smali"),
            group = ToolchainGroup.REVERSE_ENGINEERING,
        ),
        ToolchainProbe(
            id = "dex2jar",
            displayName = "dex2jar",
            purpose = "Dex 转 Jar，便于用常规 Java 工具分析字节码",
            commands = listOf("d2j-dex2jar", "d2j-dex2jar.sh"),
            aptPackages = listOf("dex2jar"),
            group = ToolchainGroup.REVERSE_ENGINEERING,
        ),
        ToolchainProbe(
            id = "readelf",
            displayName = "binutils (readelf/objdump/strings)",
            purpose = "ELF 头段符号表分析，SO 逆向与壳识别的基础",
            commands = listOf("readelf"),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("binutils"),
            group = ToolchainGroup.REVERSE_ENGINEERING,
        ),
        ToolchainProbe(
            id = "frida",
            displayName = "Frida",
            purpose = "动态插桩与 hook，运行期改写行为；工具可装，驱动需 root",
            commands = listOf("frida", "frida-ps"),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("python3-pip"),
            group = ToolchainGroup.REVERSE_ENGINEERING,
            requiresRootAtRuntime = true,
        ),
        ToolchainProbe(
            id = "strace",
            displayName = "strace",
            purpose = "系统调用追踪，定位文件/网络行为与崩溃根因",
            commands = listOf("strace"),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("strace"),
            group = ToolchainGroup.DEBUG_INSPECT,
        ),
        ToolchainProbe(
            id = "ltrace",
            displayName = "ltrace",
            purpose = "库函数调用追踪，观察 dlopen/malloc 等关键行为",
            commands = listOf("ltrace"),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("ltrace"),
            group = ToolchainGroup.DEBUG_INSPECT,
        ),
        ToolchainProbe(
            id = "gdb",
            displayName = "GDB (multiarch)",
            purpose = "原生调试器，支持 ARM64 跨架构调试",
            commands = listOf("gdb-multiarch", "gdb"),
            versionCommand = "{cmd} --version",
            aptPackages = listOf("gdb-multiarch"),
            group = ToolchainGroup.DEBUG_INSPECT,
        ),
    )

    fun probesInGroup(group: ToolchainGroup): List<ToolchainProbe> = probes.filter { it.group == group }
}
