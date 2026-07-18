package org.charged_proton.secondopinion.di

import org.charged_proton.secondopinion.data.audio.SileroVadTrimmer
import org.charged_proton.secondopinion.data.recorder.VadTrimmingAudioRecorder
import org.charged_proton.secondopinion.data.repository.InMemoryCaseRepository
import org.charged_proton.secondopinion.data.repository.MockAssessmentRepository
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository
import org.charged_proton.secondopinion.domain.usecase.CreateCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.GetAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.GetCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.GetFeedbackUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.domain.usecase.ReleaseRecorderUseCase
import org.charged_proton.secondopinion.domain.usecase.RequestAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.StartRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.SubmitFeedbackUseCase
import org.charged_proton.secondopinion.presentation.assessment.AssessmentViewModel
import org.charged_proton.secondopinion.presentation.history.HistoryViewModel
import org.charged_proton.secondopinion.presentation.symptom.SymptomViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Platform + data (mock implementations for now)
    single { SileroVadTrimmer(androidContext().assets) }
    single<AudioRecorder> { VadTrimmingAudioRecorder(androidContext(), get()) }
    single<CaseRepository> { InMemoryCaseRepository() }
    single<AssessmentRepository> { MockAssessmentRepository(get()) }

    // Use cases
    factory { StartRecordingUseCase(get()) }
    factory { StopRecordingUseCase(get()) }
    factory { ReleaseRecorderUseCase(get()) }
    factory { CreateCaseUseCase(get()) }
    factory { ObserveCasesUseCase(get()) }
    factory { GetCaseUseCase(get()) }
    factory { RequestAssessmentUseCase(get()) }
    factory { GetAssessmentUseCase(get()) }
    factory { SubmitFeedbackUseCase(get()) }
    factory { GetFeedbackUseCase(get()) }

    // ViewModels
    viewModel { SymptomViewModel(get(), get(), get(), get()) }
    viewModel { (caseId: String) -> AssessmentViewModel(caseId, get(), get(), get()) }
    viewModel { HistoryViewModel(get()) }
}
