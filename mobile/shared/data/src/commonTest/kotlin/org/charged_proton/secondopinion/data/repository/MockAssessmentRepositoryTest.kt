package org.charged_proton.secondopinion.data.repository

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.model.Recording
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockAssessmentRepositoryTest {

    private val caseRepository = InMemoryCaseRepository()
    private val repository = MockAssessmentRepository(caseRepository, stageDelayMillis = 10)

    private suspend fun newCase() = caseRepository.createCase(Recording("/tmp/rec.m4a", 1L))

    @Test
    fun requestAssessment_emitsAllStagesThenCompleted() = runTest {
        val case = newCase()

        val emitted = repository.requestAssessment(case.id).toList()

        val expectedStages = listOf(
            PipelineStage.UPLOADING,
            PipelineStage.DIARIZING,
            PipelineStage.TRANSCRIBING,
            PipelineStage.EXTRACTING,
            PipelineStage.ASSESSING,
        )
        assertEquals(
            expectedStages.map(AssessmentProgress::InProgress),
            emitted.dropLast(1),
        )
        val completed = assertIs<AssessmentProgress.Completed>(emitted.last())
        assertEquals(case.id, completed.assessment.caseId)
        assertEquals("assessment-${case.id}", completed.assessment.id)
    }

    @Test
    fun requestAssessment_updatesCaseStatusToCompleted() = runTest {
        val case = newCase()

        repository.requestAssessment(case.id).toList()

        assertEquals(CaseStatus.COMPLETED, caseRepository.getCase(case.id)?.status)
    }

    @Test
    fun requestAssessment_unknownCase_emitsFailedOnly() = runTest {
        val emitted = repository.requestAssessment("missing").toList()

        assertEquals(1, emitted.size)
        val failed = assertIs<AssessmentProgress.Failed>(emitted.single())
        assertTrue("missing" in failed.reason)
    }

    @Test
    fun requestAssessment_alreadyAssessed_replaysCachedResultWithoutPipeline() = runTest {
        val case = newCase()
        val first = repository.requestAssessment(case.id).toList()

        val second = repository.requestAssessment(case.id).toList()

        assertEquals(1, second.size)
        assertEquals(first.last(), second.single())
    }

    @Test
    fun requestAssessment_rotatesScenariosAcrossSubmissions() = runTest {
        suspend fun assess(): Assessment {
            val case = newCase()
            val completed = repository.requestAssessment(case.id).last()
            return assertIs<AssessmentProgress.Completed>(completed).assessment
        }

        val summaries = List(4) { assess().symptomSummary }

        assertEquals(3, summaries.take(3).distinct().size)
        assertEquals(summaries[0], summaries[3])
    }

    @Test
    fun getAssessment_returnsCachedAssessmentOrNull() = runTest {
        val case = newCase()
        assertNull(repository.getAssessment(case.id))

        val completed =
            assertIs<AssessmentProgress.Completed>(repository.requestAssessment(case.id).last())

        assertEquals(completed.assessment, repository.getAssessment(case.id))
    }

    @Test
    fun submitFeedback_thenGetFeedback_roundTrips() = runTest {
        val feedback = Feedback("assessment-1", PharmacistDecision.ACCEPTED, note = "ok")
        assertNull(repository.getFeedback("assessment-1"))

        val result = repository.submitFeedback(feedback)

        assertTrue(result.isSuccess)
        assertEquals(feedback, repository.getFeedback("assessment-1"))
    }
}
