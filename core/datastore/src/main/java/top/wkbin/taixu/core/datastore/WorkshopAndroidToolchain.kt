package top.wkbin.taixu.core.datastore

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map

data class WorkshopAndroidToolchain(val sdkPath: String, val aapt2Path: String)

internal fun SettingsDataStore.observeWorkshopAndroidToolchain() = workshopDataStore.data.map {
    WorkshopAndroidToolchain(it[workshopAndroidSdkPathKey].orEmpty(), it[workshopAapt2PathKey].orEmpty())
}

internal suspend fun SettingsDataStore.setWorkshopAndroidToolchain(sdkPath: String, aapt2Path: String) {
    workshopDataStore.edit {
        if (sdkPath.isBlank()) it.remove(workshopAndroidSdkPathKey) else it[workshopAndroidSdkPathKey] = sdkPath
        if (aapt2Path.isBlank()) it.remove(workshopAapt2PathKey) else it[workshopAapt2PathKey] = aapt2Path
    }
}
