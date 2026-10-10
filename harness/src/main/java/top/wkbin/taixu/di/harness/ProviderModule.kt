package top.wkbin.taixu.di.harness

import org.koin.dsl.module
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.harness.ProviderModelResolver
import top.wkbin.taixu.harness.ProviderTransport

internal val providerModule = module {
    single { ProviderModelResolver(providerRepository = get(), modelDao = get(), settingsDataStore = get()) }
    single { ProviderTransport(client = get(), json = get()) }
    single { ProviderClient(modelResolver = get(), transport = get()) }
}
