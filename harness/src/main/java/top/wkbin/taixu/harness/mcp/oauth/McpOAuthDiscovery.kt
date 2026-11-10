package top.wkbin.taixu.harness.mcp.oauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
/**
 * MCP OAuth 2.1 自动发现与动态客户端注册（RFC 9728 / RFC 8414 / RFC 7591）。
 *
 * 职责：给只填了 serverUrl 的三方 MCP 服务自动预配 OAuth 端点与 client_id，
 * 免去用户手动查文档填 client_id / authorization_endpoint / token_endpoint。
 * 任何网络或解析失败都以 null 返回（不抛出），由调用方决定回退手动配置或报错；
 * 本类无状态、不落任何存储。
 */
class McpOAuthDiscovery(private val client: OkHttpClient) {

    /** RFC 9728 受保护资源元数据。 */
    data class ProtectedResource(
        val authorizationServers: List<String>,
        val resource: String?,
        val scopesSupported: List<String>,
    )

    /** RFC 8414 授权服务器元数据（含 OpenID Configuration 兼容回退）。 */
    data class AuthorizationServerMetadata(
        val authorizationEndpoint: String?,
        val tokenEndpoint: String?,
        val registrationEndpoint: String?,
        val scopesSupported: List<String>,
    )

    /** RFC 7591 动态注册结果。 */
    data class RegisteredClient(
        val clientId: String,
        val clientSecret: String?,
        val scope: String?,
    )

    /** RFC 9728：从 MCP 服务器地址发现受保护资源元数据（authorization_servers / resource）。 */
    suspend fun discoverProtectedResource(serverUrl: String): ProtectedResource? =
        wellKnownCandidates(serverUrl, WELL_KNOWN_PROTECTED_RESOURCE).firstNotNullOfOrNull { url ->
            fetchJson(url)?.let(::parseProtectedResource)
        }

    /** RFC 8414：发现授权服务器元数据（含 OpenID Configuration 兼容回退）。 */
    suspend fun discoverAuthorizationServer(authorizationServer: String): AuthorizationServerMetadata? =
        wellKnownCandidates(authorizationServer, WELL_KNOWN_AUTHORIZATION_SERVER)
            .plus(wellKnownCandidates(authorizationServer, WELL_KNOWN_OPENID_CONFIGURATION))
            .firstNotNullOfOrNull { url ->
                fetchJson(url)?.let(::parseAuthorizationServer)
            }

    /** RFC 7591：动态客户端注册（公共客户端 + PKCE；服务器签发 secret 时原样带回）。 */
    suspend fun registerClient(
        registrationEndpoint: String,
        redirectUri: String,
        clientName: String,
        scope: String?,
    ): RegisteredClient? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("client_name", clientName)
            put("redirect_uris", JSONArray().put(redirectUri))
            put("grant_types", JSONArray().put("authorization_code").put("refresh_token"))
            put("response_types", JSONArray().put("code"))
            put("token_endpoint_auth_method", "none")
            if (!scope.isNullOrBlank()) put("scope", scope)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(registrationEndpoint).post(body)
            .header("Accept", "application/json")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                val clientId = json.optString("client_id").takeIf { it.isNotBlank() } ?: return@withContext null
                RegisteredClient(
                    clientId = clientId,
                    clientSecret = json.optString("client_secret").takeIf { it.isNotBlank() },
                    scope = json.optString("scope").takeIf { it.isNotBlank() },
                )
            }
        }.getOrNull()
    }

    /**
     * well-known 候选序列（RFC 8414 §3.1 / RFC 9728 §3.1）：
     * 先做 path 插入（`https://host/.well-known/xxx/<path>`），再回退根路径。
     */
    internal fun wellKnownCandidates(baseUrl: String, wellKnown: String): List<String> {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return emptyList()
        val schemeEnd = trimmed.indexOf("://") + 3
        val pathStart = trimmed.indexOf('/', schemeEnd)
        val origin = if (pathStart < 0) trimmed else trimmed.substring(0, pathStart)
        val path = if (pathStart < 0) "" else trimmed.substring(pathStart)
        return buildList {
            if (path.isNotBlank()) add("$origin/$wellKnown$path")
            add("$origin/$wellKnown")
        }.distinct()
    }

    private suspend fun fetchJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private fun parseProtectedResource(json: JSONObject): ProtectedResource? {
        val servers = json.optJSONArray("authorization_servers") ?: return null
        val list = servers.toStringList().filter { it.isNotBlank() }
        if (list.isEmpty()) return null
        return ProtectedResource(
            authorizationServers = list,
            resource = json.optString("resource").takeIf { it.isNotBlank() },
            scopesSupported = json.optJSONArray("scopes_supported").toStringList(),
        )
    }

    private fun parseAuthorizationServer(json: JSONObject): AuthorizationServerMetadata? {
        val authorization = json.optString("authorization_endpoint").takeIf { it.isNotBlank() }
        val token = json.optString("token_endpoint").takeIf { it.isNotBlank() }
        if (authorization == null && token == null) return null
        return AuthorizationServerMetadata(
            authorizationEndpoint = authorization,
            tokenEndpoint = token,
            registrationEndpoint = json.optString("registration_endpoint").takeIf { it.isNotBlank() },
            scopesSupported = json.optJSONArray("scopes_supported").toStringList(),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private companion object {
        const val WELL_KNOWN_PROTECTED_RESOURCE = ".well-known/oauth-protected-resource"
        const val WELL_KNOWN_AUTHORIZATION_SERVER = ".well-known/oauth-authorization-server"
        const val WELL_KNOWN_OPENID_CONFIGURATION = ".well-known/openid-configuration"
    }
}
