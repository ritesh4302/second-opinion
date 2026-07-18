package org.charged_proton.secondopinion.domain.repository

import kotlinx.coroutines.flow.Flow
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.Feedback

/** Port for the upload → pipeline → assessment round trip and the feedback loop. */
interface AssessmentRepository {

    /**
     * Uploads the case's recording and drives it through the pipeline,
     * emitting progress until [AssessmentProgress.Completed] or
     * [AssessmentProgress.Failed].
     */
    fun requestAssessment(caseId: String): Flow<AssessmentProgress>

    /** Completed assessment for a case, or null if not (yet) available. */
    suspend fun getAssessment(caseId: String): Assessment?

    suspend fun submitFeedback(feedback: Feedback): Result<Unit>

    /** Previously submitted decision for an assessment, or null. */
    suspend fun getFeedback(assessmentId: String): Feedback?
}
