# Second Opinion — Project Documentation

> **Purpose of this document:** Primary context for humans and coding agents working on this
> project. It captures the problem, the agreed solution, key decisions, architecture, current
> codebase state, and the roadmap. Keep it updated as decisions change.

**Last updated:** 2026-07-24
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
2. **Medicine guidance** — OTC preferred; prescription (Schedule H/H1) medicines may be
   suggested but always carry a clear "prescription drug" label (requirement change: was
   OTC-only with a hard blocklist)
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

Android app wired to the backend pipeline end-to-end.

| Area | State |
|---|---|
| Project | Kotlin Multiplatform (KMM), layer-per-module: `:app` (Compose UI) + `:shared:domain` / `:shared:data` / `:shared:presentation`; Material3, dynamic color; minSdk 29, targetSdk 37 |
| Package | `org.charged_proton.secondopinion` |
| UI flow | Login gate (email/password form + Google Sign-In button) → record (patient-consent dialog → Speak/Stop + runtime permission) → assessment (pipeline progress, referral banner on red flags, prescription-labeled guidance, pharmacist accept/reject/override) → history (case list + recording playback + delete with confirmation); Navigation Compose |
| Audio capture | `AudioRecord` 16 kHz mono PCM → Silero VAD silence trim (sherpa-onnx) → AAC/.m4a in app cache (see §6.3 and `docs/ANDROID_APP.md` §5.1) |
| Data layer | `BackendAssessmentRepository` (Ktor): multipart upload (with consent flag) → status polling → assessment fetch → feedback POST; case deletion = backend DELETE + local audio-file cleanup; in-memory case store (SQLDelight pending); mock repository kept for tests/demo |
| `AndroidManifest.xml` | `RECORD_AUDIO` + `INTERNET`; debug manifest allows cleartext HTTP to the dev stack |
| Auth / login | Backend: Firebase ID-token verification (`TokenVerifier` port, `firebase` \| `fake` providers; issuer `securetoken.google.com/<project>`, audience = project id), `users` table (Firebase UID + email + display name), owner-scoped recordings, `GET /v1/auth/me`. App: login gate with Google Sign-In and email/password sign-in/sign-up (`AuthClient` port; production adapter `FirebaseAuthClient` — Credential Manager or email-password → Firebase Auth SDK → backend — selected at runtime when google-services.json is present, fake adapter otherwise), `Authorization: Bearer` on every call, 401 → sign-out |
| Backend | `backend/` FastAPI + Celery: `POST /v1/recordings` upload → MinIO + Postgres, speech worker (Sarvam Saaras v3 Batch API, native diarization), NLP worker (sarvam-105b relevance filter + structured extraction), assessment worker (sarvam-105b triage: conditions + confidence, red flags, OTC-preferred guidance with `prescription` labels; fake providers for dev), status/assessment/feedback endpoints, Alembic migrations, docker-compose dev stack (see `docs/BACKEND.md`) |
| App ↔ backend | Wired (debug base URL `http://127.0.0.1:8000` via `adb reverse tcp:8000 tcp:8000`); verified end-to-end on emulator against the docker-compose stack incl. feedback persistence; assessment response now includes `symptom_summary` |
| Tests | Mobile: 96 host unit tests (`commonTest`) + 27 Compose UI tests (`androidTest`); Backend: 50 pytest tests |

## 8. Roadmap

### Phase 1 — Capture POC (complete)
- [x] Record patient audio via Speak button with runtime permission
- [x] Silero VAD trimming before upload
- [x] Recording list / playback for pharmacist verification (history screen Play/Stop)
- [x] Consent capture step (tap-to-confirm) before recording

### Phase 2 — Speech pipeline
- [x] Backend service skeleton + audio upload endpoint
- [ ] Benchmark ASR candidates (Sarvam vs AI4Bharat vs Scribe) on real pharmacy-style Hinglish audio
- [x] Diarization integration — Sarvam Batch API native diarization (single vendor; pyannote deferred, see Q2)
- [x] LLM relevance weighting + irrelevant-segment filtering (sarvam-105b; content-based patient inference per Q1)
- [x] Structured extraction: symptoms, age, gender, location, duration, severity (sarvam-105b, Pydantic-validated JSON)
- [x] App ↔ backend integration — Ktor multipart upload → status polling → assessment fetch →
  feedback POST (`BackendAssessmentRepository`), verified end-to-end on emulator

### Phase 3 — Assessment & decision support
- [x] Medical AI model integration → assessment + confidence + red flags (interim: sarvam-105b behind the `Assessor` port; dedicated medical LLM pending Q3 benchmark)
- [x] Prescription-drug labeling (requirement change: no hard blocklist) — guidance items
  carry `prescription: true/false` from the assessment stage
- [x] "Prescription drug" label rendering in the app UI (badge on guidance items with
  `prescription: true`)
- [x] Red-flag referral escalation UI (prominent "Refer to a doctor" banner above all content)
- [x] Pharmacist accept/reject/override capture (feedback loop) — full three-way decision bar
  wired to `POST /v1/assessments/{id}/feedback` (verified end-to-end: decision persisted in
  Postgres)

### Phase 4 — Hardening & pilot
- [ ] Authentication and role model (pharmacist / patient / doctor) — backend done
  (Firebase Google Sign-In ID-token verification, `users` table + role enum,
  owner-scoped data, `/v1/auth/me`); app Google Sign-In login gate + bearer-token calls
  + Credential Manager → Firebase Auth SDK adapter (`FirebaseAuthClient`) done;
  remaining: Firebase project provisioning (google-services.json), sign-out UI
- [x] DPDP-compliant consent, retention, and deletion flows — upload requires
  `consent_confirmed=true` (set from the in-app consent dialog and stored on the
  recording), `DELETE /v1/recordings/{id}` erasure cascades to audio + transcripts +
  extraction + assessment + feedback (app: per-case delete with confirmation in
  history, incl. local audio-file cleanup), daily retention sweep purges audio +
  transcripts after `SO_RETENTION_DAYS` (default 30)
- [ ] Legal/regulatory review (CDSCO classification, pharmacist liability)
- [ ] Pilot in North India Hindi-belt pharmacies

## 9. Open Questions & Risks

| # | Item | Notes |
|---|---|---|
| Q1 | How to identify which diarized speaker is the patient | Content-based inference (first-person symptom language) implemented in the NLP stage relevance prompt; accuracy on real pharmacy audio still to be validated |
| Q2 | ~~Does Sarvam provide adequate diarization natively?~~ | **Answered:** yes — Saaras v3 Batch API supports `with_diarization` (≤1 h, ≤8 speakers); integrated as single-vendor speech stage. Quality on real pharmacy audio still to be validated (benchmark task) |
| Q3 | Which medical AI model for assessment | Interim: general sarvam-105b behind the `Assessor` port; dedicated medical LLM (open-source, e.g. MedGemma-class, vs API) still to be benchmarked — drop-in swap |
| Q4 | CDSCO medical-device classification | Legal review required before pilot |
| Q5 | DPDP Act 2023 compliance | Voice + health data are sensitive; consent capture, erasure (in-app case deletion), and retention sweep are implemented — legal review of the policy itself (window length, consent wording) still needed before real-patient use |
| Q6 | Liability framing | Pharmacist is final decision-maker; needs explicit in-app disclaimers and terms |

## 10. Related Files

- `docs/ANDROID_APP.md` — Android app architecture, data flow, and developer/agent guide
- `docs/BACKEND.md` — backend architecture, components, tech stack, and scaling practices
- `docs/ideation.txt` — original rough sketch of the idea
