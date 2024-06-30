package digitalwallet

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import jakarta.inject.Inject

@MicronautTest
class HelloControllerTest(@Client("/") val client: HttpClient) {

    @Test
    fun testHelloWorld() {
        val req: HttpRequest<Any> = HttpRequest.GET("/hello")
        val body = client.toBlocking().retrieve(req)
        assertNotNull(body)
        assertEquals("Hello World", body)
    }
}