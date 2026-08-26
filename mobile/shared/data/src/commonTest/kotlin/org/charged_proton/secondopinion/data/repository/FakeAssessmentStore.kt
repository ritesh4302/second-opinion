package org.charged_proton.secondopinion.data.repository

import org.charged_proton.secondopinion.data.local.AssessmentStore
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.Feedback

internal class FakeAssessmentStore : AssessmentStore {
    private val assessments = mutableMapOf<String, Assessment>()
    private val feedback = mutableMapOf<String, Feedback>()

    override suspend fun saveAssessment(assessment: Assessment) {
        assessments[assessment.caseId] = assessment
    }

    override suspend fun getAssessment(caseId: String): Assessment? = assessments[caseId]

    override suspend fun saveFeedback(feedback: Feedback) {
        this.feedback[feedback.assessmentId] = feedback
    }

    override suspend fun getFeedback(assessmentId: String): Feedback? = feedback[assessmentId]

    override suspend fun deleteCase(caseId: String) {
        assessments.remove(caseId)?.let { feedback.remove(it.id) }
    }
}