package top.wkbin.taixu.runtime.doctor

import top.wkbin.taixu.core.model.RepairProgress
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.ShellCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class EnvironmentRepairer(
    private val linuxRuntime: LinuxRuntime,
    private val environmentDoctor: EnvironmentDoctor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<RepairProgress?>(null)
    val progress: StateFlow<RepairProgress?> = _progress.asStateFlow()
    private val _isRepairing = MutableStateFlow(false)
    val isRepairing: StateFlow<Boolean> = _isRepairing.asStateFlow()
    private var currentRepairJob: Job? = null

    fun startRepair(): Job {
        val existing = currentRepairJob
        if (existing?.isActive == true) return existing
        val job = scope.launch {
            _isRepairing.value = true
            try {
                repair().collect { p ->
                    _progress.value = p
                }
            } finally {
                _isRepairing.value = false
            }
        }
        currentRepairJob = job
        return job
    }

    fun cancelRepair() {
        currentRepairJob?.cancel()
        _isRepairing.value = false
        _progress.value = null
    }

    fun repair(): Flow<RepairProgress> = flow {
        val totalSteps = 5
        val logs = mutableListOf<String>()

        fun addLog(message: String) {
            logs.add(message)
            if (logs.size > 200) {
                logs.removeAt(0)
            }
        }

        if (linuxRuntime.state.value !is RuntimeState.Ready) {
            emit(
                RepairProgress(
                    stepTitle = "Linux 沙箱未就绪",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = listOf("错误: Linux 沙箱未初始化或处于异常状态，无法执行自愈修复"),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "Linux 沙箱未就绪，请先初始化沙箱",
                ),
            )
            return@flow
        }

        try {
            // ==========================================
            // Step 1: 修复 DNS 与证书
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在配置高可用 DNS 与 SSL 根证书...",
                    stepIndex = 1,
                    totalSteps = totalSteps,
                    progress = 0.15f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 1/5] 写入公共 DNS (114.114.114.114, 223.5.5.5, 8.8.8.8)")
            val dnsCmd = "mkdir -p /etc && printf 'nameserver 114.114.114.114\\nnameserver 223.5.5.5\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf"
            val dnsRes = executeCommand(dnsCmd, logs)
            if (!dnsRes.isSuccess) {
                addLog("警告: 写入 /etc/resolv.conf 失败: ${dnsRes.stderr}")
            }

            // ==========================================
            // Step 2: 清理残留锁并切换国内镜像加速源
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在配置清华大学国内镜像加速源...",
                    stepIndex = 2,
                    totalSteps = totalSteps,
                    progress = 0.35f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 2/5] 清理 dpkg/apt 事务锁并替换国内源")
            val mirrorScript = """
                rm -rf /var/lib/dpkg/updates/* /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true
                DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true
                mkdir -p /etc/apt/sources.list.d
                # 停用所有自带官方源与旧源，避免与新源冲突或引发 404
                for f in /etc/apt/sources.list /etc/apt/sources.list.d/*.list /etc/apt/sources.list.d/*.sources; do
                    if [ -f "${'$'}f" ] && [ "${'$'}(basename "${'$'}f")" != "taixu-mirrors.list" ]; then
                        mv "${'$'}f" "${'$'}f.taixu-disabled" 2>/dev/null || true
                    fi
                done
                if [ -f /etc/os-release ]; then
                    . /etc/os-release
                    if [ "${'$'}ID" = "ubuntu" ]; then
                        CN="${'$'}{VERSION_CODENAME:-noble}"
                        printf "deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s main restricted universe multiverse\ndeb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s-updates main restricted universe multiverse\ndeb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s-security main restricted universe multiverse\ndeb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s-backports main restricted universe multiverse\n" "${'$'}CN" "${'$'}CN" "${'$'}CN" "${'$'}CN" > /etc/apt/sources.list.d/taixu-mirrors.list
                    elif [ "${'$'}ID" = "debian" ] || [ "${'$'}ID_LIKE" = "debian" ]; then
                        CN="${'$'}{VERSION_CODENAME:-bookworm}"
                        printf "deb https://mirrors.tuna.tsinghua.edu.cn/debian %s main contrib non-free non-free-firmware\ndeb https://mirrors.tuna.tsinghua.edu.cn/debian %s-updates main contrib non-free non-free-firmware\ndeb https://mirrors.tuna.tsinghua.edu.cn/debian-security %s-security main contrib non-free non-free-firmware\n" "${'$'}CN" "${'$'}CN" "${'$'}CN" > /etc/apt/sources.list.d/taixu-mirrors.list
                    elif [ "${'$'}ID" = "kali" ]; then
                        printf "deb https://mirrors.tuna.tsinghua.edu.cn/kali kali-rolling main contrib non-free\n" > /etc/apt/sources.list.d/taixu-mirrors.list
                    fi
                fi
                # 清理旧的 apt lists 缓存，确保重新从镜像拉取完整的 index
                rm -rf /var/lib/apt/lists/* 2>/dev/null || true
            """.trimIndent()
            executeCommand(mirrorScript, logs)

            // ==========================================
            // Step 3: 更新 APT 软件包索引
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在更新 APT 软件包索引...",
                    stepIndex = 3,
                    totalSteps = totalSteps,
                    progress = 0.55f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 3/5] 执行 apt-get update 刷新索引")
            val updateRes = executeCommand("rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* 2>/dev/null || true; DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true; DEBIAN_FRONTEND=noninteractive apt-get update -y", logs, timeoutMs = 120_000L)
            if (!updateRes.isSuccess) {
                addLog("提示: apt-get update 产生部分非致命提示")
            }

            // ==========================================
            // Step 4: 安装核心工具链 (curl, git, tar, xz-utils)
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在安装核心工具链 (Git / Curl / Tar / XZ)...",
                    stepIndex = 4,
                    totalSteps = totalSteps,
                    progress = 0.75f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 4/5] 安装 ca-certificates, curl, git, tar, xz-utils, procps, file")
            val installToolsCmd = "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates curl git tar xz-utils procps file"
            val installToolsRes = executeCommand(installToolsCmd, logs, timeoutMs = 180_000L)
            if (!installToolsRes.isSuccess) {
                addLog("警告: 基础工具链安装异常: ${installToolsRes.stderr.ifBlank { installToolsRes.stdout }}")
            }

            // ==========================================
            // Step 5: 配置并准备/升级 Node.js 与国内包镜像
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在就绪/升级 Node.js 运行时与 NPM/PIP 国内镜像...",
                    stepIndex = 5,
                    totalSteps = totalSteps,
                    progress = 0.90f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 5/5] 检查并自动升级 Node.js (>= v20 LTS) 及配置镜像加速")
            val setupNodeAndMirrors = """
                NODE_VER=${'$'}(node -v 2>/dev/null | tr -d 'v' | cut -d. -f1)
                NODE_BIN=${'$'}(command -v node 2>/dev/null || true)
                # 只有「完全找不到 node」才安装；已有 node 一律不覆盖（否则会把新版降级成 v22）
                if [ -z "${'$'}NODE_BIN" ] && [ -z "${'$'}NODE_VER" ]; then
                    echo "检测到 Node.js 缺失或版本较低 (当前: ${'$'}{NODE_VER:-未安装})，正在下载安装 Node.js v22 (LTS ARM64)..."
                    mkdir -p /tmp/node_setup
                    # 版本号不写死：动态查官方 latest-lts 的实际版本
                    NODE_LTS_VER=${'$'}(curl -fsSL --connect-timeout 10 --max-time 20 https://nodejs.org/dist/index.json 2>/dev/null \
                        | python3 -c "import sys,json;d=json.load(sys.stdin);print([x['version'].lstrip('v') for x in d if x.get('lts')][0])" 2>/dev/null)
                    [ -n "${'$'}NODE_LTS_VER" ] || NODE_LTS_VER=${'$'}{TAIXU_NODE_VERSION:-}
                    if [ -n "${'$'}NODE_LTS_VER" ]; then
                        curl -fsSL --connect-timeout 10 --max-time 180 "https://npmmirror.com/mirrors/node/v${'$'}{NODE_LTS_VER}/node-v${'$'}{NODE_LTS_VER}-linux-arm64.tar.xz" -o /tmp/node_setup/node.tar.xz || \
                        curl -fsSL --connect-timeout 10 --max-time 180 "https://nodejs.org/dist/v${'$'}{NODE_LTS_VER}/node-v${'$'}{NODE_LTS_VER}-linux-arm64.tar.xz" -o /tmp/node_setup/node.tar.xz || true
                    else
                        echo "无法解析 Node LTS 版本，跳过 Node 安装（不写死版本号）"
                    fi
                    if [ -f /tmp/node_setup/node.tar.xz ]; then
                        tar -xJf /tmp/node_setup/node.tar.xz -C /usr/local --strip-components=1
                        rm -rf /tmp/node_setup
                        echo "Node.js 安装完成: ${'$'}(node -v 2>/dev/null)"
                    else
                        echo "预编译包拉取受限，尝试通过系统包管理器就绪基础 Node..."
                        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends nodejs npm || true
                    fi
                else
                    echo "Node.js 已存在 (v${'$'}{NODE_VER:-?}，${'$'}NODE_BIN)，跳过安装（环境修复不覆盖/不降级已有版本）"
                    if [ -n "${'$'}NODE_VER" ] && [ "${'$'}NODE_VER" -lt 20 ] 2>/dev/null; then
                        echo "提示: 当前 Node 主版本 ${'$'}NODE_VER 低于 20，建议手动升级；本修复不会自动替换"
                    fi
                fi
                which npm >/dev/null 2>&1 && npm config set registry https://registry.npmmirror.com || true
                mkdir -p ${'$'}HOME/.pip
                printf '[global]\nindex-url = https://pypi.tuna.tsinghua.edu.cn/simple\n' > ${'$'}HOME/.pip/pip.conf 2>/dev/null || true
                echo "运行时就绪状态: Node ${'$'}(node -v 2>/dev/null || echo '未就绪'), NPM ${'$'}(npm -v 2>/dev/null || echo '未就绪')"
                if [ -d /opt/android-sdk ] || [ -d /opt/taixu/toolchains/android ]; then
                    if [ ! -f /etc/profile.d/taixu-android.sh ]; then
                        mkdir -p /etc/profile.d
                        cat << 'EOF' > /etc/profile.d/taixu-android.sh
# TaiXu Android development environment (self-healed by EnvironmentRepairer)
                        # ---- 太墟动态路径解析（勿手改成具体版本号）----
                        # 按名字扫描目录取最高正式版；跳过 rc/alpha/beta/preview/snapshot/canary
                        _tx_latest_dir() {
                            _tx_parent="${'$'}1"
                            _tx_prefix="${'$'}{2:-}"
                            ls -d "${'$'}_tx_parent"/"${'$'}_tx_prefix"*/ 2>/dev/null \
                                | sed 's#/${'$'}##' \
                                | grep -Eiv 'rc[0-9]*${'$'}|[-._]?(alpha|beta|preview|snapshot|canary)' \
                                | sort -V | tail -n 1
                        }
                        _tx_ndk_version() {
                            sed -n 's/^Pkg\.Revision *= *//p' "${'$'}1/source.properties" 2>/dev/null | tr -d '\r' | head -n 1
                        }
                        # _tx_ndk_dir：NDK 根。按太墟原设计布局探测（TAIXU_NDK_PATH → ANDROID_NDK_HOME → ANDROID_HOME/ndk → 内置目录）
                        _tx_ndk_dir() {
                            if [ -n "${'$'}{TAIXU_NDK_PATH:-}" ]; then printf '%s' "${'$'}TAIXU_NDK_PATH"; return 0; fi
                            if [ -n "${'$'}{ANDROID_NDK_HOME:-}" ]; then printf '%s' "${'$'}ANDROID_NDK_HOME"; return 0; fi
                            _d=${'$'}(_tx_latest_dir "${'$'}{ANDROID_HOME:-/opt/android-sdk}/ndk" '')
                            [ -z "${'$'}_d" ] && _d=${'$'}(_tx_latest_dir /opt/taixu/toolchains/android ndk)
                            [ -n "${'$'}_d" ] && printf '%s' "${'$'}_d"
                        }
                        # _tx_jdk_dir：JDK 根。按太墟原设计布局探测（JAVA_HOME → 内置 android/jdk → toolchains/jdk<ver>）
                        _tx_jdk_dir() {
                            if [ -n "${'$'}{JAVA_HOME:-}" ]; then printf '%s' "${'$'}JAVA_HOME"; return 0; fi
                            if [ -x /opt/taixu/toolchains/android/jdk/bin/javac ]; then printf '%s' /opt/taixu/toolchains/android/jdk; return 0; fi
                            _j=${'$'}(_tx_latest_dir /opt/taixu/toolchains jdk)
                            [ -n "${'$'}_j" ] && printf '%s' "${'$'}_j"
                        }
                        export ANDROID_HOME="/opt/android-sdk"
                        export ANDROID_SDK_ROOT="${'$'}ANDROID_HOME"
                        export JAVA_HOME="${'$'}{JAVA_HOME:-${'$'}(_tx_jdk_dir)}"
                        export GRADLE_HOME="${'$'}(_tx_latest_dir /opt gradle-)"
                        export TAIXU_BUILD_TOOLS_DIR="${'$'}(_tx_latest_dir "${'$'}ANDROID_HOME/build-tools" '')"
                        export TAIXU_AAPT2_PATH="${'$'}TAIXU_BUILD_TOOLS_DIR/aapt2"
                        export TAIXU_NDK_PATH="${'$'}{TAIXU_NDK_PATH:-${'$'}(_tx_ndk_dir)}"
                        export TAIXU_NDK_VERSION="${'$'}(_tx_ndk_version "${'$'}TAIXU_NDK_PATH")"
                        export ANDROID_NDK_HOME="${'$'}TAIXU_NDK_PATH"
                        export ANDROID_NDK_ROOT="${'$'}TAIXU_NDK_PATH"
                        export TAIXU_CMAKE_HOME="/opt/taixu/tools/android-suite-offline/cmake"
                        export TAIXU_NINJA_HOME="/opt/taixu/tools/android-suite-offline/bin"
                        export PATH="/opt/taixu/bin:/opt/taixu/tools/android-suite-offline/bin:/opt/taixu/tools/android-suite-offline/cmake/bin:${'$'}JAVA_HOME/bin:${'$'}GRADLE_HOME/bin:/opt/flutter/bin:${'$'}PATH"
                        export _JAVA_OPTIONS="-Djava.security.egd=file:/dev/urandom"
EOF
                        chmod 644 /etc/profile.d/taixu-android.sh 2>/dev/null || true
                    fi
                    if [ -f /root/.bashrc ] && ! grep -q "taixu-android" /root/.bashrc 2>/dev/null; then
                        echo '. /etc/profile.d/taixu-android.sh 2>/dev/null || true' >> /root/.bashrc
                    fi
                fi
            """.trimIndent()
            executeCommand(setupNodeAndMirrors, logs, timeoutMs = 240_000L)

            // 触发重新体检
            addLog("自愈修复已全部执行完成，正在刷新环境体检报告...")
            environmentDoctor.check()

            emit(
                RepairProgress(
                    stepTitle = "环境自愈与加速配置完成！",
                    stepIndex = 5,
                    totalSteps = totalSteps,
                    progress = 1.0f,
                    logs = logs.toList(),
                    isCompleted = true,
                    isFailed = false,
                ),
            )
        } catch (cancellation: CancellationException) {
            addLog("修复流程被用户取消")
            emit(
                RepairProgress(
                    stepTitle = "修复已取消",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = logs.toList(),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "修复任务已被手动取消",
                ),
            )
            throw cancellation
        } catch (throwable: Throwable) {
            addLog("修复异常: ${throwable.message}")
            emit(
                RepairProgress(
                    stepTitle = "自愈修复失败",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = logs.toList(),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = throwable.message ?: "环境自愈修复发生未知异常",
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeCommand(
        command: String,
        logs: MutableList<String>,
        timeoutMs: Long = 60_000L,
    ): CommandResult {
        val result = runCatching {
            linuxRuntime.execute(ShellCommand(commandLine = command, timeoutMs = timeoutMs))
        }.getOrElse {
            CommandResult(
                exitCode = 1,
                stdout = "",
                stderr = it.message ?: "执行命令出错",
                durationMs = 0L,
            )
        }

        result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(50)
            .forEach { logs.add(it) }

        if (!result.isSuccess) {
            result.stderr.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(30)
                .forEach { logs.add("ERR: $it") }
        }

        return result
    }
}
