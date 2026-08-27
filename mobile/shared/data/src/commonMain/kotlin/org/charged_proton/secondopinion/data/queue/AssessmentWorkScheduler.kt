package org.charged_proton.secondopinion.data.queue

interface AssessmentWorkScheduler {
    fun enqueue(caseId: String, ownerId: String)
    fun cancel(caseId: String, ownerId: String)
}