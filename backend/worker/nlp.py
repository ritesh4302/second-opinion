"""NLP stage port + providers.

One combined LLM call per recording — relevance weighting (which speaker is
the patient, which segments matter) and structured extraction in a single
pass — covering the `filtering` / `extracting` states (docs/BACKEND.md §2.2).
Merged to halve the stage's LLM round-trips (each call on a reasoning model
pays a full thinking pass). The provider sits behind `NlpModel` so the vendor
(sarvam-105b for the POC) can be swapped per benchmark results without
touching pipeline orchestration.
"""

import json
import logging
from dataclasses import dataclass
from functools import lru_cache
from typing import Protocol

from pydantic import BaseModel, Field, ValidationError, field_validator

from app.settings import get_settings
from worker.errors import ProviderConfigurationError

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


class NlpAnalysis(BaseModel):
    """Relevance weights + structured extraction from one combined LLM call."""

    patient_speaker: str | None = None
    segments: list[SegmentRelevance]
    symptoms: list[str] = Field(default_factory=list)
    age: int | None = None
    gender: str | None = None
    location: str | None = None
    duration_days: int | None = None
    severity: str | None = None
    raw: dict = Field(default_factory=dict)

    @field_validator("patient_speaker", mode="before")
    @classmethod
    def _stringify(cls, value: object) -> str | None:
        return None if value is None else str(value)


class NlpModel(Protocol):
    """Port for the NLP stage; tests provide a stub."""

    def analyze(self, lines: list[TranscriptLine]) -> NlpAnalysis: ...


def parse_llm_json(text: str) -> dict:
    """Extract the first JSON object from an LLM reply (tolerates fences/prose)."""
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end <= start:
        raise NlpError("LLM reply contains no JSON object")
    try:
        return json.loads(text[start : end + 1])
    except json.JSONDecodeError as exc:
        raise NlpError(f"LLM reply is not valid JSON: {exc}") from exc


_ANALYSIS_SYSTEM = (
    "You analyse a diarized pharmacy-counter conversation from India "
    "(Hindi / Hinglish / English). Do both of these in one pass:\n"
    "1. Decide which speaker is the patient — the person the medicines are "
    "for, or whoever describes the symptoms — and rate each segment's "
    "relevance to the medical complaint: first-person symptom descriptions "
    "high, pharmacist questions about the complaint medium, greetings/"
    "prices/unrelated chatter low.\n"
    "2. Extract structured intake data from the medically relevant "
    "segments.\n"
    "Reply with JSON only:\n"
    '{"patient_speaker": "<label exactly as given, e.g. \\"0\\", or null>", '
    '"segments": [{"index": <int>, "relevance": <0.0-1.0>}], '
    '"symptoms": ["<short English phrases>"], "age": <int or null>, '
    '"gender": "male" | "female" | "other" | null, '
    '"location": "<affected body part/area>" | null, '
    '"duration_days": <int or null>, '
    '"severity": "mild" | "moderate" | "severe" | null}\n'
    "Include every input index exactly once. Use null for anything not "
    "stated in the transcript; never guess."
)


class SarvamNlp:
    """Sarvam chat completions; JSON prompted + Pydantic-validated (no schema mode)."""

    def __init__(self) -> None:
        from sarvamai import SarvamAI

        settings = get_settings()
        if not settings.sarvam_api_key:
            raise ProviderConfigurationError("SO_SARVAM_API_KEY is not set")
        self._client = SarvamAI(api_subscription_key=settings.sarvam_api_key)
        self._model = settings.sarvam_chat_model
        self._max_tokens = settings.sarvam_max_tokens

    def _complete(self, system: str, user: str) -> dict:
        # Sarvam chat models can spend hidden reasoning tokens that count
        # against max_tokens: keep effort low and leave generous headroom
        # (a truncated reply is unparseable JSON). sarvam-105b needs >6k
        # tokens on realistic transcripts; sarvam-105b-conversations caps
        # max_tokens at 8192.
        response = self._client.chat.completions(
            model=self._model,
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            temperature=0.2,
            max_tokens=self._max_tokens,
            reasoning_effort="low",
        )
        return parse_llm_json(response.choices[0].message.content or "")

    def analyze(self, lines: list[TranscriptLine]) -> NlpAnalysis:
        user = "\n".join(
            f"{line.index} | speaker {line.speaker_label} | {line.text}" for line in lines
        )
        data = self._complete(_ANALYSIS_SYSTEM, user)
        try:
            result = NlpAnalysis.model_validate(data)
        except ValidationError as exc:
            raise NlpError(f"analysis reply failed validation: {exc}") from exc
        result.raw = data
        return result


class FakeNlp:
    """Deterministic weights/extraction for dev/demo runs without an API key."""

    def analyze(self, lines: list[TranscriptLine]) -> NlpAnalysis:
        logger.info("fake nlp: analyzing %d segments", len(lines))
        return NlpAnalysis(
            patient_speaker="0",
            segments=[
                SegmentRelevance(
                    index=line.index,
                    relevance=0.9 if line.speaker_label == "0" else 0.3,
                )
                for line in lines
            ],
            symptoms=["fever", "headache"],
            duration_days=2,
            severity="mild",
        )


@lru_cache
def get_nlp_model() -> NlpModel:
    provider = get_settings().nlp_provider
    if provider == "fake":
        return FakeNlp()
    if provider == "sarvam":
        return SarvamNlp()
    raise ProviderConfigurationError(f"unknown NLP provider: {provider}")
