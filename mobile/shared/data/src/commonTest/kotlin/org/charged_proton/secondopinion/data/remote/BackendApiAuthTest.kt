package org.charged_proton.secondopinion.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Auth behavior of [backendHttpClient]: bearer-token header injection and
 * the 401 → onUnauthorized session-drop hook.
 */
class BackendApiAuthTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val recordingJson = """{"id":"any","status":"queued","failure_stage":null}"""

    @Test
    fun attachesBearerToken_whenTokenAvailable() = runTest {
        var authHeader: String? = null
        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            respond(recordingJson, HttpStatusCode.OK, json)
        }
        val api = BackendApi(
            backendHttpClient(engine, tokenProvider = { "fake:uid-1:+911234567890" }),
            "http://test",
        )

        api.getRecording("rec-1")

        assertEquals("Bearer fake:uid-1:+911234567890", authHeader)
    }

    @Test
    fun omitsAuthorizationHeader_whenSignedOut() = runTest {
        var authHeader: String? = "sentinel"
        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            respond(recordingJson, HttpStatusCode.OK, json)
        }
        val api = BackendApi(backendHttpClient(engine), "http://test")

        api.getRecording("rec-1")

        assertNull(authHeader)
    }

    @Test
    fun firesOnUnauthorized_andThrows_on401() = runTest {
        var unauthorizedCalls = 0
        val engine = MockEngine {
            respond("""{"title":"Not authenticated"}""", HttpStatusCode.Unauthorized, json)
        }
        val api = BackendApi(
            backendHttpClient(
                engine,
                tokenProvider = { "fake:uid-1" },
                onUnauthorized = { unauthorizedCalls++ },
            ),
            "http://test",
        )

        assertFailsWith<ClientRequestException> { api.getRecording("rec-1") }

        assertEquals(1, unauthorizedCalls)
    }

    @Test
    fun doesNotFireOnUnauthorized_onOtherErrors() = runTest {
        var unauthorizedCalls = 0
        val engine = MockEngine {
            respond("""{"title":"Not found"}""", HttpStatusCode.NotFound, json)
        }
        val api = BackendApi(
            backendHttpClient(engine, onUnauthorized = { unauthorizedCalls++ }),
            "http://test",
        )

        assertFailsWith<ClientRequestException> { api.getRecording("rec-1") }

        assertEquals(0, unauthorizedCalls)
    }
}
