"""Speech stage port + providers.

Sarvam Saaras v3 bundles diarization with ASR in one Batch API call
(docs/BACKEND.md §10 step 3; answers open question Q2 — no self-hosted
pyannote needed for the POC). Providers are swappable behind `Transcriber`
so a pyannote+ASR combo can be added later without touching the pipeline.
"""

import json
import logging
import tempfile
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Protocol

from app.settings import get_settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Segment:
    speaker_label: str
    text: str
    start_ms: int
    end_ms: int


class TranscriptionError(RuntimeError):
    pass


class Transcriber(Protocol):
    """Port for diarized transcription; tests provide a fake."""

    def transcribe(self, audio: bytes, filename: str, locale: str) -> list[Segment]: ...


def parse_sarvam_output(data: dict) -> list[Segment]:
    """Parse one Batch API output JSON into ordered segments."""
    entries = (data.get("diarized_transcript") or {}).get("entries") or []
    segments = [
        Segment(
            speaker_label=str(entry.get("speaker_id", "unknown")),
            text=entry["transcript"],
            start_ms=round(float(entry["start_time_seconds"]) * 1000),
            end_ms=round(float(entry["end_time_seconds"]) * 1000),
        )
        for entry in entries
    ]
    if not segments and data.get("transcript"):
        # Diarization can come back empty on very short/single-speaker audio.
        segments = [Segment("unknown", data["transcript"], 0, 0)]
    return segments


class SarvamTranscriber:
    """Sarvam Batch STT with native diarization (saaras:v3, up to 1 h / 8 speakers)."""

    def __init__(self) -> None:
        from sarvamai import SarvamAI

        settings = get_settings()
        if not settings.sarvam_api_key:
            raise TranscriptionError("SO_SARVAM_API_KEY is not set")
        self._client = SarvamAI(api_subscription_key=settings.sarvam_api_key)
        self._model = settings.sarvam_model
        self._timeout_s = settings.sarvam_job_timeout_s

    def transcribe(self, audio: bytes, filename: str, locale: str) -> list[Segment]:
        job = self._client.speech_to_text_job.create_job(
            model=self._model,
            mode="transcribe",
            language_code=locale,
            with_diarization=True,
        )
        with tempfile.TemporaryDirectory() as tmp:
            audio_path = Path(tmp) / filename
            audio_path.write_bytes(audio)
            if not job.upload_files(file_paths=[str(audio_path)]):
                raise TranscriptionError("Sarvam batch upload failed")
            job.start()
            job.wait_until_complete(timeout=self._timeout_s)

            results = job.get_file_results()
            if results.get("failed") or not results.get("successful"):
                detail = "; ".join(str(f.get("error_message")) for f in results.get("failed", []))
                raise TranscriptionError(f"Sarvam batch job failed: {detail or 'no output'}")

            out_dir = Path(tmp) / "output"
            out_dir.mkdir()
            job.download_outputs(output_dir=str(out_dir))
            outputs = sorted(out_dir.glob("*.json"))
            if not outputs:
                raise TranscriptionError("Sarvam batch job produced no output files")
            return parse_sarvam_output(json.loads(outputs[0].read_text()))


class FakeTranscriber:
    """Deterministic segments for dev/demo runs without an API key."""

    def transcribe(self, audio: bytes, filename: str, locale: str) -> list[Segment]:
        logger.info("fake transcriber: returning canned segments (%d audio bytes)", len(audio))
        return [
            Segment("0", "मुझे दो दिन से बुखार और सिर दर्द है।", 0, 3200),
            Segment("1", "कोई दवा ली अभी तक?", 3400, 4800),
            Segment("0", "नहीं, अभी तक कुछ नहीं लिया।", 5000, 7100),
        ]


@lru_cache
def get_transcriber() -> Transcriber:
    provider = get_settings().speech_provider
    if provider == "fake":
        return FakeTranscriber()
    if provider == "sarvam":
        return SarvamTranscriber()
    raise TranscriptionError(f"unknown speech provider: {provider}")
