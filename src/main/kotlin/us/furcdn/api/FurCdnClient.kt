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

    /**
     * 送請求 body 時用的設定：略過預設值，只送出呼叫端實際指定的欄位
     * （例如 `PurgeOptions(all = true)` 只會送出 `{"all":true}`）。
     */
    private val requestJson = Json {
        encodeDefaults = false
    }

    /** 列出當前 API key 擁有者的所有域名 */
    fun listDomains(): List<Domain> =
        json.decodeFromString<DomainsResponse>(request("GET", "/api/v1/domains")).domains

    /** 刷新指定域名所有節點的 L1+L2 快取 */
    fun purgeCache(domainId: Long): PurgeResult =
        json.decodeFromString(request("POST", "/api/v1/domains/$domainId/purge"))

    /**
     * 選擇性清除快取：只清 [options] 命中的內容，未命中的快取維持不變。
     *
     * ```kotlin
     * client.purgeCache(123, PurgeOptions(urls = listOf("https://example.com/a.css?v=1")))
     * client.purgeCache(123, PurgeOptions(prefixes = listOf("/static/")))
     * ```
     *
     * `patterns` 以 glob 比對路徑，`*` 可匹配任意字元（含斜線），值必須以斜線開頭；
     * 三種選擇器可同時使用（聯集）。
     *
     * 若要清整個域名，請用不帶 body 的 [purgeCache]；帶 `PurgeOptions(all = true)` 亦可。
     *
     * @throws IllegalArgumentException 條件不合法（選擇器與 `all` 併用、全部為空、
     *   超過 100 筆、空字串或單筆超過 2048 字元）。此檢查發生在發出請求之前。
     */
    fun purgeCache(domainId: Long, options: PurgeOptions): PurgeResult {
        val body = requestJson.encodeToString(options)
        return json.decodeFromString(request("POST", "/api/v1/domains/$domainId/purge", body))
    }

    /** 為指定域名上傳 PEM 格式憑證和私鑰，會關閉自動續約 */
    fun uploadSsl(domainId: Long, cert: String, key: String) {
        val body = json.encodeToString(SslBody(cert, key))
        request("POST", "/api/v1/domains/$domainId/ssl", body)
    }

    /**
     * 取得目前全部節點的回源 IP，供源站防火牆白名單使用。
     *
     * 這是公開端點，不需要 API key（SDK 仍會帶上 Authorization，端點會忽略）。
     * 清單「不是固定的」：節點隨時新增 / 下線 / 換 IP。請勿寫死，建議每 5~10 分鐘
     * 重新取得後同步防火牆規則。更可靠的回源鑑別方式是自定義回源 header。
     */
    fun originIps(): List<String> =
        json.decodeFromString<OriginIpsResponse>(request("GET", "/api/public/origin-ips?format=json")).ips

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
