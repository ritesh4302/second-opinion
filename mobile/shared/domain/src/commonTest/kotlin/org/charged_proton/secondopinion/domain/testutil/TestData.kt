package org.charged_proton.secondopinion.domain.testutil

import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.ConditionHypothesis
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.SymptomCase

fun testRecording(
    filePath: String = "/tmp/rec.m4a",
    createdAtEpochMillis: Long = 1_000L,
) = Recording(filePath, createdAtEpochMillis)

fun testCase(
    id: String = "case-1",
    recording: Recording = testRecording(),
    status: CaseStatus = CaseStatus.RECORDED,
    createdAtEpochMillis: Long = recording.createdAtEpochMillis,
) = SymptomCase(id, recording, status, createdAtEpochMillis)

fun testAssessment(
    id: String = "assessment-1",
    caseId: String = "case-1",
) = Assessment(
    id = id,
    caseId = caseId,
    symptomSummary = "summary",
    conditions = listOf(ConditionHypothesis("cold", 70, "classic cluster")),
    redFlags = emptyList(),
    otcGuidance = emptyList(),
    disclaimer = "triage only",
)
