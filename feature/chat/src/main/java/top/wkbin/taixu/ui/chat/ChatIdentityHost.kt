package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import top.wkbin.taixu.core.datastore.ChatIdentityPreferences
import top.wkbin.taixu.core.model.ChatIdentityStyle
import top.wkbin.taixu.ui.components.*

private val LocalIdentityProvided = staticCompositionLocalOf { false }

@Composable
internal fun ChatIdentityHost(content: @Composable () -> Unit) {
    if (LocalIdentityProvided.current) { content(); return }
    val preferences = previewOrNull { koinInject<ChatIdentityPreferences>() }
    val style by preferences?.style?.collectAsStateWithLifecycle(ChatIdentityStyle()) ?: previewStateOf(ChatIdentityStyle())
    CompositionLocalProvider(LocalChatIdentity provides style, LocalIdentityProvided provides true, content = content)
}
