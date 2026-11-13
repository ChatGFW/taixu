package top.wkbin.taixu.ui.chat

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.squircle.squircleSurface

/**
 * 大窗底栏单排：返回 / 主页 + 右侧操作区（接管/继续等）。
 * 去掉最近任务键，避免与系统手势栏抢语义。
 */
@Composable
internal fun TwinPhoneChromeBar(
    onInjectKey: (keyCode: Int) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .squircleSurface(
                color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                cornerRadius = 28.dp,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuntimeIconButton(
            onClick = { onInjectKey(KeyEvent.KEYCODE_BACK) },
            modifier = Modifier.size(40.dp),
            contentDescription = "返回",
        ) {
            RuntimeIcon(RuntimeIconName.Back, Modifier.size(22.dp), RuntimeTheme.colorScheme.onSurface)
        }
        RuntimeIconButton(
            onClick = { onInjectKey(KeyEvent.KEYCODE_HOME) },
            modifier = Modifier.size(40.dp),
            contentDescription = "主页",
        ) {
            RuntimeIcon(RuntimeIconName.Home, Modifier.size(22.dp), RuntimeTheme.colorScheme.onSurface)
        }
        actions()
    }
}
