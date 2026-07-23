package org.charged_proton.secondopinion.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.AuthUser
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.ConditionHypothesis
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.OtcAdvice
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.RedFlag
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.platform.AudioPlayer
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository

fun testCase(
    id: String = "case-1",
    status: CaseStatus = CaseStatus.RECORDED,
    createdAtEpochMillis: Long = 1_000L,
) = SymptomCase(
    id = id,
    recording = Recording("/tmp/$id.m4a", createdAtEpochMillis),
    status = status,
    createdAtEpochMillis = createdAtEpochMillis,
)

fun testAssessment(
    id: String = "assessment-1",
    caseId: String = "case-1",
    redFlags: List<RedFlag> = emptyList(),
    otcGuidance: List<OtcAdvice> = emptyList(),
) = Assessment(
    id = id,
    caseId = caseId,
    symptomSummary = "Fever with headache for 2 days",
    conditions = listOf(ConditionHypothesis("Viral fever", 70, "classic cluster")),
    redFlags = redFlags,
    otcGuidance = otcGuidance,
    disclaimer = "Triage support only",
)

class FakeAudioRecorder : AudioRecorder {
    var stopResult: Recording? = Recording("/tmp/test.m4a", 1_000L)
    var releaseCalls = 0

    override var isRecording: Boolean = false
        private set

    override fun start() {
        isRecording = true
    }

    override suspend fun stop(): Recording? {
        isRecording = false
        return stopResult
    }

    override fun release() {
        releaseCalls++
        isRecording = false
    }
}

class FakeAudioPlayer : AudioPlayer {
    val playedFilePaths = mutableListOf<String>()
    var stopCalls = 0
    private var onCompleted: (() -> Unit)? = null

    override fun play(filePath: String, onCompleted: () -> Unit) {
        playedFilePaths += filePath
        this.onCompleted = onCompleted
    }

    override fun stop() {
        stopCalls++
        onCompleted = null
    }
}

class FakeCaseRepository : CaseRepository {
    val cases = MutableStateFlow<List<SymptomCase>>(emptyList())
    private var nextId = 1

    override fun observeCases(): Flow<List<SymptomCase>> = cases

    override suspend fun createCase(recording: Recording): SymptomCase {
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

    override val authState = state

    override suspend fun requestOtp(phoneNumber: String): Result<Unit> {
        requestedPhoneNumbers += phoneNumber
        requestOtpError?.let { return Result.failure(it) }
        return Result.success(Unit)
    }

    override suspend fun verifyOtp(code: String): Result<AuthUser> {
        verifyOtpError?.let { return Result.failure(it) }
        val user = AuthUser("uid-1", "+911234567890")
        state.value = AuthState.SignedIn(user)
        return Result.success(user)
    }

    override suspend fun currentToken(): String? = token

    override suspend fun signOut() {
        state.value = AuthState.SignedOut
    }
}

class FakeAssessmentRepository : AssessmentRepository {
    var progressFlow: Flow<AssessmentProgress> = emptyFlow()
    val assessmentsByCaseId = mutableMapOf<String, Assessment>()
    val feedbackByAssessmentId = mutableMapOf<String, Feedback>()
    var submitError: Throwable? = null

    override fun requestAssessment(caseId: String): Flow<AssessmentProgress> = progressFlow

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
