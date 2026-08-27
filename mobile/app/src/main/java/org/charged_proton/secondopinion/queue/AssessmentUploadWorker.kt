package org.charged_proton.secondopinion.queue

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.charged_proton.secondopinion.data.queue.QueueProcessResult
import org.charged_proton.secondopinion.data.queue.UploadQueueProcessor
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
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

        val processor = koin.get<UploadQueueProcessor>()
        val attemptCount = runAttemptCount + 1
        return try {
            when (val result = processor.process(caseId, attemptCount)) {
                QueueProcessResult.Success -> Result.success()
                is QueueProcessResult.PermanentFailure -> Result.failure()
                is QueueProcessResult.Retry -> retryOrFail(
                    processor,
                    caseId,
                    result.reason,
                    attemptCount,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            retryOrFail(
                processor,
                caseId,
                failure.message ?: "Unexpected upload failure",
                attemptCount,
            )
        }
    }

    private suspend fun retryOrFail(
        processor: UploadQueueProcessor,
        caseId: String,
        reason: String,
        attemptCount: Int,
    ): Result = if (attemptCount >= MAX_ATTEMPTS) {
        processor.markRetriesExhausted(caseId, reason, attemptCount)
        Result.failure()
    } else {
        processor.markRetryScheduled(caseId, reason, attemptCount)
        Result.retry()
    }

    companion object {
        const val CASE_ID = "case_id"
        const val OWNER_ID = "owner_id"
        const val MAX_ATTEMPTS = 5
    }
}