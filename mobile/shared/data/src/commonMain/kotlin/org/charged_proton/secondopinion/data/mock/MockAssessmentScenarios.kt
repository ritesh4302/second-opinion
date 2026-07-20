package org.charged_proton.secondopinion.data.mock

import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.ConditionHypothesis
import org.charged_proton.secondopinion.domain.model.OtcAdvice
import org.charged_proton.secondopinion.domain.model.RedFlag

/**
 * Canned assessment scenarios the mock backend rotates through, so testers see
 * the normal, the mixed (incl. a prescription-labeled medicine), and the
 * red-flag/referral paths without a real pipeline.
 */
internal object MockAssessmentScenarios {

    private const val DISCLAIMER =
        "Preliminary triage support only — not a diagnosis or prescription. " +
            "The pharmacist remains the final decision-maker. Refer to a doctor when in doubt."

    val scenarios: List<(id: String, caseId: String) -> Assessment> = listOf(
        ::viralUri,
        ::gastroenteritis,
        ::chestPainRedFlag,
    )

    private fun viralUri(id: String, caseId: String) = Assessment(
        id = id,
        caseId = caseId,
        symptomSummary = "Adult male, ~3 days of runny nose, sneezing, mild sore throat and " +
            "low-grade bukhaar (fever). No breathing difficulty reported.",
        conditions = listOf(
            ConditionHypothesis(
                name = "Viral upper respiratory infection (common cold)",
                confidencePercent = 72,
                rationale = "Classic cluster: coryza, sneezing, mild fever, short duration.",
            ),
            ConditionHypothesis(
                name = "Allergic rhinitis",
                confidencePercent = 18,
                rationale = "Sneezing prominent, but fever makes allergy less likely.",
            ),
        ),
        redFlags = emptyList(),
        otcGuidance = listOf(
            OtcAdvice(
                medicine = "Paracetamol 500 mg",
                dosage = "1 tablet every 6–8 h after food, max 3 g/day",
                note = "For fever and body ache.",
            ),
            OtcAdvice(
                medicine = "Saline nasal drops",
                dosage = "2 drops per nostril, 3–4 times a day",
                note = "Relieves nasal congestion; safe with other medicines.",
            ),
        ),
        disclaimer = DISCLAIMER,
    )

    private fun gastroenteritis(id: String, caseId: String) = Assessment(
        id = id,
        caseId = caseId,
        symptomSummary = "Young woman, ~2 days of loose motions (4–5/day), stomach cramps and " +
            "weakness after eating outside food. No blood in stool reported.",
        conditions = listOf(
            ConditionHypothesis(
                name = "Acute gastroenteritis (likely food-borne)",
                confidencePercent = 64,
                rationale = "Onset after outside food; diarrhoea with cramps, no dysentery signs.",
            ),
            ConditionHypothesis(
                name = "Early dehydration",
                confidencePercent = 33,
                rationale = "Weakness reported; fluid loss over 2 days.",
            ),
        ),
        redFlags = listOf(
            RedFlag(
                description = "Blood in stool, high fever, or signs of severe dehydration " +
                    "(very little urine, dizziness on standing)",
                action = "If any of these appear, refer to a doctor the same day.",
            ),
        ),
        otcGuidance = listOf(
            OtcAdvice(
                medicine = "ORS (oral rehydration solution)",
                dosage = "1 glass after every loose motion",
                note = "First-line; continue normal light diet.",
            ),
            OtcAdvice(
                medicine = "Zinc 20 mg",
                dosage = "Once daily for 10–14 days",
                note = "Reduces duration and severity of diarrhoea.",
            ),
            OtcAdvice(
                medicine = "Ondansetron 4 mg",
                dosage = "1 tablet up to twice a day if vomiting",
                note = "Schedule H — requires a doctor's prescription; pharmacist to decide.",
                prescription = true,
            ),
        ),
        disclaimer = DISCLAIMER,
    )

    private fun chestPainRedFlag(id: String, caseId: String) = Assessment(
        id = id,
        caseId = caseId,
        symptomSummary = "Man in his 50s, chest heaviness (seene mein bhaaripan) since morning, " +
            "spreading to left arm, sweating, breathlessness on walking.",
        conditions = listOf(
            ConditionHypothesis(
                name = "Possible cardiac event (acute coronary syndrome)",
                confidencePercent = 58,
                rationale = "Chest heaviness radiating to arm + sweating + exertional " +
                    "breathlessness is a danger pattern until proven otherwise.",
            ),
            ConditionHypothesis(
                name = "Severe acidity / gastro-oesophageal reflux",
                confidencePercent = 22,
                rationale = "Can mimic cardiac pain, but must not be assumed first.",
            ),
        ),
        redFlags = listOf(
            RedFlag(
                description = "Chest pain radiating to arm with sweating and breathlessness",
                action = "URGENT: send to the nearest hospital/emergency immediately. " +
                    "Do NOT manage at the pharmacy.",
            ),
        ),
        otcGuidance = emptyList(),
        disclaimer = DISCLAIMER,
    )
}
