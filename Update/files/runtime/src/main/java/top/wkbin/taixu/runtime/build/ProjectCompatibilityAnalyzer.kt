package top.wkbin.taixu.runtime.build

import java.io.File
import top.wkbin.taixu.runtime.ProjectType

enum class CompatibilitySeverity {
    INFO,
    WARNING,
    ERROR,
}

data class CompatibilityFinding(
    val id: String,
    val severity: CompatibilitySeverity,
    val message: String,
    val current: String? = null,
    val expected: String? = null,
    val remediation: String? = null,
)

data class ProjectCompatibilityReport(
    val projectPath: String,
    val projectType: ProjectType,
    val filesChecked: List<String>,
    val findings: List<CompatibilityFinding>,
) {
    val canUseArm64Toolchain: Boolean
        get() = findings.none { it.severity == CompatibilitySeverity.ERROR }

    val requiresUserAlignment: Boolean
        get() = findings.any { it.id == "compile_sdk" || it.id == "gradle_wrapper" || it.id == "agp" || it.id == "kotlin" }

    val hasX86NativeInputs: Boolean
        get() = findings.any { it.id == "x86_abi" }
}

/**
 * A conservative, side-effect-free analyzer for third-party Android/Flutter projects.
 * It only reads project files and never rewrites them. The shell doctor remains the
 * authority inside PRoot; this class gives the app and tests a deterministic report model.
 */
object ProjectCompatibilityAnalyzer {
    private val compileSdkPattern = Regex("compileSdk(?:Version)?\\s*[= (:]\\s*(\\d+)")
    private val gradleVersionPattern = Regex("gradle-([0-9]+(?:\\.[0-9]+)*)-(?:bin|all)\\.zip")
    private val agpPattern = Regex("(?:com\\.android\\.tools\\.build:gradle|com\\.android\\.application)[:\\\"' =]+(?:[\\\"']?)([0-9]+(?:\\.[0-9]+)*)")
    private val kotlinPattern = Regex("(?:kotlin(?:Version)?|org\\.jetbrains\\.kotlin\\.android)[^0-9]*([0-9]+(?:\\.[0-9]+){1,2})")
    private val ndkVersionPattern = Regex("ndkVersion[^0-9]*(\\d+(?:\\.\\d+)+)")
    private val cmakeVersionPattern = Regex("cmake[^\\n]*version[^0-9]*(\\d+(?:\\.\\d+)+)", RegexOption.IGNORE_CASE)

