# Second Opinion — Project Documentation

> **Purpose of this document:** Primary context for humans and coding agents working on this
> project. It captures the problem, the agreed solution, key decisions, architecture, current
> codebase state, and the roadmap. Keep it updated as decisions change.

**Last updated:** 2026-07-18
**Status:** POC in progress

---

## 1. Problem Statement

In rural and semi-urban India, pharmacies are often the first — and sometimes only — point of
contact for healthcare. Patients bypass doctors due to cost, distance, long wait times, and low
doctor availability. As a result, pharmacists routinely make de-facto clinical judgments they are
neither trained nor legally authorized to make. This leads to:

- Misdiagnosis and inappropriate medication (including antibiotic misuse)
- Delayed escalation of serious conditions that need a doctor
- Legal and ethical risk carried informally by the pharmacist

## 2. Solution

**Second Opinion** is an AI-powered **clinical decision-support and triage tool** for pharmacists.
It captures the patient's spoken symptom description (Hindi with English code-switching), converts
it into a structured symptom summary, and returns:

1. **Preliminary assessment** — possible condition categories with confidence levels
   (explicitly *not* a definitive diagnosis)
2. **OTC-only guidance** — never scheduled (Schedule H/H1) drugs
3. **Red-flag escalation** — a prominent "refer to doctor" path for danger signs
   (e.g., chest pain, infant fever, breathing difficulty)

**The human is always the final authority.** The pharmacist reviews every output and decides
whether to act on it. The app assists; it does not prescribe or diagnose.

### 2.1 Positioning (agreed, final)

- Decision support / triage tool — **not** a diagnostic or prescribing device.
- This framing addresses liability concerns : the pharmacist remains
  the decision-maker; the tool provides information, not instructions.
- Regulatory note: outputs framed as "diagnosis + prescription" would likely classify the
  software as a medical device under CDSCO rules. Triage/decision-support framing reduces (but
  does not eliminate) this risk. Legal review required before public launch.

## 3. Target Users

Kept intentionally open-ended (agreed decision):

| User | Role | Priority |
|---|---|---|
| **Pharmacist** | Primary user — records patient, reviews AI output, decides action | MVP |
| **Patient** | May use the app directly for self-triage | Later |
| **Doctor** | May use it as a support/second-opinion tool during consultations | Later |

Design implication: core pipeline (capture → transcribe → assess) must stay user-role agnostic;
role-specific behavior lives at the UI/response layer.

## 4. Key Decisions & Assumptions (agreed)

| # | Decision | Value |
|---|---|---|
| D1 | Positioning | Triage + decision support + OTC guidance + referral escalation |
| D2 | Audio capture strategy | **Option B**: ambient recording on device, lightweight on-device pre-processing (VAD trim, compression), server-side diarization + transcription |
| D3 | Pilot geography / language | North India Hindi belt; Hindi with English code-switching (Hinglish) |
| D4 | Connectivity | Good network connection assumed; no offline-first requirement for MVP |
| D5 | Users | Open-ended (pharmacist / patient / doctor) |
| D6 | Security (POC phase) | No authentication or login flows; functionality over security |

## 5. System Architecture

```
┌────────────────────── Android App (Kotlin, Compose) ──────────────────────┐
│ Record audio → VAD trim (Silero via sherpa-onnx) → AAC compress → upload │
└──────────────────────────────────┬────────────────────────────────────────┘
                                   │ HTTPS (audio + metadata)
┌──────────────────────────────────▼────────────────────────────────────────┐
│                              Backend (TBD)                                │
│  1. Speaker diarization  — who spoke when (pyannote / NeMo / bundled)     │
│  2. ASR (Hinglish)       — Sarvam Saaras v3 (primary candidate)           │
│  3. Relevance filtering  — LLM assigns weight per speaker/segment,        │
│                            keeps patient-relevant content                 │
│  4. Extraction           — symptoms, age, gender, location, duration      │
│  5. Medical AI model     — preliminary assessment + confidence + red flags│
└──────────────────────────────────┬────────────────────────────────────────┘
                                   │ JSON response
┌──────────────────────────────────▼────────────────────────────────────────┐
│  App displays: assessment, OTC guidance, red-flag referral prompt.        │
│  Pharmacist accepts / rejects / overrides → decision logged (feedback).   │
└────────────────────────────────────────────────────────────────────────────┘
```

