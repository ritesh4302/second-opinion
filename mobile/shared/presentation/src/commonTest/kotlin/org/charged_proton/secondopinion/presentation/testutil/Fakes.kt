package org.charged_proton.secondopinion.presentation.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.AuthUser
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.ConditionHypothesis
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.platform.AudioPlayer
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository

fun testRecording(
    filePath: String = "/tmp/rec.m4a",
    createdAtEpochMillis: Long = 1_000L,
) = Recording(filePath, createdAtEpochMillis)

fun testAssessment(
    id: String = "assessment-1",
    caseId: String = "case-1",
) = Assessment(
    id = id,
    caseId = caseId,
    symptomSummary = "summary",
    conditions = listOf(ConditionHypothesis("cold", 70, "classic cluster")),
    redFlags = emptyList(),
    otcGuidance = emptyList(),
    disclaimer = "triage only",
)

class FakeAudioRecorder : AudioRecorder {
    var startError: Throwable? = null
    var stopError: Throwable? = null
    var stopResult: Recording? = null
    var releaseCalls = 0

    override var isRecording: Boolean = false
        private set

    override fun start() {
        startError?.let { throw it }
        isRecording = true
    }

    override suspend fun stop(): Recording? {
        stopError?.let { throw it }
        isRecording = false
        return stopResult
    }

    override fun release() {
        releaseCalls++
        isRecording = false
    }
}

class FakeAudioPlayer : AudioPlayer {
    var playError: Throwable? = null
    val playedFilePaths = mutableListOf<String>()
    var stopCalls = 0
    private var onCompleted: (() -> Unit)? = null

    override fun play(filePath: String, onCompleted: () -> Unit) {
        playError?.let { throw it }
        playedFilePaths += filePath
        this.onCompleted = onCompleted
    }

    override fun stop() {
        stopCalls++
        onCompleted = null
    }

    /** Simulates playback finishing on its own. */
    fun completePlayback() {
        val callback = onCompleted
        onCompleted = null
        callback?.invoke()
    }
}

class FakeCaseRepository : CaseRepository {
    val cases = MutableStateFlow<List<SymptomCase>>(emptyList())
    var createError: Throwable? = null
    private var nextId = 1

    override fun observeCases(): Flow<List<SymptomCase>> =
        cases.map { list -> list.sortedByDescending(SymptomCase::createdAtEpochMillis) }

    override suspend fun createCase(recording: Recording): SymptomCase {
        createError?.let { throw it }
        val case = SymptomCase(
            id = "case-${nextId++}",
            recording = recording,
            status = CaseStatus.RECORDED,
            createdAtEpochMillis = recording.createdAtEpochMillis,
        )
        cases.update { it + case }
        return case
    }

    override suspend fun getCase(caseId: String): SymptomCase? =
        cases.value.firstOrNull { it.id == caseId }

    override suspend fun updateStatus(caseId: String, status: CaseStatus) {
        cases.update { list ->
            list.map { if (it.id == caseId) it.copy(status = status) else it }
        }
    }
}

class FakeAuthClient : AuthClient {
    val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    var requestOtpError: Throwable? = null
    var verifyOtpError: Throwable? = null
    var token: String? = null
    val requestedPhoneNumbers = mutableListOf<String>()
    val verifiedCodes = mutableListOf<String>()
    var signOutCalls = 0

    override val authState = state

    override suspend fun requestOtp(phoneNumber: String): Result<Unit> {
        requestedPhoneNumbers += phoneNumber
        requestOtpError?.let { return Result.failure(it) }
        return Result.success(Unit)
    }

    override suspend fun verifyOtp(code: String): Result<AuthUser> {
        verifiedCodes += code
        verifyOtpError?.let { return Result.failure(it) }
        val user = AuthUser("uid-1", "+911234567890")
        state.value = AuthState.SignedIn(user)
        return Result.success(user)
    }

    override suspend fun currentToken(): String? = token

    override suspend fun signOut() {
        signOutCalls++
        state.value = AuthState.SignedOut
    }
}

class FakeAssessmentRepository : AssessmentRepository {
    /** Emissions are driven by tests; [progressOverride] replaces it when set. */
    val progress = MutableSharedFlow<AssessmentProgress>()
    var progressOverride: Flow<AssessmentProgress>? = null
    val requestedCaseIds = mutableListOf<String>()
    val assessmentsByCaseId = mutableMapOf<String, Assessment>()
    val feedbackByAssessmentId = mutableMapOf<String, Feedback>()
    var submitError: Throwable? = null

    override fun requestAssessment(caseId: String): Flow<AssessmentProgress> {
        requestedCaseIds += caseId
        return progressOverride ?: progress
    }

    override suspend fun getAssessment(caseId: String): Assessment? =
        assessmentsByCaseId[caseId]

    override suspend fun submitFeedback(feedback: Feedback): Result<Unit> {
        submitError?.let { return Result.failure(it) }
        feedbackByAssessmentId[feedback.assessmentId] = feedback
        return Result.success(Unit)
    }

    override suspend fun getFeedback(assessmentId: String): Feedback? =
        feedbackByAssessmentId[assessmentId]
}
