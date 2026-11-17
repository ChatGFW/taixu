package top.wkbin.taixu.ui.workspace

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.tools.AndroidSdkManager
import top.wkbin.taixu.core.tools.AndroidSdkRelease
import top.wkbin.taixu.core.tools.InstalledAndroidSdk
import top.wkbin.taixu.runtime.LinuxRuntime

@Immutable
data class WorkshopSdkState(
    val distroId: String = "",
    val installed: ImmutableList<InstalledAndroidSdk> = persistentListOf(),
    val releases: ImmutableList<AndroidSdkRelease> = persistentListOf(),
    val busy: Boolean = false,
    val progress: Float? = null,
    val message: String = "",
    val error: String? = null,
    val fetched: Boolean = false,
)

class WorkshopSdkViewModel(private val manager: AndroidSdkManager, private val runtime: LinuxRuntime) : ViewModel() {
    private val _state = MutableStateFlow(WorkshopSdkState())
    val state = _state.asStateFlow()
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            runtime.activeDistroId.collectLatest { distro ->
                operation?.cancelAndJoin()
                _state.update { it.copy(distroId = distro, installed = persistentListOf(), busy = false, message = "", error = null) }
                try {
                    val installed = manager.installed(distro).toImmutableList()
                    _state.update { it.copy(installed = installed) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _state.update { it.copy(error = error.message) }
                }
            }
        }
    }

    fun refresh() = runOperation("正在获取 GitHub 版本列表") { distro ->
        val installed = manager.installed(distro).toImmutableList()
        _state.update { it.copy(installed = installed) }
        val releases = manager.releases(distro).toImmutableList()
        _state.update { it.copy(releases = releases, fetched = true, message = if (releases.isEmpty()) "暂无 Linux ARM64 musl 发行包" else "") }
    }

    fun download(release: AndroidSdkRelease) = runOperation("准备下载 ${release.version}") { distro ->
        manager.install(distro, release) { message, progress ->
            _state.update { it.copy(message = message, progress = progress) }
        }
        val installed = manager.installed(distro).toImmutableList()
        _state.update { it.copy(installed = installed, message = "${release.version} 已安装，可以选择使用") }
    }

    fun choose(sdk: InstalledAndroidSdk, onReady: (InstalledAndroidSdk) -> Unit) = runOperation("正在验证 ${sdk.version}") { distro ->
        manager.validate(distro, sdk)
        check(runtime.activeDistroId.value == distro) { "Linux 系统已切换，请重新选择 SDK" }
        onReady(sdk)
        _state.update { it.copy(message = "已选择 ${sdk.version}，点击下方保存环境后生效") }
    }

    fun cancel() { operation?.cancel() }

    private fun runOperation(message: String, block: suspend (String) -> Unit) {
        if (operation?.isActive == true || _state.value.distroId.isBlank()) return
        val distro = _state.value.distroId
        _state.update { it.copy(busy = true, message = message, progress = null, error = null) }
        operation = viewModelScope.launch {
            try {
                block(distro)
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(message = "操作已取消，下载可重试续传") }
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "SDK 操作失败", message = "") }
            } finally {
                _state.update { it.copy(busy = false, progress = null) }
            }
        }
    }
}
