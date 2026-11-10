package top.wkbin.taixu.core.model

/**
 * 工具链路径的**动态解析**（替代所有硬编码版本号）。
 *
 * ## 为什么需要它
 * 旧实现把 `/opt/gradle-8.14.2`、`/opt/android-sdk/build-tools/35.0.0`、`android-34`、
 * `TAIXU_NDK_VERSION="r29"` 这类路径写死在多处（EnvironmentRepairer / BuildEnvironmentPreflight /
 * DevEnvironmentSuite / SandboxToolchain / WorkshopSettingsViewModel / WorkshopSigningManager）。
 * 后果：用户升级了 Gradle / build-tools / platform 后，环境体检仍按旧版本判定「缺组件」，
 * 触发自愈后把 `/etc/profile.d/taixu-android.sh` 重写成旧路径，于是新版本依赖被「还原」。
 *
 * ## 设计约定
 * 1. **按名字扫描目录 + 取最高版本**，不写任何具体版本号。
 * 2. 版本比较用 `sort -V`（GNU version sort），能正确处理 `37.2 > 37`、`9.8.2 > 9.8.10` 之类。
 * 3. **跳过预发布**：名字含 rc / alpha / beta / preview / snapshot / canary 的目录一律排除
 *    （用户铁律：只认正式版）。
 * 4. 解析逻辑以 **shell 函数**形式提供 —— App 进程看不到 PRoot 沙箱内的 `/opt`，
 *    只有把逻辑注入到沙箱内执行的脚本里才能真正探测到实际文件系统。
 *
 * ## 改上游后要做什么
 * 见仓库根目录 `Progress.md` 的「依赖版本动态检测」章节与 `HookPoints.md` 的对应结构档。
 */
object ToolchainPathResolver {

    /** Android SDK 根（PRoot 沙箱内路径，固定，不含版本号）。 */
    const val ANDROID_SDK_ROOT: String = "/opt/android-sdk"

    /** 太墟自带工具链根（固定，不含版本号）。 */
    const val TAIXU_TOOLCHAINS: String = "/opt/taixu/tools"

    /** 系统级 Gradle 安装根的父目录（`/opt/gradle-<version>`）。 */
    const val GRADLE_PARENT: String = "/opt"

    /** 系统级 Gradle 目录名前缀。 */
    const val GRADLE_PREFIX: String = "gradle-"

    /** 内置 JDK 固定目录（目录名不含版本号，版本体现在 java -version）。 */
    const val BUILTIN_JDK: String = "$TAIXU_TOOLCHAINS/android/jdk"

    /** 内置 NDK 固定目录（目录名不含版本号）。 */
    const val BUILTIN_NDK: String = "$TAIXU_TOOLCHAINS/android/ndk"

    /**
     * 太墟环境目标架构：PRoot 沙箱与 APK 均为 **aarch64 / arm64-v8a**。
     *
     * ★ 只检测「本架构可用」的正式版：平台相关制品（tar.gz / 含 native 的 zip）
     * 必须带 `aarch64` 或 `arm64`；纯 Java 制品（.jar、`gradle-*-bin.zip`）不做架构过滤。
     * 反例：platform-tools 37.0.2 的 linux 包只有 x86_64 → 对太墟不可用，不能被当成"可更新"。
     */
    const val TARGET_ARCH: String = "aarch64"

    /** 架构标记匹配（shell 侧 `grep -Ei` 用）。 */
    const val ARCH_PATTERN: String = "aarch64|arm64"

    /** 与架构无关的制品：纯 Java / 通用包，不做架构过滤。 */
    val ARCH_AGNOSTIC: List<String> = listOf(".jar", "-bin.zip", "-universal.zip", "-all.zip")

    /** 排除预发布目录名的正则（用于 shell 侧 grep -Ei）。 */
    const val PRERELEASE_PATTERN: String = "rc[0-9]*$|[-._]?(alpha|beta|preview|snapshot|canary)"

    /**
     * 注入到目标脚本开头的 shell 函数定义。
     *
     * 用法：
     * ```
     * eval "$(ToolchainPathResolver.SHELL_PRELUDE)"   # 或直接拼进 heredoc
     * BT="$(_tx_latest_dir /opt/android-sdk/build-tools '')"
     * ```
     */
    val SHELL_PRELUDE: String = """
        # ---- 太墟动态路径解析（ToolchainPathResolver，勿手改成具体版本号）----
        # _tx_latest_dir <父目录> [名字前缀]
        #   输出该目录下版本号最高的子目录绝对路径（无则输出空）。
        #   跳过 rc / alpha / beta / preview / snapshot / canary（只认正式版）。
        _tx_latest_dir() {
            _tx_parent="${'$'}1"
            _tx_prefix="${'$'}{2:-}"
            ls -d "${'$'}_tx_parent"/"${'$'}_tx_prefix"*/ 2>/dev/null \
                | sed 's#/${'$'}##' \
                | grep -Eiv '${PRERELEASE_PATTERN}' \
                | sort -V \
                | tail -n 1
        }
        # _tx_ndk_version <NDK 目录>
        #   从 source.properties 读 Pkg.Revision，读不到输出空。
        _tx_ndk_version() {
            sed -n 's/^Pkg\.Revision *= *//p' "${'$'}1/source.properties" 2>/dev/null | tr -d '\r' | head -n 1
        }
        # _tx_ndk_dir：NDK 根目录。按**太墟原设计布局**探测（不依赖任何搬迁后的怪路径）：
        #   ① 已设 TAIXU_NDK_PATH / ANDROID_NDK_HOME ② ANDROID_HOME/ndk/<ver>
        #   ③ /opt/taixu/tools/android/ndk（内置固定目录）
        _tx_ndk_dir() {
            if [ -n "${'$'}{TAIXU_NDK_PATH:-}" ]; then printf '%s' "${'$'}TAIXU_NDK_PATH"; return 0; fi
            if [ -n "${'$'}{ANDROID_NDK_HOME:-}" ]; then printf '%s' "${'$'}ANDROID_NDK_HOME"; return 0; fi
            _d=${'$'}(_tx_latest_dir "${'$'}{ANDROID_HOME:-/opt/android-sdk}/ndk" '')
            [ -z "${'$'}_d" ] && _d=${'$'}(_tx_latest_dir /opt/taixu/tools/android ndk)
            [ -n "${'$'}_d" ] && printf '%s' "${'$'}_d"
        }
        # _tx_jdk_dir：JDK 根目录。按**太墟原设计布局**探测：
        #   ① 已设 JAVA_HOME ② /opt/taixu/tools/android/jdk（内置）
        #   ③ /opt/taixu/tools/jdk<ver>（用户自装的高版本）
        _tx_jdk_dir() {
            if [ -n "${'$'}{JAVA_HOME:-}" ]; then printf '%s' "${'$'}JAVA_HOME"; return 0; fi
            if [ -x /opt/taixu/tools/android/jdk/bin/javac ]; then printf '%s' /opt/taixu/tools/android/jdk; return 0; fi
            _j=${'$'}(_tx_latest_dir /opt/taixu/tools jdk)
            [ -n "${'$'}_j" ] && printf '%s' "${'$'}_j"
        }
    """.trimIndent()
}