### 5.1 Why server-side diarization (Option B rationale)

Pharmacy recordings contain multiple voices: the patient, family members, other customers, and
the pharmacist. Speaker separation in noisy environments is too heavy/immature for the budget
Android devices common in the target market. With good connectivity assumed (D4), the device
only records, trims, compresses, and uploads; all heavy AI runs server-side.

### 5.2 Speaker relevance weighting

After diarization + transcription, an LLM step assigns a relevance weight to each speaker's
text (patient symptoms high; unrelated customer chatter low) and discards irrelevant segments.
Identifying *which* speaker is the patient is an open problem (see §9) — initial approach:
infer from content (first-person symptom descriptions) rather than voice identity.

## 6. Technology Choices — Speech Pipeline

### 6.1 Server-side ASR (Hindi + Hinglish)

| Tool | Type | Hinglish quality | Cost | Verdict |
|---|---|---|---|---|
| **Sarvam AI Saaras v3** | Paid API (India) | Best-in-class; built for code-mixed Indic speech | ~₹30/hr (~$0.006/min) | **Primary candidate** |
| AI4Bharat IndicConformer / IndicWhisper | Open source | Good (Hindi WER ~12–18% clean, 22–30% noisy) | Free + self-hosted GPU | Long-term cost/data-sovereignty option |
| ElevenLabs Scribe v2 | Paid API | Good, 90+ languages | Higher per-hour | Fallback; diarization built in |
| Google Chirp 3 / Gemini | Paid API | Decent; script handling issues on mixed sentences | ~$0.016/min | Fallback |
| Deepgram Nova-3 | Paid API | Hindi yes; code-mix weaker | ~$0.0043/min | Low latency option |
| Azure / AWS Transcribe | Paid API | hi-IN supported; not code-mix optimized | ~$0.016–0.024/min | Enterprise compliance option |
| Bhashini (Govt. DPI) | Free/subsidized | Indic-focused | Free tiers | Evaluate; mission-aligned |

### 6.2 Server-side speaker diarization

| Tool | Type | Accuracy (DER) | Notes |
|---|---|---|---|
| **pyannote community-1 / 3.1** | Open source (CC-BY/MIT) | ~11–19% | **Primary candidate** — most popular, easy; paid `precision-2` upgrade path |
| NVIDIA NeMo (MSDD/Sortformer) | Open source | ~8% (best OSS) | Higher accuracy, more GPU/ops effort |
| WhisperX | Open source | Uses pyannote | Bundles ASR + word timestamps + diarization |
| Bundled commercial (ElevenLabs, AssemblyAI, Deepgram) | Paid | Competitive | Zero setup; single-vendor simplicity |

### 6.3 On-device (Android) pre-processing

| Tool | Role | License |
|---|---|---|
| **Silero VAD** (via sherpa-onnx) | Trim silence before upload | MIT — **implemented** |
| **sherpa-onnx** | Android toolkit: VAD, noise suppression; on-device ASR/diarization if ever needed offline | Apache-2.0 — **implemented** (v1.13.4, JitPack AAR) |
| RNNoise | Noise suppression for shop environments | BSD — optional |
| AudioRecord + MediaCodec (AAC/M4A) | Raw PCM capture + compression after VAD trim | Android SDK — **implemented** (replaced MediaRecorder) |

## 7. Current State of the Codebase

Android app + backend skeleton (Phase 2 started).

