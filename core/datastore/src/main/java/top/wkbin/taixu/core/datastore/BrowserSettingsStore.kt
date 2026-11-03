package top.wkbin.taixu.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 内置浏览器偏好，从 [SettingsDataStore] 拆出以免撑破棘轮。键仍在 [BrowserPreferencesKeys]。 */
internal class BrowserSettingsStore(private val store: DataStore<Preferences>) {
    val defaultFamily: Flow<String> = store.data.map { it[BrowserPreferencesKeys.DefaultFamily].orEmpty() }
    suspend fun setDefaultFamily(value: String) { store.edit { it[BrowserPreferencesKeys.DefaultFamily] = value } }
    val homeUrl: Flow<String> = store.data.map { it[BrowserPreferencesKeys.HomeUrl].orEmpty() }
    suspend fun setHomeUrl(value: String) { store.edit { it[BrowserPreferencesKeys.HomeUrl] = value } }
    val coBrowsingEnabled: Flow<Boolean> = store.data.map { it[BrowserPreferencesKeys.CoBrowsingEnabled] ?: true }
    suspend fun setCoBrowsingEnabled(value: Boolean) { store.edit { it[BrowserPreferencesKeys.CoBrowsingEnabled] = value } }
    val allowRemoteConnect: Flow<Boolean> = store.data.map { it[BrowserPreferencesKeys.AllowRemoteConnect] ?: false }
    suspend fun setAllowRemoteConnect(value: Boolean) { store.edit { it[BrowserPreferencesKeys.AllowRemoteConnect] = value } }
    val allowEvalJs: Flow<Boolean> = store.data.map { it[BrowserPreferencesKeys.AllowEvalJs] ?: false }
    suspend fun setAllowEvalJs(value: Boolean) { store.edit { it[BrowserPreferencesKeys.AllowEvalJs] = value } }
    val allowHooks: Flow<Boolean> = store.data.map { it[BrowserPreferencesKeys.AllowHooks] ?: false }
    suspend fun setAllowHooks(value: Boolean) { store.edit { it[BrowserPreferencesKeys.AllowHooks] = value } }
    val allowCdp: Flow<Boolean> = store.data.map { it[BrowserPreferencesKeys.AllowCdp] ?: false }
    suspend fun setAllowCdp(value: Boolean) { store.edit { it[BrowserPreferencesKeys.AllowCdp] = value } }
    val allowVConsole: Flow<Boolean> = store.data.map { it[BrowserPreferencesKeys.AllowVConsole] ?: false }
    suspend fun setAllowVConsole(value: Boolean) { store.edit { it[BrowserPreferencesKeys.AllowVConsole] = value } }
    val desktopUserAgent: Flow<Boolean> = store.data.map { it[BrowserPreferencesKeys.DesktopUserAgent] ?: false }
    suspend fun setDesktopUserAgent(value: Boolean) { store.edit { it[BrowserPreferencesKeys.DesktopUserAgent] = value } }
    val maxCaptureBytes: Flow<Int> = store.data.map { it[BrowserPreferencesKeys.MaxCaptureBytes] ?: (6 * 1024 * 1024) }
    suspend fun setMaxCaptureBytes(value: Int) { store.edit { it[BrowserPreferencesKeys.MaxCaptureBytes] = value } }
    val actionPolicyJson: Flow<String> = store.data.map { it[BrowserPreferencesKeys.ActionPolicyJson].orEmpty() }
    suspend fun setActionPolicyJson(value: String) { store.edit { it[BrowserPreferencesKeys.ActionPolicyJson] = value } }
}
