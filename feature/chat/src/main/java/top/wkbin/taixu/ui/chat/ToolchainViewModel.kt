package top.wkbin.taixu.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.RepairStrategy
import top.wkbin.taixu.core.model.ToolchainReport
import top.wkbin.taixu.core.tools.ToolManager
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.doctor.ToolchainInspector

/**
 * 🧰 沙箱工具链 ViewModel
 *
 * 检测走 [ToolchainInspector]；补齐分两路串行编排：
 *  1. 开发套件组件（cmake/ninja/aapt2/apksigner/zipalign/jadx/apktool）
 *     → [ToolManager].startBackgroundBatchInstall（白名单+PRoot 准备+互斥锁+后台任务）；
 *  2. 套件未覆盖的 apt 工具（patchelf/baksmali/dex2jar/readelf/strace/ltrace/gdb）
 *     → [ToolchainRepairer]（复用 PluginBundleScripts 准备步骤 + aptOptions，逐包安装，
 *       经 ToolManager.runExclusive 与套件安装共用同一把互斥锁）。
 */
class ToolchainViewModel(
    private val inspector: ToolchainInspector,
    private val toolManager: ToolManager,
    private val linuxRuntime: LinuxRuntime,
) : ViewModel() {

    private val repairer = ToolchainRepairer(linuxRuntime)

    private val _report = MutableStateFlow<ToolchainReport?>(null)
    val report: StateFlow<ToolchainReport?> = _report.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _hasGap = MutableStateFlow(false)
    val hasGap: StateFlow<Boolean> = _hasGap.asStateFlow()

    /** 面板实时日志（补齐过程可见）。 */
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    /** 开发套件安装进度（来自 ToolManager）。 */
    val installState: StateFlow<String?> = toolManager.bundleInstallState

    /** 当前发行版是否 Debian 系（packageManager == "apt"），非 Debian 系隐藏补齐。 */
    val debBased: Boolean
        get() {
            val activeId = linuxRuntime.activeDistroId.value
            val active = linuxRuntime.installedDistros.value.firstOrNull { it.id == activeId }
            return active?.packageManager == "apt"
        }

    init {
        // 套件批量安装状态归零（结束）时自动刷新检测结果
        viewModelScope.launch {
            toolManager.bundleInstallState.collect { state ->
                _busy.value = toolManager.isBatchInstalling.value || _aptRunning
                if (state == null && _report.value != null && !_aptRunning) {
                    refresh()
                }
            }
        }
    }

    /** apt 补齐运行标志（跨协程可见）。 */
    @Volatile
    private var _aptRunning: Boolean = false

    /** 全量检测；沙箱不可用时如实置 failed。 */
    fun refresh() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { inspector.inspect() }
                .onSuccess { report ->
                    _failed.value = false
                    _report.value = report
                    _hasGap.value = report.repairable.isNotEmpty()
                }
                .onFailure {
                    _failed.value = true
                    _report.value = null
                }
            _busy.value = false
        }
    }

    /**
     * 一键补齐（串行两路）：
     * 先套件组件（若 busy 则等它结束——bundleInstallState collect 已处理），
     * 再 apt 项；全程日志实时回显，结束后自动刷新检测。
     */
    fun repairMissing() {
        val current = _report.value ?: return
        if (_busy.value || _aptRunning) return

        val bundleIds = current.repairable
            .mapNotNull { it.probe.repair as? RepairStrategy.ByBundleComponents }
            .flatMap { it.componentIds }
            .distinct()
            .toSet()

        viewModelScope.launch {
            _aptRunning = true
            _busy.value = true
            try {
                // ---- 路 1：套件组件安装（同步等待完成）----
                if (bundleIds.isNotEmpty()) {
                    _logs.value += "==> [1/2] 安装开发套件组件: ${bundleIds.joinToString(", ")}"
                    runCatching {
                        toolManager.batchInstallComponents(bundleIds, reinstall = false)
                            .collect { /* 进度经 bundleInstallState/bundleInstallLog 呈现 */ }
                    }.onFailure { _logs.value += "套件安装失败: ${it.message}" }
                }

                // ---- 路 2：apt 补齐（逐包，失败不拖累整批）----
                val fresh = _report.value ?: current
                val aptTargets = repairer.aptTargets(fresh)
                if (aptTargets.isNotEmpty()) {
                    _logs.value += "==> [2/2] 安装 apt 工具: ${aptTargets.joinToString(", ") { it.first }}"
                    val outcome = repairer.repair(fresh) { line -> _logs.value += line }
                    if (outcome.failures.isNotEmpty()) {
                        _logs.value += "补齐完成，${outcome.failures.size} 项失败（详见上方日志）"
                    }
                }

                // ---- 刷新检测（更新 checkedAt 与红点）----
                runCatching { inspector.inspect() }.onSuccess { report ->
                    _report.value = report
                    _hasGap.value = report.repairable.isNotEmpty()
                }
            } finally {
                _aptRunning = false
                _busy.value = false
            }
        }
    }
}
