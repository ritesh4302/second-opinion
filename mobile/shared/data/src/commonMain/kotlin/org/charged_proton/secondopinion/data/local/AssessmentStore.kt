package org.charged_proton.secondopinion.data.local

import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.Feedback

/** Local owner-scoped cache for completed assessments and pharmacist decisions. */
interface AssessmentStore {
    suspend fun saveAssessment(assessment: Assessment)
    suspend fun getAssessment(caseId: String): Assessment?
    suspend fun saveFeedback(feedback: Feedback)
    suspend fun getFeedback(assessmentId: String): Feedback?
    suspend fun deleteCase(caseId: String)
}