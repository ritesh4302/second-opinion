package org.charged_proton.secondopinion.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** HTTP client for the Second Opinion backend (BACKEND.md §3). */
class BackendApi(
    private val client: HttpClient,
    baseUrl: String,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    /**
     * Multipart upload; idempotent on the client-generated [recordingId]
     * (202 on first upload, 200 on replay).
     */
    suspend fun uploadRecording(
        recordingId: String,
        audio: ByteArray,
        durationMillis: Long,
        locale: String,
    ): RecordingDto = client.submitFormWithBinaryData(
        url = "$baseUrl/v1/recordings",
        formData = formData {
            append("id", recordingId)
            append("duration_ms", durationMillis.toString())
            append("locale", locale)
            append(
                "audio",
                audio,
                Headers.build {
                    append(HttpHeaders.ContentType, "audio/mp4")
                    append(HttpHeaders.ContentDisposition, "filename=\"$recordingId.m4a\"")
                },
            )
        },
    ).body()

    suspend fun getRecording(recordingId: String): RecordingDto =
        client.get("$baseUrl/v1/recordings/$recordingId").body()

    suspend fun getAssessment(recordingId: String): AssessmentDto =
        client.get("$baseUrl/v1/recordings/$recordingId/assessment").body()

    suspend fun submitFeedback(assessmentId: String, body: FeedbackRequestDto) {
        client.post("$baseUrl/v1/assessments/$assessmentId/feedback") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
}

/** Client configured for the backend: kotlinx JSON + throw on non-2xx. */
fun backendHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
