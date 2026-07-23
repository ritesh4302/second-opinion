package org.charged_proton.secondopinion.data.repository

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.charged_proton.secondopinion.data.platform.AudioFileDeleter
import org.charged_proton.secondopinion.data.platform.AudioFileReader
import org.charged_proton.secondopinion.data.remote.BackendApi
import org.charged_proton.secondopinion.data.remote.FeedbackRequestDto
import org.charged_proton.secondopinion.data.remote.pipelineStageFor
import org.charged_proton.secondopinion.data.remote.toDomain
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository

/**
 * Real backend implementation of the assessment port (ANDROID_APP.md §5.2):
 * multipart upload to POST /v1/recordings (the case id is the recording id),
 * status polling until completed/failed, then GET .../assessment. Feedback is
 * POSTed and cached locally — the backend has no read endpoint for it.
 */
class BackendAssessmentRepository(
    private val api: BackendApi,
    private val caseRepository: CaseRepository,
    private val audioFileReader: AudioFileReader,
    private val audioFileDeleter: AudioFileDeleter,
    private val locale: String = "hi-IN",
    private val pollIntervalMillis: Long = 2_000,
    private val maxPollMillis: Long = 15 * 60_000,
) : AssessmentRepository {

    private val mutex = Mutex()
    private val assessmentsByCaseId = mutableMapOf<String, Assessment>()
    private val feedbackByAssessmentId = mutableMapOf<String, Feedback>()

    override fun requestAssessment(caseId: String): Flow<AssessmentProgress> = flow {
        getAssessment(caseId)?.let {
            emit(AssessmentProgress.Completed(it))
            return@flow
        }
        val case = caseRepository.getCase(caseId)
        if (case == null) {
            emit(AssessmentProgress.Failed("Case not found: $caseId"))
            return@flow
        }

        try {
            caseRepository.updateStatus(caseId, CaseStatus.UPLOADING)
            emit(AssessmentProgress.InProgress(PipelineStage.UPLOADING))
            val audio = withContext(Dispatchers.Default) {
                audioFileReader.read(case.recording.filePath)
            }
            api.uploadRecording(
                caseId,
                audio,
                case.recording.durationMillis,
                locale,
                case.recording.consentConfirmed,
            )

            caseRepository.updateStatus(caseId, CaseStatus.PROCESSING)
            pollUntilDone(caseId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            caseRepository.updateStatus(caseId, CaseStatus.FAILED)
            emit(AssessmentProgress.Failed(failure.message ?: "Could not reach the server"))
        }
    }

    private suspend fun FlowCollector<AssessmentProgress>.pollUntilDone(caseId: String) {
        var lastStage = PipelineStage.UPLOADING
        var elapsedMillis = 0L
        while (true) {
            val recording = api.getRecording(caseId)
            when (recording.status) {
                "completed" -> {
                    val assessment = api.getAssessment(caseId).toDomain()
                    mutex.withLock { assessmentsByCaseId[caseId] = assessment }
                    caseRepository.updateStatus(caseId, CaseStatus.COMPLETED)
                    emit(AssessmentProgress.Completed(assessment))
                    return
                }
                "failed" -> {
                    caseRepository.updateStatus(caseId, CaseStatus.FAILED)
                    val stage = recording.failureStage ?: "unknown"
                    emit(AssessmentProgress.Failed("Pipeline failed at the $stage stage"))
                    return
                }
                else -> {
                    val stage = pipelineStageFor(recording.status)
                    if (stage != lastStage) {
                        emit(AssessmentProgress.InProgress(stage))
                        lastStage = stage
                    }
                }
            }
            if (elapsedMillis >= maxPollMillis) {
                caseRepository.updateStatus(caseId, CaseStatus.FAILED)
                emit(AssessmentProgress.Failed("Timed out waiting for the assessment"))
                return
            }
            delay(pollIntervalMillis)
            elapsedMillis += pollIntervalMillis
        }
    }

    override suspend fun getAssessment(caseId: String): Assessment? {
        mutex.withLock { assessmentsByCaseId[caseId] }?.let { return it }
        val assessment = try {
            api.getAssessment(caseId).toDomain()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (notReady: ClientRequestException) {
            if (notReady.response.status == HttpStatusCode.NotFound) return null
            throw notReady
        } catch (unreachable: Exception) {
            return null // treat "can't reach backend" as "not yet available"
        }
        mutex.withLock { assessmentsByCaseId[caseId] = assessment }
        return assessment
    }

    override suspend fun submitFeedback(feedback: Feedback): Result<Unit> = runCatching {
        api.submitFeedback(
            feedback.assessmentId,
            FeedbackRequestDto(decision = feedback.decision.name.lowercase(), note = feedback.note),
        )
        mutex.withLock { feedbackByAssessmentId[feedback.assessmentId] = feedback }
    }

    override suspend fun getFeedback(assessmentId: String): Feedback? =
        mutex.withLock { feedbackByAssessmentId[assessmentId] }

    override suspend fun deleteCase(caseId: String): Result<Unit> = runCatching {
        try {
            api.deleteRecording(caseId)
        } catch (notFound: ClientRequestException) {
            // Never uploaded or already purged server-side; still erase locally.
            if (notFound.response.status != HttpStatusCode.NotFound) throw notFound
        }
        caseRepository.getCase(caseId)?.let { case ->
            withContext(Dispatchers.Default) { audioFileDeleter.delete(case.recording.filePath) }
        }
        caseRepository.deleteCase(caseId)
        mutex.withLock {
            assessmentsByCaseId.remove(caseId)?.let { feedbackByAssessmentId.remove(it.id) }
        }
    }
}
