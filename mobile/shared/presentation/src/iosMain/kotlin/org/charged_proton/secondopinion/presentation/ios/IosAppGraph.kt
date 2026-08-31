package org.charged_proton.secondopinion.presentation.ios

import org.charged_proton.secondopinion.data.auth.FakeGoogleAuthClient
import org.charged_proton.secondopinion.data.auth.UserDefaultsAuthTokenStore
import org.charged_proton.secondopinion.data.legal.PersistentLegalConsentRepository
import org.charged_proton.secondopinion.data.legal.UserDefaultsLegalAcceptanceStore
import org.charged_proton.secondopinion.data.local.IosDatabaseFactory
import org.charged_proton.secondopinion.data.local.SqlDelightAssessmentStore
import org.charged_proton.secondopinion.data.local.SqlDelightUploadQueueStore
import org.charged_proton.secondopinion.data.platform.IosAudioFileDeleter
import org.charged_proton.secondopinion.data.platform.IosAudioFileReader
import org.charged_proton.secondopinion.data.player.AvAudioPlayerAudioPlayer
import org.charged_proton.secondopinion.data.queue.UploadQueueProcessor
import org.charged_proton.secondopinion.data.recorder.AvAudioRecorder
import org.charged_proton.secondopinion.data.remote.createBackendApi
import org.charged_proton.secondopinion.data.repository.BackendAssessmentRepository
import org.charged_proton.secondopinion.data.repository.QueuedAssessmentRepository
import org.charged_proton.secondopinion.data.repository.SqlDelightCaseRepository
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.legal.LegalConsentRepository
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.usecase.AcceptLegalTermsUseCase
import org.charged_proton.secondopinion.domain.usecase.CreateCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.DeleteCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.GetFeedbackUseCase
import org.charged_proton.secondopinion.domain.usecase.GetLegalAcceptanceUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveAuthStateUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.domain.usecase.PlayRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.ReleaseRecorderUseCase
import org.charged_proton.secondopinion.domain.usecase.RequestAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.ResetPasswordUseCase
import org.charged_proton.secondopinion.domain.usecase.SignInUseCase
import org.charged_proton.secondopinion.domain.usecase.SignInWithEmailUseCase
import org.charged_proton.secondopinion.domain.usecase.SignOutUseCase
import org.charged_proton.secondopinion.domain.usecase.SignUpWithEmailUseCase
import org.charged_proton.secondopinion.domain.usecase.StartRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopPlaybackUseCase
import org.charged_proton.secondopinion.domain.usecase.StopRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.SubmitFeedbackUseCase
import org.charged_proton.secondopinion.presentation.assessment.AssessmentViewModel
import org.charged_proton.secondopinion.presentation.auth.AuthViewModel
import org.charged_proton.secondopinion.presentation.history.HistoryViewModel
import org.charged_proton.secondopinion.presentation.legal.LegalConsentViewModel
import org.charged_proton.secondopinion.presentation.login.LoginViewModel
import org.charged_proton.secondopinion.presentation.symptom.SymptomViewModel
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * Manual iOS composition root mirroring the Android Koin `appModule`
 * (mobile/app/.../di/AppModule.kt). Swift constructs one instance at launch
 * and pulls ViewModels from it; Koin is not used on iOS to keep the Swift
 * surface free of Koin types.
 *
 * Auth: the fake client for now (pairs with SO_AUTH_PROVIDER=fake); the
 * Firebase Auth iOS adapter replaces it in roadmap phase 1.
 */
class IosAppGraph(backendBaseUrl: String) {

    // Platform + persistent local data
    private val audioRecorder = AvAudioRecorder()
    private val audioPlayer = AvAudioPlayerAudioPlayer()
    private val database = IosDatabaseFactory().create()
    private val tokenStore = UserDefaultsAuthTokenStore()
    val authClient: AuthClient = FakeGoogleAuthClient(tokenStore)
    private val currentOwnerId: () -> String? =
        { (authClient.authState.value as? AuthState.SignedIn)?.user?.uid }
    private val caseRepository = SqlDelightCaseRepository(database, currentOwnerId)
    private val assessmentStore = SqlDelightAssessmentStore(database, currentOwnerId)
    private val queueStore = SqlDelightUploadQueueStore(database, currentOwnerId)
    val legalConsentRepository: LegalConsentRepository = PersistentLegalConsentRepository(
        UserDefaultsLegalAcceptanceStore(),
    ) { (NSDate().timeIntervalSince1970 * 1000).toLong() }

    // Backend + upload queue
    private val backendApi = createBackendApi(
        backendBaseUrl,
        tokenProvider = { authClient.currentToken() },
        onUnauthorized = { authClient.signOut() },
    )
    private val backendRepository = BackendAssessmentRepository(
        backendApi,
        caseRepository,
        IosAudioFileReader(),
        IosAudioFileDeleter(),
        assessmentStore,
    )
    private val uploadProcessor =
        UploadQueueProcessor(backendRepository, queueStore, caseRepository)
    private val scheduler =
        InProcessAssessmentScheduler({ uploadProcessor }, { authClient })
    private val assessmentRepository: AssessmentRepository = QueuedAssessmentRepository(
        backendRepository,
        assessmentStore,
        caseRepository,
        queueStore,
        scheduler,
    )

    // ViewModel factories (SwiftUI owns lifetimes; pair with clearViewModel)
    fun authViewModel() = AuthViewModel(
        ObserveAuthStateUseCase(authClient),
        SignOutUseCase(authClient),
    )

    fun loginViewModel() = LoginViewModel(
        SignInUseCase(authClient),
        SignInWithEmailUseCase(authClient),
        SignUpWithEmailUseCase(authClient),
        ResetPasswordUseCase(authClient),
    )

    fun legalConsentViewModel(userId: String) = LegalConsentViewModel(
        userId,
        GetLegalAcceptanceUseCase(legalConsentRepository),
        AcceptLegalTermsUseCase(legalConsentRepository),
    )

    fun symptomViewModel() = SymptomViewModel(
        StartRecordingUseCase(audioRecorder),
        StopRecordingUseCase(audioRecorder),
        ReleaseRecorderUseCase(audioRecorder),
        CreateCaseUseCase(caseRepository),
    )

    fun assessmentViewModel(caseId: String) = AssessmentViewModel(
        caseId,
        RequestAssessmentUseCase(assessmentRepository),
        GetFeedbackUseCase(assessmentRepository),
        SubmitFeedbackUseCase(assessmentRepository),
    )

    fun historyViewModel() = HistoryViewModel(
        ObserveCasesUseCase(caseRepository),
        PlayRecordingUseCase(audioPlayer),
        StopPlaybackUseCase(audioPlayer),
        DeleteCaseUseCase(assessmentRepository),
    )
}
