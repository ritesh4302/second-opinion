package org.charged_proton.secondopinion.queue

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.charged_proton.secondopinion.data.queue.QueueProcessResult
import org.charged_proton.secondopinion.data.queue.UploadQueueProcessor
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.legal.CURRENT_LEGAL_VERSION
import org.charged_proton.secondopinion.domain.legal.LegalConsentRepository
import org.charged_proton.secondopinion.telemetry.AppTelemetry
import org.charged_proton.secondopinion.telemetry.TelemetryEvent
import org.charged_proton.secondopinion.telemetry.TelemetryOperation
import org.koin.core.context.GlobalContext

class AssessmentUploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val caseId = inputData.getString(CASE_ID) ?: return Result.failure()
        val ownerId = inputData.getString(OWNER_ID) ?: return Result.failure()
        val koin = GlobalContext.get()
        val authClient = koin.get<AuthClient>()
        val currentOwner = (authClient.authState.value as? AuthState.SignedIn)?.user?.uid
        if (currentOwner != ownerId) return Result.failure()

        val telemetry = koin.get<AppTelemetry>()
        val acceptance = koin.get<LegalConsentRepository>().getAcceptance(ownerId)
        telemetry.setCollectionEnabled(acceptance?.version == CURRENT_LEGAL_VERSION)
        val processor = koin.get<UploadQueueProcessor>()
        val attemptCount = runAttemptCount + 1
        return try {
            when (val result = processor.process(caseId, attemptCount)) {
                QueueProcessResult.Success -> {
                    telemetry.event(TelemetryEvent.UPLOAD_SUCCEEDED)
                    Result.success()
                }
                is QueueProcessResult.PermanentFailure -> {
                    telemetry.event(TelemetryEvent.UPLOAD_FAILED)
                    Result.failure()
                }
                is QueueProcessResult.Retry -> retryOrFail(
                    processor,
                    caseId,
                    result.reason,
                    attemptCount,
                    telemetry,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            telemetry.recordNonFatal(TelemetryOperation.ASSESSMENT_UPLOAD, failure)
            retryOrFail(
                processor,
                caseId,
                failure.message ?: "Unexpected upload failure",
                attemptCount,
                telemetry,
            )
        }
    }

    private suspend fun retryOrFail(
        processor: UploadQueueProcessor,
        caseId: String,
        reason: String,
        attemptCount: Int,
        telemetry: AppTelemetry,
    ): Result = if (attemptCount >= MAX_ATTEMPTS) {
        processor.markRetriesExhausted(caseId, reason, attemptCount)
        telemetry.event(TelemetryEvent.UPLOAD_FAILED)
        Result.failure()
    } else {
        processor.markRetryScheduled(caseId, reason, attemptCount)
        telemetry.event(TelemetryEvent.UPLOAD_RETRY_SCHEDULED)
        Result.retry()
    }

    companion object {
        const val CASE_ID = "case_id"
        const val OWNER_ID = "owner_id"
        const val MAX_ATTEMPTS = 5
    }
}