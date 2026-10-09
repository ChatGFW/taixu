package top.wkbin.taixu.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.ToolchainProbeResult
import top.wkbin.taixu.core.model.ToolchainReport
import top.wkbin.taixu.runtime.doctor.ToolchainInspector
import top.wkbin.taixu.runtime.doctor.ToolchainInstaller

/**
 * 🧰 沙箱工具链 ViewModel
 *
 * 遵循项目规范：UI 不直接持有 LinuxRuntime，所有沙箱交互经 ViewModel 暴露，
 * 便于注入与替换（测试里可传 Fake）。
 */
class ToolchainViewModel(
    private val inspector: ToolchainInspector,
    private val installer: ToolchainInstaller,
) : ViewModel() {

    private val _report = MutableStateFlow<ToolchainReport?>(null)
    val report: StateFlow<ToolchainReport?> = _report.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** 全量检测；沙箱不可用时如实置 failed，不谎报「工具缺失」。 */
    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { inspector.inspect() }
                .onSuccess {
                    _failed.value = false
                    _report.value = it
                }
                .onFailure {
                    _failed.value = true
                    _report.value = null
                }
            _busy.value = false
        }
    }

    /**
     * 一键补齐：把 [ToolchainReport.repairable]（缺失 + 版本落后）合并安装。
     *
     * 只补明确有问题���项 —— 已就绪的工具一律不动，避免无谓破坏现成环境。
     */
    fun repairMissing() {
        val current = _report.value ?: return
        val targets = current.repairable
        if (targets.isEmpty() || _busy.value) return

        viewModelScope.launch {
            _busy.value = true
            _logs.value = emptyList()
            val results: List<ToolchainProbeResult> = runCatching {
                installer.install(targets) { line ->
                    _logs.update { (it + line).takeLast(60) }
                }
            }.getOrElse { throwable ->
                _logs.update { it + "补齐失败：${throwable.message}" }
                current.results
            }
            _report.value = current.copy(results = results)
            _busy.value = false
        }
    }
}
