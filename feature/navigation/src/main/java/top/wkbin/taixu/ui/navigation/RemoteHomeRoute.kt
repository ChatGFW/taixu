package top.wkbin.taixu.ui.navigation

import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.home.RemoteHomeScreen

/** 远程服务器独立概览路由；从 TaiXuNavHost 抽出以守尺寸棘轮。 */
@Composable
internal fun RemoteHomeRoute(
    serverId: String,
    onNavigate: (MainDestination) -> Unit,
    onBack: () -> Unit,
) {
    RemoteHomeScreen(serverId = serverId, onNavigate = onNavigate, onBack = onBack)
}
