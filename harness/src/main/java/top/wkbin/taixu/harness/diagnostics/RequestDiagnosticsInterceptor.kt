package top.wkbin.taixu.harness.diagnostics

import okhttp3.OkHttpClient
import okio.Buffer

/** Observe the final protocol body, including JSON_TEXT schemas and transport fallbacks. */
internal fun OkHttpClient.withRequestDiagnostics(
    onRequest: ((String, String, Long, Collection<String>) -> Unit)?,
): OkHttpClient {
    if (onRequest == null) return this
    return newBuilder().addInterceptor { chain ->
        val request = chain.request()
        // Diagnostics must never prevent the request from running. No raw body is logged.
        runCatching {
            val body = request.body
            if (body != null && !body.isDuplex() && !body.isOneShot() && body.contentLength() in 0..MAX_CAPTURE_BYTES) {
                val buffer = Buffer()
                body.writeTo(buffer)
                val bytes = buffer.size
                val credentials = request.headers.names().filter {
                    it.contains("key", true) || it.contains("token", true) || it.contains("secret", true) ||
                        it.equals("Authorization", true) || it.equals("Cookie", true)
                }.flatMap { name -> request.headers.values(name).flatMap { listOf(it, it.removePrefix("Bearer ")) } }
                onRequest(request.url.encodedPath.substringAfterLast('/'), buffer.readUtf8(), bytes, credentials)
            }
        }
        chain.proceed(request)
    }.build()
}

private const val MAX_CAPTURE_BYTES = 24_000_000L
