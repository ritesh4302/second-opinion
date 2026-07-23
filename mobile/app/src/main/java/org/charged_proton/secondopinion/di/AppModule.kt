package org.charged_proton.secondopinion.di

import org.charged_proton.secondopinion.BuildConfig
import org.charged_proton.secondopinion.data.audio.SileroVadTrimmer
import org.charged_proton.secondopinion.data.auth.AuthTokenStore
import org.charged_proton.secondopinion.data.auth.FakeOtpAuthClient
import org.charged_proton.secondopinion.data.auth.SharedPreferencesAuthTokenStore
import org.charged_proton.secondopinion.data.platform.AndroidAudioFileDeleter
import org.charged_proton.secondopinion.data.platform.AndroidAudioFileReader
import org.charged_proton.secondopinion.data.platform.AudioFileDeleter
import org.charged_proton.secondopinion.data.platform.AudioFileReader
import org.charged_proton.secondopinion.data.player.MediaPlayerAudioPlayer
import org.charged_proton.secondopinion.data.recorder.VadTrimmingAudioRecorder
import org.charged_proton.secondopinion.data.remote.createBackendApi
import org.charged_proton.secondopinion.data.repository.BackendAssessmentRepository
import org.charged_proton.secondopinion.data.repository.InMemoryCaseRepository
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.platform.AudioPlayer
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository
import org.charged_proton.secondopinion.domain.usecase.CreateCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.DeleteCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.GetAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.GetCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.GetFeedbackUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveAuthStateUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.domain.usecase.PlayRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.ReleaseRecorderUseCase
import org.charged_proton.secondopinion.domain.usecase.RequestAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.RequestOtpUseCase
import org.charged_proton.secondopinion.domain.usecase.StartRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopPlaybackUseCase
import org.charged_proton.secondopinion.domain.usecase.StopRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.SubmitFeedbackUseCase
import org.charged_proton.secondopinion.domain.usecase.VerifyOtpUseCase
import org.charged_proton.secondopinion.presentation.assessment.AssessmentViewModel
import org.charged_proton.secondopinion.presentation.auth.AuthViewModel
import org.charged_proton.secondopinion.presentation.history.HistoryViewModel
import org.charged_proton.secondopinion.presentation.login.LoginViewModel
import org.charged_proton.secondopinion.presentation.symptom.SymptomViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Platform + data (in-memory case store until SQLDelight lands)
    single { SileroVadTrimmer(androidContext().assets) }
    single<AudioRecorder> { VadTrimmingAudioRecorder(androidContext(), get()) }
    single<AudioPlayer> { MediaPlayerAudioPlayer() }
    single<CaseRepository> { InMemoryCaseRepository() }
    single<AudioFileReader> { AndroidAudioFileReader() }
    single<AudioFileDeleter> { AndroidAudioFileDeleter() }
    // Fake OTP client until the Firebase project (google-services.json) lands;
    // it pairs with the backend's SO_AUTH_PROVIDER=fake verifier.
    single<AuthTokenStore> { SharedPreferencesAuthTokenStore(androidContext()) }
    single<AuthClient> { FakeOtpAuthClient(get()) }
    single {
        val authClient = get<AuthClient>()
        createBackendApi(
            BuildConfig.BACKEND_BASE_URL,
            tokenProvider = { authClient.currentToken() },
            onUnauthorized = { authClient.signOut() },
        )
    }
    single<AssessmentRepository> { BackendAssessmentRepository(get(), get(), get(), get()) }

    // Use cases
    factory { ObserveAuthStateUseCase(get()) }
    factory { RequestOtpUseCase(get()) }
    factory { VerifyOtpUseCase(get()) }
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

    // ViewModels
    viewModel { AuthViewModel(get()) }
    viewModel { LoginViewModel(get(), get()) }
    viewModel { SymptomViewModel(get(), get(), get(), get()) }
    viewModel { (caseId: String) -> AssessmentViewModel(caseId, get(), get(), get()) }
    viewModel { HistoryViewModel(get(), get(), get(), get()) }
}
