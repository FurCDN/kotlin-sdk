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

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        client = FurCdnClient("fck_test_secret", "http://127.0.0.1:${server.address.port}")
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    private fun respond(path: String, status: Int, body: String) {
        server.createContext(path) { ex ->
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
    }

    @Test
    fun uploadSsl() {
        respond("/api/v1/domains/7/ssl", 200, """{"ok":true}""")
        client.uploadSsl(7, "CERT", "KEY")
    }

    @Test
    fun apiError() {
        respond("/api/v1/domains", 401, """{"error":"未授權"}""")
        val ex = assertFailsWith<FurCdnException> { client.listDomains() }
        assertEquals(401, ex.statusCode)
        assertTrue(ex.message!!.contains("未授權"))
    }
}
