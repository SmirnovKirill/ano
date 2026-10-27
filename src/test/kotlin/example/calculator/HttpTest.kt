package example.calculator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpTest {
    @LocalServerPort
    private var port: Int = 0

    private fun get(path: String) = HttpClient.newHttpClient().use { client ->
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    @Test
    fun `serves the form`() {
        val response = get("/")
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("<form id=\"calculator\">"))
    }

    @Test
    fun `returns exact sum and ISO date over HTTP`() {
        val response = get("/api/calculate?a=0.1&b=0.2&date=2024-02-29")
        val json = JsonMapper.builder().build().readTree(response.body())

        assertEquals(200, response.statusCode())
        assertTrue(json.path("sum").isString)
        assertEquals("0.3", json.path("sum").asString())
        assertEquals("2024-02-29", json.path("date").asString())
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "b=2&date=2024-02-29", "a=1&b=2", "a=oops&b=2&date=2024-02-29",
        "a=1&b=2&date=2025-02-29",
    ])
    fun `rejects missing or invalid input`(query: String) {
        assertEquals(400, get("/api/calculate?$query").statusCode())
    }
}
