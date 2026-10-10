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
 * 职责：
 * - 检测：调用 [ToolchainInspector] 全量探针；
 * - 补齐：**委托 [ToolManager].startBackgroundBatchInstall** 走开发套件安装
 *   （白名单 + PRoot 准备步骤 + 应用级互斥锁 + 后台任务），不自己拼 apt/删锁/换源。
 *
 * 生命周期：安装用 ToolManager 的应用级 managerScope 承载，离开聊天页也不会被打断；
 * 本 VM 只订阅 [ToolManager.bundleInstallState] 刷新 UI。
 */
class ToolchainViewModel(
    private val inspector: ToolchainInspector,
    private val toolManager: ToolManager,
    private val linuxRuntime: LinuxRuntime,
) : ViewModel() {

    private val _report = MutableStateFlow<ToolchainReport?>(null)
    val report: StateFlow<ToolchainReport?> = _report.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** 顶部入口是否需要亮红点（有可一键补齐的缺口即亮）。 */
    private val _hasGap = MutableStateFlow(false)
    val hasGap: StateFlow<Boolean> = _hasGap.asStateFlow()

    /** 安装进度（来自开发套件批量安装的实时日志）。 */
    val installLog: StateFlow<List<String>> = toolManager.bundleInstallLog

    /** 安装状态文案（null = 空闲）。 */
    val installState: StateFlow<String?> = toolManager.bundleInstallState

    /**
     * 当前发行版是否 Debian 系（packageManager == "apt"）。
     * 非 Debian 系（alpine/arch/fedora）没有 apt，开发套件安装不适用，UI 层据此隐藏补齐按钮。
     */
    val debBased: Boolean
        get() {
            val activeId = linuxRuntime.activeDistroId.value
            val active = linuxRuntime.installedDistros.value.firstOrNull { it.id == activeId }
            return active?.packageManager == "apt"
        }

    init {
        // 安装状态结束后自动刷新检测结果与红点
        viewModelScope.launch {
            toolManager.bundleInstallState.collect { state ->
                _busy.value = toolManager.isBatchInstalling.value
                if (state == null && _report.value != null) {
                    refresh()
                }
            }
        }
    }

    /** 全量检测；沙箱不可用时如实置 failed，不谎报「工具缺失」。 */
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
     * 一键补齐：把 [ToolchainReport.repairable] 里走开发套件的项映射到组件 id，
     * 交给 ToolManager 后台批量安装。
     */
    fun repairMissing() {
        val current = _report.value ?: return
        val targets = current.repairable
        if (targets.isEmpty() || _busy.value) return

        val componentIds = targets
            .mapNotNull { it.probe.repair as? RepairStrategy.ByBundleComponents }
            .flatMap { it.componentIds }
            .distinct()
            .toSet()

        if (componentIds.isEmpty()) return

        toolManager.startBackgroundBatchInstall(componentIds, reinstall = false, onCompleted = {
            viewModelScope.launch { refresh() }
        })
    }
}
