package org.charged_proton.secondopinion.presentation.ios

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.data.queue.AssessmentWorkScheduler
import org.charged_proton.secondopinion.data.queue.QueueProcessResult
import org.charged_proton.secondopinion.data.queue.UploadQueueProcessor
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState

/**
 * Foreground upload scheduler for iOS: runs the shared [UploadQueueProcessor]
 * immediately in-process with the same five bounded exponential-backoff
 * attempts as Android's AssessmentUploadWorker. Unlike WorkManager this does
 * not survive app termination — the BGTaskScheduler + background URLSession
 * shim is a follow-up (TODO.md §2); until then the durable queue re-drives
 * unfinished work on next launch via [resume].
 */
class InProcessAssessmentScheduler(
    private val processor: () -> UploadQueueProcessor,
    private val authClient: () -> AuthClient,
) : AssessmentWorkScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = mutableMapOf<String, Job>()

    override fun enqueue(caseId: String, ownerId: String) {
        if (jobs[caseId]?.isActive == true) return
        jobs[caseId] = scope.launch { run(caseId, ownerId) }
    }

    override fun cancel(caseId: String, ownerId: String) {
        jobs.remove(caseId)?.cancel()
    }

    private suspend fun run(caseId: String, ownerId: String) {
        val processor = processor()
        for (attempt in 1..MAX_ATTEMPTS) {
            val currentOwner =
                (authClient().authState.value as? AuthState.SignedIn)?.user?.uid
            if (currentOwner != ownerId) return
            val result = try {
                processor.process(caseId, attempt)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                QueueProcessResult.Retry(failure.message ?: "Unexpected upload failure")
            }
            when (result) {
                QueueProcessResult.Success -> return
                is QueueProcessResult.PermanentFailure -> return
                is QueueProcessResult.Retry -> {
                    if (attempt >= MAX_ATTEMPTS) {
                        processor.markRetriesExhausted(caseId, result.reason, attempt)
                        return
                    }
                    processor.markRetryScheduled(caseId, result.reason, attempt)
                    delay(BACKOFF_BASE_MS shl (attempt - 1))
                }
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val BACKOFF_BASE_MS = 10_000L
    }
}
