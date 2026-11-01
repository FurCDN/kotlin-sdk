package us.furcdn.api

import kotlinx.serialization.Serializable

@Serializable
data class Domain(
    val id: Long,
    val name: String,
    val enabled: Boolean,
)

@Serializable
data class PurgeResult(
    val ok: Boolean,
    val total: Int,
    val success: Int,
    /** `"all"`（整站）或 `"selective"`（選擇性）；舊版伺服器不會回傳此欄位。 */
    val mode: String = "",
    /** 選擇性清除時，各節點合計刪除的快取條目數（同一 URL 快取在 3 個節點就算 3）。 */
    val removed: Int = 0,
)

/**
 * 選擇性清除快取的條件，至少需指定 [all]、[urls]、[prefixes]、[patterns] 其中之一。
 *
 * - [all]：清掉整個域名快取，不可與其他選擇器併用。
 * - [urls]：精確 URL，可為絕對 URL（host 必須等於域名）或以 `/` 開頭的路徑；
 *   查詢字串算在比對範圍內，除非域名開啟了忽略查詢字串。
 * - [prefixes]：URL 路徑的前綴比對，查詢字串忽略。
 * - [patterns]：URL 路徑的 glob，`*` 可匹配任意字元（含 `/`），必須以 `/` 開頭。
 *
 * 三個選擇器可同時使用（聯集）。不合法的組合會在建立物件時就丟出
 * [IllegalArgumentException]，不會發出任何 HTTP 請求。
 */
@Serializable
data class PurgeOptions(
    val all: Boolean = false,
    val urls: List<String> = emptyList(),
    val prefixes: List<String> = emptyList(),
    val patterns: List<String> = emptyList(),
) {
    init {
        val entries = urls + prefixes + patterns
        require(!all || entries.isEmpty()) {
            "all = true 不能與 urls / prefixes / patterns 併用"
        }
        require(all || entries.isNotEmpty()) {
            "至少要指定 all = true 或 urls / prefixes / patterns 其中一個"
        }
        require(entries.size <= MAX_ENTRIES) {
            "選擇性清除最多 $MAX_ENTRIES 筆，目前 ${entries.size} 筆"
        }
        entries.forEach { entry ->
            require(entry.isNotEmpty()) { "清除條件不可為空字串" }
            require(entry.length <= MAX_ENTRY_LENGTH) {
                "單筆清除條件最長 $MAX_ENTRY_LENGTH 字元，目前 ${entry.length} 字元：$entry"
            }
        }
    }

    companion object {
        /** 伺服器端限制：urls + prefixes + patterns 合計最多 100 筆。 */
        const val MAX_ENTRIES = 100

        /** 伺服器端限制：單筆清除條件最長 2048 字元。 */
        const val MAX_ENTRY_LENGTH = 2048
    }
}

@Serializable
internal data class DomainsResponse(val domains: List<Domain>)

@Serializable
internal data class OriginIpsResponse(val ips: List<String> = emptyList(), val count: Int = 0)

@Serializable
internal data class OkResponse(val ok: Boolean = true)

@Serializable
internal data class ErrorResponse(val error: String? = null)

@Serializable
internal data class SslBody(val cert: String, val key: String)

class FurCdnException(val statusCode: Int, message: String) : RuntimeException(message)
