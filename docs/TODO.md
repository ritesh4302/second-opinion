# Second Opinion — TODO

> Remaining work identified in the post-auth gap analysis (2026-08-09). Roadmap context in
> `docs/PROJECT_DOCUMENTATION.md` §8; architecture in `docs/BACKEND.md` / `docs/ANDROID_APP.md`.

## AI Pipeline (Sarvam Integration)

- [ ] **Benchmark Sarvam Saaras v3 on real Hinglish audio** — diarization quality (≤8 speakers),
  ASR accuracy on pharmacy-style exchanges, and patient-speaker inference (Q1/Q2 in
  `docs/PROJECT_DOCUMENTATION.md` §9). Only verified on one synthesized two-voice sample so far.
- [ ] **Implement Celery DLQ/retry logic** — the state machine documents "retryable up to N
  times → DLQ" (`docs/BACKEND.md` §2.2), but pipeline failures currently just set
  `failed` + stage in `backend/worker/pipeline.py` with no automated retry or dead-letter path.
- [ ] Decide the compose default for `SO_SPEECH_PROVIDER` / `SO_NLP_PROVIDER` /
  `SO_ASSESSMENT_PROVIDER` (still `fake`; real Sarvam needs `SO_SARVAM_API_KEY` — consider an
  `.env` file for `backend/docker-compose.yml`).
- [ ] Benchmark a dedicated medical LLM for the `Assessor` port (Q3; sarvam-30b is interim).

## Mobile UI/UX Gaps

- [x] **Sign-out UI** — top row on `RecordScreen` showing the signed-in identity with a
  "Sign out" action, wired through `AuthViewModel` → `SignOutUseCase` → `AuthClient.signOut()`.
- [ ] **Migrate `InMemoryCaseRepository` to SQLDelight** — case history is lost on process
  death; the planned local DB (`docs/ANDROID_APP.md` module layout: recording queue + results
  cache) is unimplemented. Also a prerequisite for an offline upload queue.
- [ ] **"Forgot password" UX** — Firebase `sendPasswordResetEmail` flow from the login screen
  (plus consider email verification on sign-up).
- [ ] **Legal disclaimers** — explicit in-app terms/liability framing (Q6): pharmacist is the
  final decision-maker; needed before pilot alongside the existing per-assessment disclaimer.

## Real Device Hardening

- [ ] **Release signing** — create a release keystore, add a `signingConfigs.release` block to
  `mobile/app/build.gradle.kts` (credentials via `local.properties`/env, never committed), and
  register the release SHA-1/SHA-256 in the Firebase console (re-download
  `google-services.json` afterwards).
- [ ] **R8/ProGuard verification** — release build currently has `optimization { enable = false }`;
  enable R8 and verify keep rules for Firebase Auth, Ktor/kotlinx-serialization, and the
  sherpa-onnx JNI bindings.
- [ ] **Tunnel-based `BACKEND_BASE_URL`** — the debug URL `http://127.0.0.1:8000` only works via
  `adb reverse`; add a build-time override (Gradle property or flavor) pointing at an HTTPS
  tunnel (ngrok/cloudflared) so off-network devices and release builds (no cleartext
  exemption) can reach the backend.
- [ ] Physical-device pass: "deny twice" permission path, VAD trim with real mic noise,
  Bluetooth headset input routing.
