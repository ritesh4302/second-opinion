package org.charged_proton.secondopinion.data.repository

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.data.remote.BackendApi
import org.charged_proton.secondopinion.data.remote.backendHttpClient
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.model.Recording

/**
 * Drives [BackendAssessmentRepository] against a scripted Ktor [MockEngine]:
 * upload → status polling → assessment mapping, pipeline failure, network
 * failure, timeout, and the feedback POST body.
 */
class BackendAssessmentRepositoryTest {

    /** Scripted backend: recording statuses are consumed one per poll. */
    private class FakeBackend {
        val recordingStatuses = ArrayDeque<String>()
        var failureStage: String? = null
        var assessmentJson: String? = null
        var uploadCount = 0
        val feedbackBodies = mutableListOf<String>()

        fun handle(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
            val path = request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            return when {
                request.method == HttpMethod.Post && path == "/v1/recordings" -> {
                    uploadCount++
                    scope.respond(recordingJson("queued"), HttpStatusCode.Accepted, json)
                }
                request.method == HttpMethod.Get && path.endsWith("/assessment") ->
                    if (uploadCount > 0 && assessmentJson != null) {
                        scope.respond(assessmentJson!!, HttpStatusCode.OK, json)
                    } else {
                        scope.respond("""{"title":"Assessment not ready"}""", HttpStatusCode.NotFound, json)
                    }
                request.method == HttpMethod.Get && path.startsWith("/v1/recordings/") ->
                    scope.respond(recordingJson(recordingStatuses.removeFirst()), HttpStatusCode.OK, json)
                request.method == HttpMethod.Post && path.endsWith("/feedback") -> {
                    feedbackBodies += (request.body as TextContent).text
                    scope.respond("{}", HttpStatusCode.Created, json)
                }
                else -> scope.respond("unexpected: $path", HttpStatusCode.NotFound)
            }
        }

        private fun recordingJson(status: String) =
            """{"id":"any","status":"$status","failure_stage":${failureStage?.let { "\"$it\"" } ?: "null"}}"""
    }

    private val backend = FakeBackend()
    private val caseRepository = InMemoryCaseRepository()

    private fun repository(maxPollMillis: Long = 60_000) = BackendAssessmentRepository(
        api = BackendApi(backendHttpClient(MockEngine { backend.handle(this, it) }), "http://test"),
        caseRepository = caseRepository,
        audioFileReader = { byteArrayOf(1, 2, 3) },
        pollIntervalMillis = 10,
        maxPollMillis = maxPollMillis,
    )

    private suspend fun seededCaseId(): String =
        caseRepository.createCase(Recording("/tmp/rec.m4a", 1_000L, durationMillis = 4_200)).id

    @Test
    fun requestAssessment_uploadsPollsAndMapsTheAssessment() = runTest {
        val caseId = seededCaseId()
        backend.recordingStatuses.addAll(listOf("queued", "transcribing", "assessing", "completed"))
        backend.assessmentJson = """
            {"id":"a-1","recording_id":"$caseId","symptom_summary":"fever, headache",
             "conditions":[{"name":"Viral fever","confidence_percent":70,"rationale":"classic"}],
             "red_flags":[{"description":"stiff neck","action":"refer now"}],
             "otc_guidance":[{"medicine":"Paracetamol","dosage":"500 mg","note":"after food"},
                             {"medicine":"Azithromycin","dosage":"500 mg","note":"Schedule H","prescription":true}],
             "model_id":"sarvam-30b","prompt_version":"assess-v3","created_at":"2026-07-20T00:00:00Z"}
        """.trimIndent()

        val emissions = repository().requestAssessment(caseId).toList()

        assertEquals(AssessmentProgress.InProgress(PipelineStage.UPLOADING), emissions.first())
        assertEquals(
            listOf(PipelineStage.TRANSCRIBING, PipelineStage.ASSESSING),
            emissions.drop(1).dropLast(1).map { (it as AssessmentProgress.InProgress).stage },
        )
        val completed = assertIs<AssessmentProgress.Completed>(emissions.last())
        with(completed.assessment) {
            assertEquals("a-1", id)
            assertEquals(caseId, this.caseId)
            assertEquals("fever, headache", symptomSummary)
            assertEquals(70, conditions.single().confidencePercent)
            assertEquals("refer now", redFlags.single().action)
            assertEquals(listOf(false, true), otcGuidance.map { it.prescription })
            assertTrue(disclaimer.isNotBlank())
        }
        assertEquals(1, backend.uploadCount)
        assertEquals(CaseStatus.COMPLETED, caseRepository.getCase(caseId)?.status)
    }

    @Test
    fun requestAssessment_pipelineFailure_reportsFailedStage() = runTest {
        val caseId = seededCaseId()
        backend.failureStage = "speech"
        backend.recordingStatuses.addAll(listOf("diarizing", "failed"))

        val emissions = repository().requestAssessment(caseId).toList()

        val failed = assertIs<AssessmentProgress.Failed>(emissions.last())
        assertEquals("Pipeline failed at the speech stage", failed.reason)
        assertEquals(CaseStatus.FAILED, caseRepository.getCase(caseId)?.status)
    }

    @Test
    fun requestAssessment_unknownCase_failsWithoutUpload() = runTest {
        val emissions = repository().requestAssessment("missing").toList()

        assertIs<AssessmentProgress.Failed>(emissions.single())
        assertEquals(0, backend.uploadCount)
    }

    @Test
    fun requestAssessment_timesOutWhenPipelineNeverFinishes() = runTest {
        val caseId = seededCaseId()
        repeat(10) { backend.recordingStatuses.add("queued") }

        val emissions = repository(maxPollMillis = 30).requestAssessment(caseId).toList()

        val failed = assertIs<AssessmentProgress.Failed>(emissions.last())
        assertEquals("Timed out waiting for the assessment", failed.reason)
        assertEquals(CaseStatus.FAILED, caseRepository.getCase(caseId)?.status)
    }

    @Test
    fun requestAssessment_networkError_failsTheCase() = runTest {
        val caseId = seededCaseId()
        val broken = BackendAssessmentRepository(
            api = BackendApi(
                backendHttpClient(MockEngine { throw RuntimeException("connection refused") }),
                "http://test",
            ),
            caseRepository = caseRepository,
            audioFileReader = { byteArrayOf(1) },
        )

        val emissions = broken.requestAssessment(caseId).toList()

        val failed = assertIs<AssessmentProgress.Failed>(emissions.last())
        assertEquals("connection refused", failed.reason)
        assertEquals(CaseStatus.FAILED, caseRepository.getCase(caseId)?.status)
    }

    @Test
    fun getAssessment_notReadyOnBackend_returnsNull() = runTest {
        assertNull(repository().getAssessment(seededCaseId()))
    }

    @Test
    fun submitFeedback_postsLowercaseDecisionAndCachesIt() = runTest {
        val feedback = Feedback("a-1", PharmacistDecision.OVERRIDDEN, note = "gave ORS")
        val repository = repository()

        val result = repository.submitFeedback(feedback)

        assertTrue(result.isSuccess)
        assertEquals(
            """{"decision":"overridden","note":"gave ORS"}""",
            backend.feedbackBodies.single(),
        )
        assertEquals(feedback, repository.getFeedback("a-1"))
    }
}
