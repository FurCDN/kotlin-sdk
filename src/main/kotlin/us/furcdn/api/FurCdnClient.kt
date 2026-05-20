package us.furcdn.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class FurCdnClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 列出當前 API key 擁有者的所有域名 */
    fun listDomains(): List<Domain> =
        json.decodeFromString<DomainsResponse>(request("GET", "/api/v1/domains")).domains

    /** 刷新指定域名所有節點的 L1+L2 快取 */
    fun purgeCache(domainId: Long): PurgeResult =
        json.decodeFromString(request("POST", "/api/v1/domains/$domainId/purge"))

    /** 為指定域名上傳 PEM 格式憑證和私鑰，會關閉自動續約 */
    fun uploadSsl(domainId: Long, cert: String, key: String) {
        val body = json.encodeToString(SslBody(cert, key))
        request("POST", "/api/v1/domains/$domainId/ssl", body)
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $apiKey")

        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody()
                        else HttpRequest.BodyPublishers.ofString(body)
        if (body != null) builder.header("Content-Type", "application/json")
        builder.method(method, publisher)

        val res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val text = res.body() ?: ""

        if (res.statusCode() >= 400) {
            val msg = runCatching { json.decodeFromString<ErrorResponse>(text).error }
                .getOrNull() ?: text
            throw FurCdnException(res.statusCode(), msg)
        }
        return text
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.furcdn.us"
    }
}
