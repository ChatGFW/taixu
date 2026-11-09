package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.*

/**
 * 输入区操作钮视觉直径：对齐 miuix [IconButtonDefaults] 的 40dp，
 * 不再叠 Material 的 48dp minimumInteractive，避免实心底被撑成 FAB。
 */
internal val ComposerActionSize = 40.dp

@Composable
internal fun ComposerSendButton(
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onLongClick: (() -> Unit)? = null,
) {
    // 长按走外层 pointerInput 手势；rememberUpdatedState 防流式重组重启检测器打断进行中的长按
    val latestLongClick by rememberUpdatedState(onLongClick)
    val longPressModifier = if (onLongClick != null) {
        Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { latestLongClick?.invoke() }) }
    } else Modifier
    RuntimeFilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(ComposerActionSize).then(longPressModifier),
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor,
        contentDescription = stringResource(R.string.chat_send),
    ) {
        RuntimeIcon(RuntimeIconName.ArrowUp, Modifier.size(20.dp))
    }
}

@Composable
internal fun ComposerStopButton(onClick: () -> Unit) {
    RuntimeFilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(ComposerActionSize),
        containerColor = RuntimeTheme.colorScheme.errorContainer,
        contentColor = RuntimeTheme.colorScheme.onErrorContainer,
        contentDescription = stringResource(R.string.chat_stop_generation),
    ) {
        RuntimeIcon(RuntimeIconName.Stop, Modifier.size(20.dp))
    }
}
