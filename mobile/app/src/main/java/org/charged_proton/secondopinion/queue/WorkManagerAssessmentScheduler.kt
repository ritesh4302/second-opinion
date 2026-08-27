package org.charged_proton.secondopinion.queue

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit
import org.charged_proton.secondopinion.data.queue.AssessmentWorkScheduler

class WorkManagerAssessmentScheduler(context: Context) : AssessmentWorkScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueue(caseId: String, ownerId: String) {
        val request = OneTimeWorkRequestBuilder<AssessmentUploadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(AssessmentUploadWorker.CASE_ID, caseId)
                    .putString(AssessmentUploadWorker.OWNER_ID, ownerId)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()
        workManager.enqueueUniqueWork(workName(caseId, ownerId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(caseId: String, ownerId: String) {
        workManager.cancelUniqueWork(workName(caseId, ownerId))
    }

    private fun workName(caseId: String, ownerId: String) = "assessment-upload-$ownerId-$caseId"
}