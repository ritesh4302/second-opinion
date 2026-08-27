# Second Opinion — TODO

> Remaining work identified in the post-auth gap analysis (2026-08-09). Roadmap context in
> `docs/PROJECT_DOCUMENTATION.md` §8; architecture in `docs/BACKEND.md` / `docs/ANDROID_APP.md`.

## AI Pipeline (Sarvam Integration)

- [ ] **Benchmark Sarvam Saaras v3 on real Hinglish audio** — diarization quality (≤8 speakers),
  ASR accuracy on pharmacy-style exchanges, and patient-speaker inference (Q1/Q2 in
  `docs/PROJECT_DOCUMENTATION.md` §9). Only verified on one synthesized two-voice sample so far.
- [x] **Implement Celery DLQ/retry logic** — stage tasks now use bounded exponential backoff with
  jitter, sanitized persistent retry metadata, permanent/transient classification, a dedicated
  `pipeline.dlq` route, and operator replay from the failed stage.
- [x] **Configure real Sarvam providers for local integration** — compose keeps safe `fake`
  fallbacks, while ignored `backend/.env` settings opt the worker into Saaras v3 and sarvam-105b;
  the key is injected only into the worker and the full live provider chain has been exercised.
- [ ] **Configure production Sarvam secret injection** — after selecting a hosting platform,
  store `SO_SARVAM_API_KEY` in its secret manager, scope it to workers, and add rotation, quota,
  spend, and provider-failure alerts.
- [ ] Benchmark a dedicated medical LLM for the `Assessor` port (Q3; sarvam-105b is interim).

## Mobile UI/UX Gaps

- [x] **Sign-out UI** — top row on `RecordScreen` showing the signed-in identity with a
  "Sign out" action, wired through `AuthViewModel` → `SignOutUseCase` → `AuthClient.signOut()`.
- [x] **Migrate `InMemoryCaseRepository` to SQLDelight** — owner-scoped case metadata now
  survives process death through `SqlDelightCaseRepository`; `AppModule` uses the persistent
  database and host tests cover restoration, observation, status updates, deletion, and account
  isolation.
- [x] **Persist assessment and feedback results in SQLDelight** — owner-scoped assessment JSON
  and pharmacist decisions now survive process death through `SqlDelightAssessmentStore`; the
  v1→v2 migration and host tests cover restoration, deletion, and account isolation.
- [x] **Implement the offline upload queue** — SQLDelight persists owner-scoped queue state,
  WorkManager runs unique network-constrained uploads with five bounded exponential-backoff
  attempts, the assessment UI supports manual retry, and new audio is stored under app-private
  durable storage instead of the evictable cache.
- [x] **"Forgot password" UX** — the login screen validates and normalizes email, invokes
  Firebase `sendPasswordResetEmail`, avoids account-enumeration messaging, and surfaces localized
  success/failure state. Email verification on sign-up remains a separate product decision.
- [x] **Legal disclaimers and terms UX** — signed-in users must explicitly accept the current
  version of the Terms and Privacy Notice; acceptance is timestamped per pharmacist. Login and
  authenticated screens retain document links, and CDS responsibility is always visible.
- [ ] **Professional legal review** — qualified Indian healthcare/privacy counsel must finalize
  the draft Terms, DPDP notice, consent wording, retention policy, grievance contact, processor
  contracts, and pharmacist-liability framing before any real-patient pilot.

## Real Device Hardening

- [ ] **Release signing** — create a release keystore, add a `signingConfigs.release` block to
  `mobile/app/build.gradle.kts` (credentials via `local.properties`/env, never committed), and
  register the release SHA-1/SHA-256 in the Firebase console (re-download
  `google-services.json` afterwards).
- [x] **R8/ProGuard verification** — release optimization/resource shrinking is enabled; Firebase,
  Ktor, and kotlinx-serialization consumer rules resolve cleanly, a narrow sherpa-onnx JNI rule
  preserves native names, all four ABI libraries remain packaged, and R8 emits no missing rules.
- [ ] **Tunnel-based `BACKEND_BASE_URL`** — the debug URL `http://127.0.0.1:8000` only works via
  `adb reverse`; add a build-time override (Gradle property or flavor) pointing at an HTTPS
  tunnel (ngrok/cloudflared) so off-network devices and release builds (no cleartext
  exemption) can reach the backend.
- [ ] Physical-device pass: "deny twice" permission path, VAD trim with real mic noise,
  Bluetooth headset input routing.