    /**
     * 本地可用工具链的版本上限，供可行性判定使用。
     * 不能写死为固定值：工具链升级后写死的上限会把明明能编的工程误判为超出范围，
     * 也会让 Gradle Wrapper 比对恒报“与固定版本不同”。
     * 目录不存在时返回 null，调用方据此跳过对应检查，而不是编造一个期望值。
     */
    /**
     * 本地可用工具链的版本上限，供可行性判定使用。
     *
     * 探测基准是**沙箱 rootfs 根目录**（调用方从 RuntimePathManager.rootfsDir() 取），
     * 不能直接用 `/opt`：那是 PRoot 内的路径，App 进程看到的是宿主根，恒为空，
     * 检查会全部静默跳过。版本号不写死，按目录实际内容取最高值。
     * rootfs 未安装或目录缺失时返回 null，调用方据此跳过检查，而不是编造一个期望值。
     */
    private fun localMaxPlatform(rootfs: File?): Int? {
        if (rootfs == null) return null
        return listOf(
            File(rootfs, "opt/android-sdk/platforms"),
            File(rootfs, "opt/taixu/tools/android/platforms"),
        )
            .asSequence()
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles().orEmpty().asSequence() }
            .mapNotNull { it.name.removePrefix("android-").substringBefore('.').toIntOrNull() }
            .maxOrNull()
    }

    private fun localMaxGradle(rootfs: File?): String? {
        if (rootfs == null) return null
        val versions = File(rootfs, "opt").listFiles().orEmpty().asSequence()
            .filter { it.isDirectory && it.name.startsWith("gradle-") }
            .map { it.name.removePrefix("gradle-") }
            .filter { it.matches(Regex("[0-9]+(\\.[0-9]+)+")) }
            .toList()
        if (versions.isEmpty()) return null
        // 按数字段逐位比较，避免“9.10 < 9.8”这类字符串排序错误。
        fun parts(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }
        return versions.maxWithOrNull { a, b ->
            val pa = parts(a)
            val pb = parts(b)
            var diff = 0
            for (i in 0 until maxOf(pa.size, pb.size)) {
                diff = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
                if (diff != 0) break
            }
            diff
        }
    }

    fun analyze(projectDir: File, offline: Boolean = false, rootfsRoot: File? = null): ProjectCompatibilityReport {
        val root = projectDir.canonicalFile
        val files = root.walkTopDown()
            .filter { it.isFile && it.length() <= 512 * 1024L }
            .filter { it.extension in setOf("gradle", "kts", "yaml", "properties", "toml") }
            .take(80)
            .toList()
        val names = files.map { it.relativeToOrNull(root)?.path ?: it.name }
        val allText = files.joinToString("\n") { runCatching { it.readText() }.getOrDefault("") }
        val type = when {
            File(root, "pubspec.yaml").isFile -> ProjectType.FLUTTER
            File(root, "settings.gradle").isFile || File(root, "settings.gradle.kts").isFile ||
                File(root, "build.gradle").isFile || File(root, "build.gradle.kts").isFile -> ProjectType.ANDROID
            else -> ProjectType.GENERAL
        }
        val findings = mutableListOf<CompatibilityFinding>()

        // 上限取本地实有 Platform；读不到时跳过检查，不编造期望值。
        val maxPlatform = localMaxPlatform(rootfsRoot)
        val compileSdk = compileSdkPattern.find(allText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (compileSdk != null && maxPlatform != null) {
            if (compileSdk > maxPlatform) findings += CompatibilityFinding(
                id = "compile_sdk",
                severity = CompatibilitySeverity.ERROR,
                message = "项目 compileSdk 高于 TaiXu 本地最高 Android Platform $maxPlatform",
                current = compileSdk.toString(),
                expected = "<= $maxPlatform",
                remediation = "由 mobile_project_align 生成最小降级方案，或安装匹配的 ARM64 Platform",
            ) else findings += CompatibilityFinding(
                id = "compile_sdk",
                severity = CompatibilitySeverity.INFO,
                message = "项目 compileSdk 可使用内置 Platform",
                current = compileSdk.toString(),
                expected = maxPlatform.toString(),
            )
        }

        val wrapper = listOf(File(root, "gradle/wrapper/gradle-wrapper.properties"), File(root, "android/gradle/wrapper/gradle-wrapper.properties"))
            .firstOrNull { it.isFile }
        // 期望值取本地实有 Gradle；读不到时跳过，避免恒报与“固定版本”不符。
        val maxGradle = localMaxGradle(rootfsRoot)
        val wrapperVersion = wrapper?.let { gradleVersionPattern.find(runCatching { it.readText() }.getOrDefault(""))?.groupValues?.getOrNull(1) }
        if (wrapperVersion != null && maxGradle != null && wrapperVersion != maxGradle) findings += CompatibilityFinding(
            id = "gradle_wrapper",
            severity = CompatibilitySeverity.WARNING,
            message = "项目 Gradle Wrapper 与 TaiXu 本地最高版本不同",
            current = wrapperVersion,
            expected = maxGradle,
            remediation = "优先使用 TaiXu 本地 Gradle；需要改项目时先生成对齐计划",
        )

        agpPattern.find(allText)?.groupValues?.getOrNull(1)?.let { version ->
            findings += CompatibilityFinding(
                id = "agp",
                severity = CompatibilitySeverity.INFO,
                message = "检测到 Android Gradle Plugin 版本",
                current = version,
                remediation = "若构建失败，再由对齐 Skill 判断兼容范围",
            )
        }
        kotlinPattern.find(allText)?.groupValues?.getOrNull(1)?.let { version ->
            findings += CompatibilityFinding(
                id = "kotlin",
                severity = CompatibilitySeverity.INFO,
                message = "检测到 Kotlin 版本",
                current = version,
                remediation = "不自动改写，待用户确认后对齐",
            )
        }

        ndkVersionPattern.find(allText)?.groupValues?.getOrNull(1)?.let { version ->
            findings += CompatibilityFinding(
                id = "ndk_version",
                severity = CompatibilitySeverity.INFO,
                message = "项目显式声明了 NDK 版本",
                current = version,
                remediation = "构建时与工坊所选 NDK 的 source.properties 比对；不自动修改项目",
            )
        }
        cmakeVersionPattern.find(allText)?.groupValues?.getOrNull(1)?.let { version ->
            findings += CompatibilityFinding(
                id = "cmake_version",
                severity = CompatibilitySeverity.INFO,
                message = "项目显式声明了 CMake 版本",
                current = version,
                remediation = "构建时与工坊所选 CMake 比对；不自动修改项目",
            )
        }

        if (Regex("abiFilters[^\\n]*(x86_64|x86|armeabi-v7a)", RegexOption.IGNORE_CASE).containsMatchIn(allText)) {
            findings += CompatibilityFinding(
                id = "x86_abi",
                severity = CompatibilitySeverity.ERROR,
                message = "项目显式声明了非 ARM64 ABI",
                expected = "arm64-v8a only",
                remediation = "将 abiFilters 对齐为仅 arm64-v8a；生成的 lib/<abi> 目录由构建入口自动清理",
            )
        }

        val cacheHints = if (type == ProjectType.FLUTTER) {
            listOf(File(System.getenv("PUB_CACHE") ?: "", "hosted"))
        } else {
            listOf(File(System.getenv("GRADLE_USER_HOME") ?: File(System.getProperty("user.home"), ".gradle").path, "caches/modules-2/files-2.1"))
        }
        if (offline && cacheHints.none { it.isDirectory }) findings += CompatibilityFinding(
            id = "offline_cache",
            severity = CompatibilitySeverity.ERROR,
            message = "离线模式已开启，但本地项目依赖缓存不存在",
            remediation = "先在线完成一次依赖解析，或把项目依赖缓存放入 TaiXu 离线缓存目录",
        )

        if (findings.isEmpty()) findings += CompatibilityFinding(
            id = "no_constraints",
            severity = CompatibilitySeverity.INFO,
            message = "未发现超出当前 ARM64 工具链的显式约束",
        )
        return ProjectCompatibilityReport(root.absolutePath, type, names, findings)
    }
}
