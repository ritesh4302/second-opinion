"""NLP stage port + providers.

Two focused LLM calls per recording — relevance weighting (which speaker is
the patient, which segments matter) and structured extraction — matching the
`filtering` / `extracting` states (docs/BACKEND.md §2.2). The provider sits
behind `NlpModel` so the vendor (sarvam-30b for the POC) can be swapped per
benchmark results without touching pipeline orchestration.
"""

import json
import logging
from dataclasses import dataclass
from functools import lru_cache
from typing import Protocol

from pydantic import BaseModel, Field, ValidationError, field_validator

from app.settings import get_settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class TranscriptLine:
    index: int
    speaker_label: str
    text: str


class NlpError(RuntimeError):
    pass


class SegmentRelevance(BaseModel):
    index: int
    relevance: float

    @field_validator("relevance")
    @classmethod
    def _clamp(cls, value: float) -> float:
        return min(max(value, 0.0), 1.0)


class RelevanceResult(BaseModel):
    patient_speaker: str | None = None
    segments: list[SegmentRelevance]
    raw: dict = Field(default_factory=dict)

    @field_validator("patient_speaker", mode="before")
    @classmethod
    def _stringify(cls, value: object) -> str | None:
        return None if value is None else str(value)


class ExtractionResult(BaseModel):
    symptoms: list[str] = Field(default_factory=list)
    age: int | None = None
    gender: str | None = None
    location: str | None = None
    duration_days: int | None = None
    severity: str | None = None
    raw: dict = Field(default_factory=dict)


class NlpModel(Protocol):
    """Port for the NLP stage; tests provide a stub."""

    def weigh_relevance(self, lines: list[TranscriptLine]) -> RelevanceResult: ...

    def extract(self, text: str) -> ExtractionResult: ...


def parse_llm_json(text: str) -> dict:
    """Extract the first JSON object from an LLM reply (tolerates fences/prose)."""
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end <= start:
        raise NlpError("LLM reply contains no JSON object")
    try:
        return json.loads(text[start : end + 1])
    except json.JSONDecodeError as exc:
        raise NlpError(f"LLM reply is not valid JSON: {exc}") from exc


_RELEVANCE_SYSTEM = (
    "You analyse a diarized pharmacy-counter conversation from India "
    "(Hindi / Hinglish / English). Decide which speaker is the patient — the "
    "person the medicines are for, or whoever describes the symptoms — and "
    "rate each segment's relevance to the medical complaint: first-person "
    "symptom descriptions high, pharmacist questions about the complaint "
    "medium, greetings/prices/unrelated chatter low. Reply with JSON only:\n"
    '{"patient_speaker": "<label exactly as given, e.g. \\"0\\", or null>", '
    '"segments": [{"index": <int>, "relevance": <0.0-1.0>}]}\n'
    "Include every input index exactly once."
)

_EXTRACTION_SYSTEM = (
    "You extract structured intake data from the relevant parts of a "
    "pharmacy-counter transcript (Hindi / Hinglish / English). Reply with "
    "JSON only:\n"
    '{"symptoms": ["<short English phrases>"], "age": <int or null>, '
    '"gender": "male" | "female" | "other" | null, '
    '"location": "<affected body part/area>" | null, '
    '"duration_days": <int or null>, '
    '"severity": "mild" | "moderate" | "severe" | null}\n'
    "Use null for anything not stated in the transcript; never guess."
)


class SarvamNlp:
    """Sarvam chat completions; JSON prompted + Pydantic-validated (no schema mode)."""

    def __init__(self) -> None:
        from sarvamai import SarvamAI

        settings = get_settings()
        if not settings.sarvam_api_key:
            raise NlpError("SO_SARVAM_API_KEY is not set")
        self._client = SarvamAI(api_subscription_key=settings.sarvam_api_key)
        self._model = settings.sarvam_chat_model

    def _complete(self, system: str, user: str) -> dict:
        # Sarvam chat models are reasoning models: keep effort low and leave
        # headroom, or the token budget is spent before `content` is emitted.
        response = self._client.chat.completions(
            model=self._model,
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            temperature=0.2,
            max_tokens=4096,
            reasoning_effort="low",
        )
        return parse_llm_json(response.choices[0].message.content or "")

    def weigh_relevance(self, lines: list[TranscriptLine]) -> RelevanceResult:
        user = "\n".join(
            f"{line.index} | speaker {line.speaker_label} | {line.text}" for line in lines
        )
        data = self._complete(_RELEVANCE_SYSTEM, user)
        try:
            result = RelevanceResult.model_validate(data)
        except ValidationError as exc:
            raise NlpError(f"relevance reply failed validation: {exc}") from exc
        result.raw = data
        return result

    def extract(self, text: str) -> ExtractionResult:
        data = self._complete(_EXTRACTION_SYSTEM, text)
        try:
            result = ExtractionResult.model_validate(data)
        except ValidationError as exc:
            raise NlpError(f"extraction reply failed validation: {exc}") from exc
        result.raw = data
        return result


class FakeNlp:
    """Deterministic weights/extraction for dev/demo runs without an API key."""

    def weigh_relevance(self, lines: list[TranscriptLine]) -> RelevanceResult:
        logger.info("fake nlp: weighing %d segments", len(lines))
        return RelevanceResult(
            patient_speaker="0",
            segments=[
                SegmentRelevance(
                    index=line.index,
                    relevance=0.9 if line.speaker_label == "0" else 0.3,
                )
                for line in lines
            ],
        )

    def extract(self, text: str) -> ExtractionResult:
        logger.info("fake nlp: returning canned extraction (%d chars)", len(text))
        return ExtractionResult(symptoms=["fever", "headache"], duration_days=2, severity="mild")


@lru_cache
def get_nlp_model() -> NlpModel:
    provider = get_settings().nlp_provider
    if provider == "fake":
        return FakeNlp()
    if provider == "sarvam":
        return SarvamNlp()
    raise NlpError(f"unknown NLP provider: {provider}")
