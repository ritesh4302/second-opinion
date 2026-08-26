package org.charged_proton.secondopinion.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.charged_proton.secondopinion.data.local.db.SecondOpinionDatabase
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PharmacistDecision

/** SQLDelight-backed assessment cache isolated by the current Firebase user. */
class SqlDelightAssessmentStore private constructor(
    private val database: SecondOpinionDatabase,
    private val currentOwnerId: () -> String?,
    private val json: Json,
) : AssessmentStore {

    constructor(
        database: SecondOpinionDatabase,
        currentOwnerId: () -> String?,
    ) : this(database, currentOwnerId, Json { ignoreUnknownKeys = true })

    override suspend fun saveAssessment(assessment: Assessment) {
        val ownerId = requireOwnerId()
        val payload = json.encodeToString(CachedAssessment.serializer(), assessment.toCache())
        withContext(Dispatchers.Default) {
            database.assessmentQueries.upsertAssessment(
                case_id = assessment.caseId,
                owner_id = ownerId,
                assessment_id = assessment.id,
                payload_json = payload,
            )
        }
    }

    override suspend fun getAssessment(caseId: String): Assessment? {
        val ownerId = currentOwnerId() ?: return null
        return withContext(Dispatchers.Default) {
            database.assessmentQueries.selectAssessment(caseId, ownerId)
                .executeAsOneOrNull()
                ?.let { json.decodeFromString(CachedAssessment.serializer(), it).toDomain() }
        }
    }

    override suspend fun saveFeedback(feedback: Feedback) {
        val ownerId = requireOwnerId()
        withContext(Dispatchers.Default) {
            database.assessmentQueries.upsertFeedback(
                assessment_id = feedback.assessmentId,
                owner_id = ownerId,
                decision = feedback.decision.name,
                note = feedback.note,
            )
        }
    }

    override suspend fun getFeedback(assessmentId: String): Feedback? {
        val ownerId = currentOwnerId() ?: return null
        return withContext(Dispatchers.Default) {
            database.assessmentQueries.selectFeedback(assessmentId, ownerId) { id, decision, note ->
                Feedback(id, PharmacistDecision.valueOf(decision), note)
            }.executeAsOneOrNull()
        }
    }

    override suspend fun deleteCase(caseId: String) {
        val ownerId = currentOwnerId() ?: return
        withContext(Dispatchers.Default) {
            database.assessmentQueries.transaction {
                database.assessmentQueries.deleteFeedbackByCase(ownerId, caseId, ownerId)
                database.assessmentQueries.deleteAssessmentByCase(caseId, ownerId)
            }
        }
    }

    private fun requireOwnerId(): String =
        checkNotNull(currentOwnerId()) { "A signed-in owner is required to cache assessment data" }
}