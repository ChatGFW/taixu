package top.wkbin.taixu.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Hashline 闸门与跨启动计数。与其它设置共用同一 DataStore，避免继续堆在 [SettingsDataStore]。
 * 键名保持不变，已有安装不会丢偏好。
 */
class HashlinePreferences(private val store: DataStore<Preferences>) {
    /** read 默认是否输出 Hashline 行锚；默认 true。 */
    val readAnchorsDefault: Flow<Boolean> = store.data.map { it[readAnchorsDefaultKey] ?: true }

    suspend fun setReadAnchorsDefault(value: Boolean) {
        store.edit { it[readAnchorsDefaultKey] = value }
    }

    /** 是否允许 edit ops（Hashline）；默认 true。 */
    val editOpsEnabled: Flow<Boolean> = store.data.map { it[editOpsEnabledKey] ?: true }

    suspend fun setEditOpsEnabled(value: Boolean) {
        store.edit { it[editOpsEnabledKey] = value }
    }

    /** 是否允许 legacy oldText/newText；默认 false。 */
    val legacyEditEnabled: Flow<Boolean> = store.data.map { it[legacyEditEnabledKey] ?: false }

    suspend fun setLegacyEditEnabled(value: Boolean) {
        store.edit { it[legacyEditEnabledKey] = value }
    }

    /** Hashline 编辑计数 JSON（跨进程）；空串表示全零。 */
    val editStatsJson: Flow<String> = store.data.map { it[editStatsJsonKey].orEmpty() }

    suspend fun setEditStatsJson(value: String) {
        store.edit {
            if (value.isBlank()) it.remove(editStatsJsonKey) else it[editStatsJsonKey] = value
        }
    }

    private companion object {
        val readAnchorsDefaultKey = booleanPreferencesKey("agent_hashline_read_anchors_default")
        val editOpsEnabledKey = booleanPreferencesKey("agent_hashline_edit_ops_enabled")
        val legacyEditEnabledKey = booleanPreferencesKey("agent_hashline_legacy_edit_enabled")
        val editStatsJsonKey = stringPreferencesKey("hashline_edit_stats_v1")
    }
}

fun SettingsDataStore.hashlinePreferences(): HashlinePreferences = HashlinePreferences(appearanceDataStore)
