package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.core.model.RunningSendMode
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.RuntimeIconName

@Composable
internal fun ApprovalModeSelectorRow(mode: ApprovalMode, onModeChange: (ApprovalMode) -> Unit) {
    val options = listOf(ApprovalMode.REQUEST, ApprovalMode.ASSISTED, ApprovalMode.FULL_ACCESS)
    top.wkbin.taixu.ui.components.RuntimeOverlayDropdownPreference(
        title = resolveLegacyString("全局工具权限"),
        summary = resolveLegacyString("修改后立即同步覆盖所有会话；聊天顶部的单独切换会被下一次全局修改覆盖。"),
        items = listOf("请求批准", "帮我批准", "完全访问").map { resolveLegacyString(it) },
        selectedIndex = options.indexOf(mode),
        startAction = { SettingsRowIcon(RuntimeIconName.Shield) },
        onSelectedIndexChange = { onModeChange(options[it]) },
    )
}

@Composable
internal fun RunModeSelectorRow(mode: RunMode, onModeChange: (RunMode) -> Unit) {
    val options = listOf(RunMode.BUILD, RunMode.PLAN)
    top.wkbin.taixu.ui.components.RuntimeOverlayDropdownPreference(
        title = resolveLegacyString("新会话默认运行意图"),
        summary = resolveLegacyString("仅作为新建会话初始值，已存在会话可在聊天顶部单独切换。"),
        items = listOf("构建", "规划").map { resolveLegacyString(it) },
        selectedIndex = options.indexOf(mode),
        startAction = { SettingsRowIcon(RuntimeIconName.Code) },
        onSelectedIndexChange = { onModeChange(options[it]) },
    )
}

@Composable
internal fun RunningSendModeSelectorRow(mode: RunningSendMode, onModeChange: (RunningSendMode) -> Unit) {
    val options = listOf(RunningSendMode.QUEUE, RunningSendMode.STEER)
    top.wkbin.taixu.ui.components.RuntimeOverlayDropdownPreference(
        title = stringResource(R.string.agent_running_send_mode_title),
        summary = stringResource(when (mode) {
            RunningSendMode.QUEUE -> R.string.agent_running_send_mode_queue_description
            RunningSendMode.STEER -> R.string.agent_running_send_mode_steer_description
        }),
        items = listOf(stringResource(R.string.agent_running_send_mode_queue), stringResource(R.string.agent_running_send_mode_steer)),
        selectedIndex = options.indexOf(mode),
        startAction = { SettingsRowIcon(RuntimeIconName.Tune) },
        onSelectedIndexChange = { onModeChange(options[it]) },
    )
}
