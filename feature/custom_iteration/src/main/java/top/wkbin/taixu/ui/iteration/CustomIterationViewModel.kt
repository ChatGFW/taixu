package top.wkbin.taixu.ui.iteration

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wkbin.taixu.feature.custom_iteration.R
import top.wkbin.taixu.iteration.engine.CustomIterationBootstrap
import top.wkbin.taixu.iteration.engine.TaiXuDevBuildCoordinator

class CustomIterationViewModel(
    application: Application,
    private val buildCoordinator: TaiXuDevBuildCoordinator,
) : AndroidViewModel(application) {

    data class UiState(
        val isBusy: Boolean = false,
        val workspaceReady: Boolean = false,
        val workspacePath: String = "",
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 云端构建状态机：Idle → Dispatching → Running → Downloading → Success/Failed。 */
    private val _buildState = MutableStateFlow<TaiXuDevBuildCoordinator.BuildStatus>(
        TaiXuDevBuildCoordinator.BuildStatus.Idle
    )
    val buildState: StateFlow<TaiXuDevBuildCoordinator.BuildStatus> = _buildState.asStateFlow()

    fun startCustomIteration(onSessionReady: (prompt: String) -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)

        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val rootfsHome = File(app.filesDir, "runtime/rootfs/root")
                val result = CustomIterationBootstrap.bootstrap(app, rootfsHome)

                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        workspaceReady = true,
                        workspacePath = result.workspacePath
                    )
                    onSessionReady(result.prompt)
                } else {
                    // 引导失败的原始技术细节只进日志，UI 展示用户可理解的友好文案
                    Log.w(TAG, "bootstrap failed: ${result.errorMessage}")
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        errorMessage = app.getString(R.string.iteration_error_bootstrap)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "bootstrap custom iteration crashed", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    errorMessage = app.getString(R.string.iteration_error_bootstrap)
                )
            }
        }
    }

    /**
     * 触发 GitHub Actions 云端构建 TaiXuDev APK，并全程跟踪状态直至终态。
     * 流水线进行中（Dispatching/Running/Downloading）时忽略重复触发。
     */
    fun startCloudBuild(branch: String) {
        if (isBuildPipelineActive()) return
        val normalizedBranch = branch.trim().ifBlank { DEFAULT_BRANCH }
        viewModelScope.launch {
            val status = buildCoordinator.runPipeline(
                branch = normalizedBranch,
            ) { updated ->
                _buildState.value = updated
            }
            _buildState.value = status
        }
    }

    /** 用户关闭构建失败提示，回到 Idle 可再次触发。 */
    fun dismissBuildStatus() {
        val current = _buildState.value
        if (current is TaiXuDevBuildCoordinator.BuildStatus.Failed ||
            current is TaiXuDevBuildCoordinator.BuildStatus.Success
        ) {
            _buildState.value = TaiXuDevBuildCoordinator.BuildStatus.Idle
        }
    }

    private fun isBuildPipelineActive(): Boolean = when (_buildState.value) {
        is TaiXuDevBuildCoordinator.BuildStatus.Dispatching,
        is TaiXuDevBuildCoordinator.BuildStatus.Running,
        is TaiXuDevBuildCoordinator.BuildStatus.Downloading,
        -> true
        else -> false
    }

    /** 用户关闭错误提示。 */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val TAG = "CustomIteration"
        const val DEFAULT_BRANCH = "main"
    }
}
