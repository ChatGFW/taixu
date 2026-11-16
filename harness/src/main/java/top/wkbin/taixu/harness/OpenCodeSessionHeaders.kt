package top.wkbin.taixu.harness

import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import top.wkbin.taixu.core.network.withOpenCodeSessionHeaders
import top.wkbin.taixu.harness.bot.TaskBudgetContext

/** Bind OpenCode routing to the persisted conversation, including retries and auxiliary calls. */
internal suspend fun OkHttpClient.withOpenCodeSession(model: ModelConfig, sessionKey: String?): OkHttpClient {
    val configured = ProviderClient.parseCustomHeaders(model.customHeaders)
    val openCode = model.baseUrl.trim().toHttpUrlOrNull()?.host == "opencode.ai" ||
        configured.any { (name, _) -> name.equals("x-opencode-session", ignoreCase = true) }
    if (!openCode) return this

    val session = sessionKey?.takeIf(String::isNotBlank)
        ?: currentCoroutineContext()[TaskBudgetContext]?.sessionId?.takeIf(String::isNotBlank)
        ?: UUID.randomUUID().toString()
    // Allocate once so every HTTP retry and protocol continuation shares the same ID.
    return newBuilder().apply {
        interceptors().add(0, Interceptor { chain ->
            chain.proceed(chain.request().withOpenCodeSessionHeaders(session))
        })
    }.build()
}
