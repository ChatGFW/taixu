package top.wkbin.taixu.di.harness

import org.koin.dsl.module
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.harness.ProviderModelResolver
import top.wkbin.taixu.harness.ProviderTransport

internal val providerModule = module {
    single<top.wkbin.taixu.core.tools.ExternalProviderProbe> { top.wkbin.taixu.harness.plugins.ExternalProviderConnectionProbe(get(), get(), get()) }
    single { top.wkbin.taixu.harness.plugins.ExternalHarnessPlugins(get(), get()) }
    single { val external = get<top.wkbin.taixu.harness.plugins.ExternalHarnessPlugins>()
        top.wkbin.taixu.harness.plugins.HarnessPluginRegistry(top.wkbin.taixu.harness.plugins.builtinHarnessPlugins() + getAll<top.wkbin.taixu.harness.plugins.HarnessPlugin>(), external::plugins) }
    single { ProviderModelResolver(providerRepository = get(), modelDao = get(), settingsDataStore = get()) }
    single { val plugins = get<top.wkbin.taixu.harness.plugins.HarnessPluginRegistry>(); val json = get<kotlinx.serialization.json.Json>()
        ProviderTransport(client = get(), json = json, adapters = { client -> top.wkbin.taixu.harness.LlmApiRegistry(plugins.providers(client, json), liveOverrides = { plugins.providerOverrides(client, json) }) }) }
    single { ProviderClient(modelResolver = get(), transport = get()) }
}
