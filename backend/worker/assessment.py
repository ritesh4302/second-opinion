"""Assessment stage port + providers (build-order step 5).

Turns the structured extraction + kept transcript into a preliminary triage
assessment: condition hypotheses with confidence, red flags, and medicine
guidance — OTC preferred, prescription (Schedule H/H1) medicines allowed but
labeled `prescription: true` (docs/PROJECT_DOCUMENTATION.md §2 — never a
diagnosis; the pharmacist decides). Q3 (dedicated medical LLM) stays open:
the POC uses a Sarvam chat model behind the `Assessor` port so a medical
model can be swapped in per benchmark results without touching pipeline
orchestration.
"""

import logging
from dataclasses import dataclass
from functools import lru_cache
from typing import Protocol

from pydantic import BaseModel, Field, ValidationError, field_validator

from app.settings import get_settings
from worker.nlp import parse_llm_json

logger = logging.getLogger(__name__)

# Bump whenever the system prompt changes; persisted on every assessment row
# for reproducibility and evaluation (docs/BACKEND.md §4).
PROMPT_VERSION = "assess-v3"


@dataclass(frozen=True)
class CaseSummary:
    """Assessment input: extraction fields + the kept transcript text."""

    symptoms: list[str]
    age: int | None
    gender: str | None
    location: str | None
    duration_days: int | None
    severity: str | None
    transcript: str


class AssessmentError(RuntimeError):
    pass


class ConditionHypothesis(BaseModel):
    name: str
    confidence_percent: int
    rationale: str = ""

    @field_validator("confidence_percent")
    @classmethod
    def _clamp(cls, value: int) -> int:
        return min(max(value, 0), 100)


class RedFlag(BaseModel):
    description: str
    action: str = ""


class OtcAdvice(BaseModel):
    medicine: str
    dosage: str = ""
    note: str = ""
    # Prescription (Schedule H/H1) medicines are shown, not blocked; the app
    # renders this as a "prescription drug" label next to the advice.
    prescription: bool = False


class AssessmentResult(BaseModel):
    conditions: list[ConditionHypothesis] = Field(default_factory=list)
    red_flags: list[RedFlag] = Field(default_factory=list)
    otc_guidance: list[OtcAdvice] = Field(default_factory=list)
    raw: dict = Field(default_factory=dict)


class Assessor(Protocol):
    """Port for the assessment stage; tests provide a stub."""

    model_id: str

    def assess(self, case: CaseSummary) -> AssessmentResult: ...


_ASSESSMENT_SYSTEM = (
    "You are a triage decision-support assistant for a licensed pharmacist "
    "in India. You never diagnose or prescribe: you list possible condition "
    "categories with confidence, danger signs needing a doctor, and medicine "
    "guidance. Prefer over-the-counter medicines; you may include Schedule "
    "H/H1 or prescription-only medicines when clearly warranted, but mark "
    'each of those with "prescription": true and say in the note that it '
    "requires a doctor's prescription. Label by India's drug schedules: "
    "paracetamol, ibuprofen, ORS, cetirizine, antacids and similar OTC "
    'medicines are "prescription": false; antibiotics (e.g. azithromycin, '
    "amoxicillin), corticosteroids and other Schedule H/H1 medicines are "
    '"prescription": true. If danger signs are present (e.g. chest pain, '
    "breathing difficulty, infant fever, blood in stool/vomit), list them "
    "as red flags with a clear refer-to-doctor action. Write every value "
    "in English, JSON only:\n"
    '{"conditions": [{"name": "<condition category>", '
    '"confidence_percent": <0-100>, "rationale": "<one sentence>"}], '
    '"red_flags": [{"description": "<danger sign>", "action": "<referral advice>"}], '
    '"otc_guidance": [{"medicine": "<medicine>", "dosage": "<adult-adjusted dosage>", '
    '"note": "<caution>", "prescription": <true if Schedule H/H1 or '
    "prescription-only, else false>}]}\n"
    "Use empty lists when nothing applies; never invent symptoms."
)


def _case_prompt(case: CaseSummary) -> str:
    fields = [
        f"symptoms: {', '.join(case.symptoms) or 'unknown'}",
        f"age: {case.age if case.age is not None else 'unknown'}",
        f"gender: {case.gender or 'unknown'}",
        f"affected area: {case.location or 'unknown'}",
        f"duration_days: {case.duration_days if case.duration_days is not None else 'unknown'}",
        f"severity: {case.severity or 'unknown'}",
        "",
        "Patient-relevant transcript (Hindi/Hinglish/English):",
        case.transcript,
    ]
    return "\n".join(fields)


class SarvamAssessor:
    """Sarvam chat completions; JSON prompted + Pydantic-validated (no schema mode)."""

    def __init__(self) -> None:
        from sarvamai import SarvamAI

        settings = get_settings()
        if not settings.sarvam_api_key:
            raise AssessmentError("SO_SARVAM_API_KEY is not set")
        self._client = SarvamAI(api_subscription_key=settings.sarvam_api_key)
        self.model_id = settings.sarvam_assessment_model

    def assess(self, case: CaseSummary) -> AssessmentResult:
        # Sarvam chat models are reasoning models: keep effort low and leave
        # headroom, or the token budget is spent before `content` is emitted.
        response = self._client.chat.completions(
            model=self.model_id,
            messages=[
                {"role": "system", "content": _ASSESSMENT_SYSTEM},
                {"role": "user", "content": _case_prompt(case)},
            ],
            temperature=0.2,
            max_tokens=4096,
            reasoning_effort="low",
        )
        data = parse_llm_json(response.choices[0].message.content or "")
        try:
            result = AssessmentResult.model_validate(data)
        except ValidationError as exc:
            raise AssessmentError(f"assessment reply failed validation: {exc}") from exc
        result.raw = data
        return result


class FakeAssessor:
    """Canned triage output for dev/demo runs without an API key."""

    model_id = "fake"

    def assess(self, case: CaseSummary) -> AssessmentResult:
        logger.info("fake assessor: %d symptoms in", len(case.symptoms))
        return AssessmentResult(
            conditions=[
                ConditionHypothesis(
                    name="Viral fever",
                    confidence_percent=70,
                    rationale="Fever with headache and body ache of short duration.",
                )
            ],
            red_flags=[],
            otc_guidance=[
                OtcAdvice(
                    medicine="Paracetamol 500 mg",
                    dosage="1 tablet every 6 hours after food",
                    note="Refer to a doctor if fever persists beyond 3 days.",
                ),
                OtcAdvice(
                    medicine="Azithromycin 500 mg",
                    dosage="1 tablet daily for 3 days",
                    note="Schedule H — requires a doctor's prescription.",
                    prescription=True,
                ),
            ],
        )


@lru_cache
def get_assessor() -> Assessor:
    provider = get_settings().assessment_provider
    if provider == "fake":
        return FakeAssessor()
    if provider == "sarvam":
        return SarvamAssessor()
    raise AssessmentError(f"unknown assessment provider: {provider}")
