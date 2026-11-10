package top.wkbin.taixu.di.core.datastore

import org.koin.dsl.module
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.datastore.AgentServerPreferences
import top.wkbin.taixu.core.datastore.AppStatsPreferences
import top.wkbin.taixu.core.datastore.AppearancePreferences
import top.wkbin.taixu.core.datastore.BackupPreferenceStore
import top.wkbin.taixu.core.datastore.BackupPreferences
import top.wkbin.taixu.core.datastore.BrowserPreferences
import top.wkbin.taixu.core.datastore.ChatBackgroundImageStore
import top.wkbin.taixu.core.datastore.ChatBackgroundPreferences
import top.wkbin.taixu.core.datastore.ChatIdentityPreferences
import top.wkbin.taixu.core.datastore.ContainerBackendPreferences
import top.wkbin.taixu.core.datastore.EncryptedRemoteServerRepository
import top.wkbin.taixu.core.datastore.FirstUseGuidePreferences
import top.wkbin.taixu.core.datastore.FtpPreferences
import top.wkbin.taixu.core.datastore.McpClientPreferences
import top.wkbin.taixu.core.datastore.McpToolPolicyPreferences
import top.wkbin.taixu.core.datastore.OnboardingPreferences
import top.wkbin.taixu.core.datastore.PluginLibraryPreferences
import top.wkbin.taixu.core.datastore.PluginPreferences
import top.wkbin.taixu.core.datastore.ProviderPreferences
import top.wkbin.taixu.core.datastore.RegistryPreferences
import top.wkbin.taixu.core.datastore.RemoteServerRepository
import top.wkbin.taixu.core.datastore.RuntimePreferences
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.datastore.SshPreferences
import top.wkbin.taixu.core.datastore.TerminalPreferences
import top.wkbin.taixu.core.datastore.ToolPreferences
import top.wkbin.taixu.core.datastore.WorkshopPreferences
import top.wkbin.taixu.core.model.ChatIdentityStyle

/** Dependency registrations owned by the core:datastore module. */
val coreDatastoreModule = module {
    single { ChatIdentityPreferences(context = get()) }
    single(org.koin.core.qualifier.named("chatIdentityImages")) {
        val context = get<android.content.Context>().applicationContext
        ChatBackgroundImageStore(
            java.io.File(context.filesDir, ChatIdentityStyle.IMAGE_DIRECTORY),
            { uri -> context.contentResolver.openInputStream(uri) }, maxBytes = 10L * 1024 * 1024,
        )
    }
    single { ChatBackgroundPreferences(context = get()) }
    single { ChatBackgroundImageStore(context = get()) }
    single { PluginPreferences(context = get()) }
    single { PluginLibraryPreferences(context = get()) }
    single { ContainerBackendPreferences(context = get()) }
    single<RemoteServerRepository> {
        EncryptedRemoteServerRepository(get(), get())
    }
    single<BackupPreferenceStore> { BackupPreferences(context = get()) }
    single<AppearancePreferences> { AppearancePreferences(store = get()) }

    single<TerminalPreferences> { TerminalPreferences(store = get()) }

    single<RuntimePreferences> { RuntimePreferences(store = get()) }

    single<WorkshopPreferences> { WorkshopPreferences(store = get()) }

    single<SshPreferences> { SshPreferences(store = get()) }

    single<FtpPreferences> { FtpPreferences(store = get()) }

    single<AgentPreferences> { AgentPreferences(store = get()) }

    single<OnboardingPreferences> { OnboardingPreferences(store = get()) }

    single<ToolPreferences> { ToolPreferences(store = get()) }

    single<FirstUseGuidePreferences> { FirstUseGuidePreferences(store = get()) }

    single<RegistryPreferences> { RegistryPreferences(store = get()) }

    single<AppStatsPreferences> { AppStatsPreferences(store = get()) }

    single<ProviderPreferences> { ProviderPreferences(store = get()) }

    single<BrowserPreferences> { BrowserPreferences(store = get()) }

    single<AgentServerPreferences> { AgentServerPreferences(store = get()) }
    single { McpClientPreferences(context = get()) }
    single { McpToolPolicyPreferences(context = get()) }

    single<SettingsDataStore> { SettingsDataStore(context = get(), secretManager = get()) }
}
