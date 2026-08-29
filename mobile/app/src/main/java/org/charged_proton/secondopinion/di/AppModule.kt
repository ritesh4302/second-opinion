package org.charged_proton.secondopinion.di

import com.google.firebase.FirebaseApp
import org.charged_proton.secondopinion.BuildConfig
import org.charged_proton.secondopinion.auth.CurrentActivityTracker
import org.charged_proton.secondopinion.auth.FirebaseAuthClient
import org.charged_proton.secondopinion.data.audio.SileroVadTrimmer
import org.charged_proton.secondopinion.data.auth.AuthTokenStore
import org.charged_proton.secondopinion.data.auth.FakeGoogleAuthClient
import org.charged_proton.secondopinion.data.auth.SharedPreferencesAuthTokenStore
import org.charged_proton.secondopinion.data.local.AssessmentStore
import org.charged_proton.secondopinion.data.local.AndroidDatabaseFactory
import org.charged_proton.secondopinion.data.local.SqlDelightAssessmentStore
import org.charged_proton.secondopinion.data.local.SqlDelightUploadQueueStore
import org.charged_proton.secondopinion.data.local.UploadQueueStore
import org.charged_proton.secondopinion.data.legal.LegalAcceptanceStore
import org.charged_proton.secondopinion.data.legal.PersistentLegalConsentRepository
import org.charged_proton.secondopinion.data.legal.SharedPreferencesLegalAcceptanceStore
import org.charged_proton.secondopinion.data.platform.AndroidAudioFileDeleter
import org.charged_proton.secondopinion.data.platform.AndroidAudioFileReader
import org.charged_proton.secondopinion.data.platform.AudioFileDeleter
import org.charged_proton.secondopinion.data.platform.AudioFileReader
import org.charged_proton.secondopinion.data.player.MediaPlayerAudioPlayer
import org.charged_proton.secondopinion.data.recorder.VadTrimmingAudioRecorder
import org.charged_proton.secondopinion.data.remote.createBackendApi
import org.charged_proton.secondopinion.data.queue.AssessmentWorkScheduler
import org.charged_proton.secondopinion.data.queue.UploadQueueProcessor
import org.charged_proton.secondopinion.data.repository.BackendAssessmentRepository
import org.charged_proton.secondopinion.data.repository.QueuedAssessmentRepository
import org.charged_proton.secondopinion.data.repository.SqlDelightCaseRepository
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.legal.LegalConsentRepository
import org.charged_proton.secondopinion.domain.platform.AudioPlayer
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository
import org.charged_proton.secondopinion.domain.usecase.AcceptLegalTermsUseCase
import org.charged_proton.secondopinion.domain.usecase.CreateCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.DeleteCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.GetAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.GetCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.GetFeedbackUseCase
import org.charged_proton.secondopinion.domain.usecase.GetLegalAcceptanceUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveAuthStateUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.domain.usecase.PlayRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.ReleaseRecorderUseCase
import org.charged_proton.secondopinion.domain.usecase.ResetPasswordUseCase
import org.charged_proton.secondopinion.domain.usecase.RequestAssessmentUseCase
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
import org.charged_proton.secondopinion.presentation.login.LoginViewModel
import org.charged_proton.secondopinion.presentation.legal.LegalConsentViewModel
import org.charged_proton.secondopinion.presentation.symptom.SymptomViewModel
import org.charged_proton.secondopinion.queue.WorkManagerAssessmentScheduler
import org.charged_proton.secondopinion.telemetry.AppTelemetry
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule(telemetry: AppTelemetry) = module {
    // Platform + persistent local data
    single { SileroVadTrimmer(androidContext().assets) }
    single<AudioRecorder> { VadTrimmingAudioRecorder(androidContext(), get()) }
    single<AudioPlayer> { MediaPlayerAudioPlayer() }
    single { AndroidDatabaseFactory(androidContext()).create() }
    single<CaseRepository> {
        val authClient = get<AuthClient>()
        SqlDelightCaseRepository(get()) {
            (authClient.authState.value as? AuthState.SignedIn)?.user?.uid
        }
    }
    single<AssessmentStore> {
        val authClient = get<AuthClient>()
        SqlDelightAssessmentStore(get()) {
            (authClient.authState.value as? AuthState.SignedIn)?.user?.uid
        }
    }
    single<UploadQueueStore> {
        val authClient = get<AuthClient>()
        SqlDelightUploadQueueStore(get()) {
            (authClient.authState.value as? AuthState.SignedIn)?.user?.uid
        }
    }
    single<AudioFileReader> { AndroidAudioFileReader() }
    single<AudioFileDeleter> { AndroidAudioFileDeleter() }
    single<AuthTokenStore> { SharedPreferencesAuthTokenStore(androidContext()) }
    single<LegalAcceptanceStore> { SharedPreferencesLegalAcceptanceStore(androidContext()) }
    single { telemetry }
    single<LegalConsentRepository> {
        PersistentLegalConsentRepository(get(), System::currentTimeMillis)
    }
    // Real Firebase Google Sign-In when the Firebase project config
    // (google-services.json) is present (FirebaseInitProvider then initialises
    // a FirebaseApp on startup); otherwise the fake client, which pairs with
    // the backend's SO_AUTH_PROVIDER=fake verifier.
    single { CurrentActivityTracker() }
    single<AuthClient> {
        if (FirebaseApp.getApps(androidContext()).isEmpty()) {
            FakeGoogleAuthClient(get())
        } else {
            FirebaseAuthClient(androidContext(), get())
        }
    }
    single {
        val authClient = get<AuthClient>()
        createBackendApi(
            BuildConfig.BACKEND_BASE_URL,
            tokenProvider = { authClient.currentToken() },
            onUnauthorized = { authClient.signOut() },
        )
    }
    single<AssessmentWorkScheduler> { WorkManagerAssessmentScheduler(androidContext()) }
    single { BackendAssessmentRepository(get(), get(), get(), get(), get()) }
    single { UploadQueueProcessor(get<BackendAssessmentRepository>(), get(), get()) }
    single<AssessmentRepository> {
        QueuedAssessmentRepository(get<BackendAssessmentRepository>(), get(), get(), get(), get())
    }

    // Use cases
    factory { ObserveAuthStateUseCase(get()) }
    factory { SignInUseCase(get()) }
    factory { SignInWithEmailUseCase(get()) }
    factory { SignUpWithEmailUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }
    factory { SignOutUseCase(get()) }
    factory { StartRecordingUseCase(get()) }
    factory { StopRecordingUseCase(get()) }
    factory { ReleaseRecorderUseCase(get()) }
    factory { PlayRecordingUseCase(get()) }
    factory { StopPlaybackUseCase(get()) }
    factory { CreateCaseUseCase(get()) }
    factory { DeleteCaseUseCase(get()) }
    factory { ObserveCasesUseCase(get()) }
    factory { GetCaseUseCase(get()) }
    factory { RequestAssessmentUseCase(get()) }
    factory { GetAssessmentUseCase(get()) }
    factory { SubmitFeedbackUseCase(get()) }
    factory { GetFeedbackUseCase(get()) }
    factory { GetLegalAcceptanceUseCase(get()) }
    factory { AcceptLegalTermsUseCase(get()) }

    // ViewModels
    viewModel { AuthViewModel(get(), get()) }
    viewModel { LoginViewModel(get(), get(), get(), get()) }
    viewModel { (userId: String) -> LegalConsentViewModel(userId, get(), get()) }
    viewModel { SymptomViewModel(get(), get(), get(), get()) }
    viewModel { (caseId: String) -> AssessmentViewModel(caseId, get(), get(), get()) }
    viewModel { HistoryViewModel(get(), get(), get(), get()) }
}
