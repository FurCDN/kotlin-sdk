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
)

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