| Area | State |
|---|---|
| Project | Kotlin Multiplatform (KMM), layer-per-module: `:app` (Compose UI) + `:shared:domain` / `:shared:data` / `:shared:presentation`; Material3, dynamic color; minSdk 29, targetSdk 37 |
| Package | `org.charged_proton.secondopinion` |
| UI flow | Record (Speak/Stop + runtime permission) → assessment (pipeline progress, result, pharmacist accept/override) → history; Navigation Compose |
| Audio capture | `AudioRecord` 16 kHz mono PCM → Silero VAD silence trim (sherpa-onnx) → AAC/.m4a in app cache (see §6.3 and `docs/ANDROID_APP.md` §5.1) |
| Data layer | Mock: in-memory case repository + simulated assessment pipeline with canned scenarios (real upload/backend integration is Phase 2) |
| `AndroidManifest.xml` | `RECORD_AUDIO` permission declared |
| Auth / login | None — intentional for POC (D6) |
| Backend | `backend/` FastAPI + Celery: `POST /v1/recordings` upload → MinIO + Postgres, speech worker (Sarvam Saaras v3 Batch API, native diarization), NLP worker (sarvam-m relevance filter + structured extraction; fake providers for dev), status/assessment/feedback endpoints, Alembic migrations, docker-compose dev stack (see `docs/BACKEND.md`) |
| App ↔ backend | Not wired yet — app still runs on its mock data layer; AI pipeline stages not implemented |
| Tests | Mobile: 48 host unit tests (`commonTest`) + 6 Compose UI tests (`androidTest`); Backend: 12 pytest API tests |

## 8. Roadmap

### Phase 1 — Capture POC (current)
- [x] Record patient audio via Speak button with runtime permission
- [x] Silero VAD trimming before upload
- [ ] Recording list / playback for pharmacist verification
- [ ] Consent capture step (tap-to-confirm) before recording

### Phase 2 — Speech pipeline
- [x] Backend service skeleton + audio upload endpoint
- [ ] Benchmark ASR candidates (Sarvam vs AI4Bharat vs Scribe) on real pharmacy-style Hinglish audio
- [x] Diarization integration — Sarvam Batch API native diarization (single vendor; pyannote deferred, see Q2)
- [x] LLM relevance weighting + irrelevant-segment filtering (sarvam-m; content-based patient inference per Q1)
- [x] Structured extraction: symptoms, age, gender, location, duration, severity (sarvam-m, Pydantic-validated JSON)

### Phase 3 — Assessment & decision support
- [ ] Medical AI model integration → assessment + confidence + red flags
- [ ] OTC-only guidance layer with hard blocklist of scheduled drugs
- [ ] Red-flag referral escalation UI
- [ ] Pharmacist accept/reject/override capture (feedback loop)

### Phase 4 — Hardening & pilot
- [ ] Authentication and role model (pharmacist / patient / doctor)
- [ ] DPDP-compliant consent, retention, and deletion flows
- [ ] Legal/regulatory review (CDSCO classification, pharmacist liability)
- [ ] Pilot in North India Hindi-belt pharmacies

## 9. Open Questions & Risks

| # | Item | Notes |
|---|---|---|
| Q1 | How to identify which diarized speaker is the patient | Content-based inference (first-person symptom language) implemented in the NLP stage relevance prompt; accuracy on real pharmacy audio still to be validated |
| Q2 | ~~Does Sarvam provide adequate diarization natively?~~ | **Answered:** yes — Saaras v3 Batch API supports `with_diarization` (≤1 h, ≤8 speakers); integrated as single-vendor speech stage. Quality on real pharmacy audio still to be validated (benchmark task) |
| Q3 | Which medical AI model for assessment | To be researched in Phase 3 (open-source medical LLMs vs API) |
| Q4 | CDSCO medical-device classification | Legal review required before pilot |
| Q5 | DPDP Act 2023 compliance | Voice + health data are sensitive; consent flow and retention policy needed before any real-patient use |
| Q6 | Liability framing | Pharmacist is final decision-maker; needs explicit in-app disclaimers and terms |

## 10. Related Files

- `docs/ANDROID_APP.md` — Android app architecture, data flow, and developer/agent guide
- `docs/BACKEND.md` — backend architecture, components, tech stack, and scaling practices
- `docs/ideation.txt` — original rough sketch of the idea
