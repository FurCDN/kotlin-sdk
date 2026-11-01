package us.furcdn.api

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FurCdnClientTest {

    private lateinit var server: HttpServer
    private lateinit var client: FurCdnClient
    private var requestCount = 0
    private var lastRequestBody: String? = null

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        client = FurCdnClient("fck_test_secret", "http://127.0.0.1:${server.address.port}")
        requestCount = 0
        lastRequestBody = null
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    private fun respond(path: String, status: Int, body: String) {
        server.createContext(path) { ex ->
            requestCount++
            lastRequestBody = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val auth = ex.requestHeaders.getFirst("Authorization")
            if (auth != "Bearer fck_test_secret") {
                ex.sendResponseHeaders(401, 0)
                ex.close()
                return@createContext
            }
            val data = body.toByteArray(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, data.size.toLong())
            ex.responseBody.use { it.write(data) }
        }
    }

    @Test
    fun listDomains() {
        respond("/api/v1/domains", 200, """{"domains":[{"id":1,"name":"example.com","enabled":true}]}""")
        val domains = client.listDomains()
        assertEquals(1, domains.size)
        assertEquals("example.com", domains[0].name)
        assertTrue(domains[0].enabled)
    }

    @Test
    fun purgeCache() {
        respond("/api/v1/domains/42/purge", 200, """{"ok":true,"total":3,"success":3}""")
        val r = client.purgeCache(42)
        assertTrue(r.ok)
        assertEquals(3, r.total)
        assertEquals(3, r.success)
        // 舊行為：不帶 body，且舊回應（沒有 mode / removed）仍能解碼。
        assertEquals("", lastRequestBody)
        assertEquals("", r.mode)
        assertEquals(0, r.removed)
    }

    @Test
    fun purgeCacheAllWithOptions() {
        respond(
            "/api/v1/domains/42/purge",
            200,
            """{"ok":true,"mode":"all","total":5,"success":5}""",
        )
        val r = client.purgeCache(42, PurgeOptions(all = true))
        assertEquals("""{"all":true}""", lastRequestBody)
        assertEquals("all", r.mode)
        assertEquals(5, r.total)
        assertEquals(5, r.success)
        assertEquals(0, r.removed)
    }

    @Test
    fun purgeCacheUrls() {
        respond(
            "/api/v1/domains/42/purge",
            200,
            """{"ok":true,"mode":"selective","total":5,"success":5,"removed":12}""",
        )
        val r = client.purgeCache(
            42,
            PurgeOptions(urls = listOf("https://example.com/a.css?v=1", "/b.js")),
        )
        assertEquals(
            """{"urls":["https://example.com/a.css?v=1","/b.js"]}""",
            lastRequestBody,
        )
        assertEquals("selective", r.mode)
        assertEquals(12, r.removed)
    }

    @Test
    fun purgeCachePrefixes() {
        respond(
            "/api/v1/domains/42/purge",
            200,
            """{"ok":true,"mode":"selective","total":5,"success":5,"removed":3}""",
        )
        client.purgeCache(
            42,
            PurgeOptions(prefixes = listOf("/static/", "https://example.com/img/")),
        )
        assertEquals(
            """{"prefixes":["/static/","https://example.com/img/"]}""",
            lastRequestBody,
        )
    }

    @Test
    fun purgeCachePatterns() {
        respond(
            "/api/v1/domains/42/purge",
            200,
            """{"ok":true,"mode":"selective","total":5,"success":5,"removed":7}""",
        )
        client.purgeCache(42, PurgeOptions(patterns = listOf("/assets/*.js")))
        assertEquals("""{"patterns":["/assets/*.js"]}""", lastRequestBody)
    }

    @Test
    fun purgeCacheCombinedSelectors() {
        respond(
            "/api/v1/domains/42/purge",
            200,
            """{"ok":true,"mode":"selective","total":5,"success":5,"removed":9}""",
        )
        client.purgeCache(
            42,
            PurgeOptions(
                urls = listOf("/a.js"),
                prefixes = listOf("/static/"),
                patterns = listOf("/img/*"),
            ),
        )
        assertEquals(
            """{"urls":["/a.js"],"prefixes":["/static/"],"patterns":["/img/*"]}""",
            lastRequestBody,
        )
    }

    @Test
    fun purgeCacheSelectiveError() {
        respond("/api/v1/domains/42/purge", 400, """{"error":"patterns 必須以 / 開頭"}""")
        val ex = assertFailsWith<FurCdnException> {
            client.purgeCache(42, PurgeOptions(patterns = listOf("assets/*.js")))
        }
        assertEquals(400, ex.statusCode)
        assertTrue(ex.message!!.contains("patterns"))
    }

    @Test
    fun purgeOptionsRejectAllWithSelectors() {
        assertFailsWith<IllegalArgumentException> {
            PurgeOptions(all = true, urls = listOf("/a.js"))
        }
        assertFailsWith<IllegalArgumentException> {
            PurgeOptions(all = true, prefixes = listOf("/static/"))
        }
        assertFailsWith<IllegalArgumentException> {
            PurgeOptions(all = true, patterns = listOf("/img/*"))
        }
    }

    @Test
    fun purgeOptionsRequireAtLeastOneField() {
        assertFailsWith<IllegalArgumentException> { PurgeOptions() }
        assertFailsWith<IllegalArgumentException> { PurgeOptions(all = false) }
        // 驗證在建立物件時就發生，因此客戶端不會發出任何 HTTP 請求。
        respond("/api/v1/domains/42/purge", 200, """{"ok":true,"total":1,"success":1}""")
        assertFailsWith<IllegalArgumentException> { client.purgeCache(42, PurgeOptions()) }
        assertEquals(0, requestCount)
    }

    @Test
    fun purgeOptionsEntryLimits() {
        val maxEntries = PurgeOptions.MAX_ENTRIES
        val atLimit = PurgeOptions(
            urls = List(maxEntries - 1) { "/p$it.js" },
            prefixes = listOf("/static/"),
        )
        assertEquals(maxEntries, atLimit.urls.size + atLimit.prefixes.size)

        assertFailsWith<IllegalArgumentException> {
            PurgeOptions(
                urls = List(maxEntries) { "/p$it.js" },
                prefixes = listOf("/static/"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PurgeOptions(patterns = List(maxEntries + 1) { "/p$it/*" })
        }
    }

    @Test
    fun purgeOptionsEntryLengthAndEmptiness() {
        val maxLength = PurgeOptions.MAX_ENTRY_LENGTH
        PurgeOptions(urls = listOf("/" + "a".repeat(maxLength - 1)))

        assertFailsWith<IllegalArgumentException> {
            PurgeOptions(urls = listOf("/" + "a".repeat(maxLength)))
        }
        assertFailsWith<IllegalArgumentException> { PurgeOptions(urls = listOf("")) }
        assertFailsWith<IllegalArgumentException> { PurgeOptions(prefixes = listOf("")) }
        assertFailsWith<IllegalArgumentException> { PurgeOptions(patterns = listOf("")) }
    }

    @Test
    fun uploadSsl() {
        respond("/api/v1/domains/7/ssl", 200, """{"ok":true}""")
        client.uploadSsl(7, "CERT", "KEY")
    }

    @Test
    fun originIps() {
        respond("/api/public/origin-ips", 200, """{"ips":["1.2.3.4","5.6.7.8"],"count":2}""")
        val ips = client.originIps()
        assertEquals(2, ips.size)
        assertEquals("1.2.3.4", ips[0])
        assertEquals("5.6.7.8", ips[1])
    }

    @Test
    fun apiError() {
        respond("/api/v1/domains", 401, """{"error":"未授權"}""")
        val ex = assertFailsWith<FurCdnException> { client.listDomains() }
        assertEquals(401, ex.statusCode)
        assertTrue(ex.message!!.contains("未授權"))
    }
}
