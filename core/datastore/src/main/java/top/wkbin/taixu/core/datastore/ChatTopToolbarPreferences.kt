package top.wkbin.taixu.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import top.wkbin.taixu.core.model.ChatTopToolbarAction

/**
 * 聊天页顶部右侧快捷按钮置顶列表。
 *
 * 经 [AppearancePreferences] 暴露；与 ToolStatusPreferences 同属外观门面，
 * 不继续堆进已触及尺寸棘轮的 SettingsDataStore.kt。
 */
internal class ChatTopToolbarPreferences(private val store: DataStore<Preferences>) {
    val pinnedActions: Flow<List<ChatTopToolbarAction>> =
        store.data.map { ChatTopToolbarAction.parsePinned(it[PINNED]) }

    suspend fun setPinnedActions(actions: List<ChatTopToolbarAction>) {
        store.edit { it[PINNED] = ChatTopToolbarAction.encode(actions) }
    }

    /**
     * 置顶或取消置顶。[pin]=true 且已满 [ChatTopToolbarAction.MAX_PINNED] 时拒绝并返回 false。
     * 置顶成功时追加到列表末尾。
     */
    suspend fun setPinned(action: ChatTopToolbarAction, pin: Boolean): Boolean {
        val current = pinnedActions.first()
        if (pin) {
            if (action in current) return true
            if (current.size >= ChatTopToolbarAction.MAX_PINNED) return false
            setPinnedActions(current + action)
            return true
        }
        setPinnedActions(current.filterNot { it == action })
        return true
    }

    private companion object {
        val PINNED = stringPreferencesKey("chat_top_toolbar_pinned")
    }
}
