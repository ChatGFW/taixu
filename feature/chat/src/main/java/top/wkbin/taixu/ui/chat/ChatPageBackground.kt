package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.koinInject
import top.wkbin.taixu.core.datastore.AppearancePreferences
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.datastore.ChatBackgroundPreferences
import top.wkbin.taixu.core.model.ChatBackgroundStyle
import top.wkbin.taixu.ui.components.RuntimeChatBackground
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.theme.LocalChatBackgroundActive
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

class ChatBackgroundViewModel(preferences: ChatBackgroundPreferences) : ViewModel() {
    val style = preferences.style.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatBackgroundStyle())
}

private const val BACKDROP_SCRIM_ALPHA = 0.55f

@Composable
internal fun ChatPageSurface(
    modifier: Modifier = Modifier,
    viewModel: ChatBackgroundViewModel? = previewOrNull { koinViewModel() },
    content: @Composable () -> Unit,
) {
    val style by viewModel?.style?.collectAsStateWithLifecycle() ?: previewStateOf(ChatBackgroundStyle())
    val appearance = previewOrNull { koinInject<AppearancePreferences>() }
    val classicDots by appearance?.classicToolStatusDots?.collectAsStateWithLifecycle(initialValue = false) ?: previewStateOf(false)
    val normalized = style.normalized()
    val hazeState = rememberHazeState()
    ChatIdentityHost {
        CompositionLocalProvider(LocalChatBackgroundActive provides
            (normalized.imageName != null && normalized.visibility > 0f), LocalClassicToolStatusDots provides classicDots,
            LocalChatHazeState provides hazeState) {
            // One continuous image behind the top bar, conversation, composer and system insets.
            Box(modifier) {
                RuntimeChatBackground(normalized, Modifier.matchParentSize().hazeSource(hazeState))
                // 正文直接落在背景上后，靠一层整页连续暗化保证对比度：
                // 不加圆角、不分区，避免再把对话"框"起来。
                if (normalized.imageName != null && normalized.visibility > 0f) {
                    Box(
                        Modifier.matchParentSize().background(
                            MaterialTheme.colorScheme.background.copy(alpha = BACKDROP_SCRIM_ALPHA),
                        ),
                    )
                }
                content()
            }
        }
    }
}

/**
 * 助手正文直接落在页面背景上，不再套统一的圆角卡片。
 *
 * 之前有背景图时会给每条回复加一层 0.92 不透明度的卡片，视觉上像被"框住"，
 * 与对话流本身的连续感冲突。可读性改由背景自身的暗化/模糊设置承担。
 */
@Composable
internal fun ChatReadableMessage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

/**
 * 工具步骤卡片与问候语不再单独铺底色。
 *
 * 有背景图时这里原先会加一层 0.92 的圆角底，整个工具调用区看起来像一块大卡片；
 * 可读性统一交给页面的连续暗化层，过程行保持与正文同一条视觉基线。
 */
@Composable
internal fun chatGreetingModifier(): Modifier = Modifier
