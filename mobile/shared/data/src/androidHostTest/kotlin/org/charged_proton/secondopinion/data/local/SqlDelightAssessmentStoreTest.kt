package org.charged_proton.secondopinion.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.data.local.db.SecondOpinionDatabase
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.ConditionHypothesis
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.OtcAdvice
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.model.RedFlag

class SqlDelightAssessmentStoreTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: SecondOpinionDatabase
    private var ownerId: String? = "owner-1"

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SecondOpinionDatabase.Schema.create(driver)
        database = SecondOpinionDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun assessmentAndFeedbackSurviveStoreRecreation() = runTest {
        val assessment = assessment()
        val feedback = Feedback(assessment.id, PharmacistDecision.OVERRIDDEN, "Gave ORS")
        store().saveAssessment(assessment)
        store().saveFeedback(feedback)

        val recreated = store()

        assertEquals(assessment, recreated.getAssessment(assessment.caseId))
        assertEquals(feedback, recreated.getFeedback(assessment.id))
    }

    @Test
    fun cachedDataIsScopedToCurrentOwner() = runTest {
        val firstOwnerAssessment = assessment()
        store().saveAssessment(firstOwnerAssessment)

        ownerId = "owner-2"

        assertNull(store().getAssessment(firstOwnerAssessment.caseId))
        assertNull(store().getFeedback(firstOwnerAssessment.id))
    }

    @Test
    fun deleteCaseRemovesItsAssessmentAndFeedback() = runTest {
        val assessment = assessment()
        val feedback = Feedback(assessment.id, PharmacistDecision.ACCEPTED)
        val store = store()
        store.saveAssessment(assessment)
        store.saveFeedback(feedback)

        store.deleteCase(assessment.caseId)

        assertNull(store.getAssessment(assessment.caseId))
        assertNull(store.getFeedback(assessment.id))
    }

    private fun store() = SqlDelightAssessmentStore(database) { ownerId }

    private fun assessment() = Assessment(
        id = "assessment-1",
        caseId = "case-1",
        symptomSummary = "Fever and headache",
        conditions = listOf(ConditionHypothesis("Viral fever", 70, "Typical symptoms")),
        redFlags = listOf(RedFlag("Stiff neck", "Refer immediately")),
        otcGuidance = listOf(OtcAdvice("Paracetamol", "500 mg", "After food", false)),
        disclaimer = "Pharmacist remains the decision-maker.",
    )
}