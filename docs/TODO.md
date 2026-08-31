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
- [x] **Privacy-gated Firebase telemetry** — Analytics and Crashlytics default off, legal acceptance
  enables a fixed PHI-free event schema, sign-out disables collection, and fatal/non-fatal
  exception messages and causes are sanitized before reporting.
- [ ] **Professional legal review** — qualified Indian healthcare/privacy counsel must finalize
  the draft Terms, DPDP notice, consent wording, retention policy, grievance contact, processor
  contracts, and pharmacist-liability framing before any real-patient pilot.

## Real Device Hardening

- [x] **Cloud free-tier analysis** — `docs/CLOUD_DEPLOYMENT_ANALYSIS.md` compares Fly.io, Render,
  Railway, and GCP compute, storage, and secrets. No ongoing free tier safely fits the full stack;
  GCP paid managed services are recommended for a real-patient pilot.

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

## iOS App (Deployment Parity with Android)

> Goal: an iOS client with the same production setup the Android app now has — Firebase
> (`pharmacy-opinion-3`) with consent-gated telemetry, the Cloud Run backend, keyless CI via the
> existing WIF pool, and Firebase App Distribution to `internal-testers`. The shared KMP modules
> (`:shared:domain/data/presentation`) were designed for this ("iosMain later" per
> `docs/ANDROID_APP.md` §2), but today they declare only Android targets, so phase 0 is a
> prerequisite for everything else.

### 0. KMP shared-code enablement (prerequisite)

- [ ] **Add iOS targets to the shared modules** — `iosArm64()` + `iosSimulatorArm64()` in
  `mobile/shared/{domain,data,presentation}/build.gradle.kts`, exported as a single umbrella
  XCFramework (or via CocoaPods/SPM wrapper) that the Xcode project consumes.
- [ ] **Provide `iosMain` implementations of the platform ports** (Android counterparts in
  `shared/data/src/androidMain`):
  - `AudioRecorder` — AVAudioEngine/AVAudioRecorder capturing 16 kHz mono, encoded to AAC
    `.m4a` (AVAudioRecorder can write `.m4a` directly, replacing the Android
    `MediaCodec` pipeline). VAD silence trimming: sherpa-onnx ships iOS xcframeworks, but
    start without trimming (the recorder port already tolerates a no-trim fallback) and add
    VAD as a follow-up.
  - `AudioPlayer` — AVAudioPlayer.
  - `AudioFileReader` / `AudioFileDeleter` — NSData/FileManager over the app container.
  - SQLDelight driver — `NativeSqliteDriver` (`sqldelight-native-driver`) via an
    `IosDatabaseFactory` mirroring `AndroidDatabaseFactory`.
  - Ktor engine — `ktor-client-darwin` in `iosMain` (OkHttp stays Android-only).
  - `AuthTokenStore` / `LegalAcceptanceStore` — Keychain (tokens) and NSUserDefaults
    (per-user legal acceptance), mirroring the SharedPreferences stores.
- [ ] **Create the Xcode project** (`mobile/ios/`) — SwiftUI screens mirroring
  Record/Assessment/History/Login/Legal, driven by the shared `:shared:presentation`
  ViewModels; Koin initialized from Swift with the iOS platform singletons.
- [ ] **Auth parity decision** — Firebase Auth iOS SDK with email/password + Google Sign-In
  (GoogleSignIn SDK). Note: App Store review requires **Sign in with Apple** whenever
  third-party sign-in is offered — either add it or ship email/password only for the
  App-Distribution-only phase (no App Store review applies to Firebase App Distribution).

### 1. Firebase integration (`pharmacy-opinion-3`)

- [ ] **Register the iOS app** in the Firebase console (or
  `firebase apps:create ios`) — bundle ID `org.charged-proton.secondopinion` (underscores are
  invalid in bundle IDs, so the Android package `org.charged_proton.secondopinion` cannot be
  reused verbatim); record the resulting `FIREBASE_APP_ID_IOS` (`1:352579493765:ios:…`).
- [ ] **Download `GoogleService-Info.plist`** into `mobile/ios/` and gitignore it, matching the
  treatment of `google-services.json`; CI receives it via a GitHub secret (base64) or keeps a
  committed copy once the team confirms it is non-secret (same decision as Android, where the
  file is committed).
- [ ] **Add Firebase SDK via SPM** — FirebaseAuth, FirebaseAnalytics, FirebaseCrashlytics.
- [ ] **Port the consent gate** — an iOS `AppTelemetry` equivalent of
  `mobile/app/.../telemetry/AppTelemetry.kt`: Analytics/Crashlytics collection **off by
  default** (`FirebaseAnalyticsCollectionEnabled` / `FirebaseCrashlyticsCollectionEnabled`
  set to `NO` in Info.plist), enabled only after the current legal version is accepted
  (`LegalConsentRepository` is already in `commonMain`), disabled on sign-out, with the same
  fixed PHI-free event schema and sanitized exception reporting.
- [ ] **Crashlytics dSYM upload** — add the run-script/Fastlane `upload_symbols` step so
  release archives symbolicate.

### 2. Backend connectivity

- [ ] **Build-time backend URL** — an `.xcconfig`-driven `BACKEND_BASE_URL` (Debug:
  `http://127.0.0.1:8000` for the compose stack via the simulator's host loopback; Release/CI:
  `https://so-api-7i4kw4366a-el.a.run.app`), injected into the shared `BackendApi` the same way
  the Gradle `-PbackendBaseUrl` property works on Android. Note ATS blocks cleartext by
  default — add a Debug-only ATS exception for localhost rather than a global one.
- [ ] **Upload queue parity (`AssessmentUploadWorker` equivalent)** — the queue state machine
  and `UploadQueueProcessor` live in `commonMain`; iOS needs the scheduler shim:
  `BGTaskScheduler` (`BGProcessingTaskRequest` with `requiresNetworkConnectivity`) triggering
  the shared processor, plus a background `URLSession` (upload task with an on-disk multipart
  body) so the `POST /v1/recordings` multipart upload (`id`, `duration_ms`, `locale`,
  `consent`, `audio` as `.m4a`) survives app suspension. Keep the five bounded
  exponential-backoff attempts; document that BGTaskScheduler timing is opportunistic
  (iOS decides when queued work runs, unlike WorkManager's constraints).
- [ ] **Status polling parity** — shared `BackendAssessmentRepository` already polls
  `GET /v1/recordings/{id}` every 2 s (≤ 8 min) and maps `filtering`/`extracting` to one UI
  stage, so the backend's FILTERING→ASSESSING skip (EXTRACTING no longer set) needs no iOS
  work — verify the mapping once the iOS UI renders `PipelineStage`.
- [ ] **End-to-end verification** — real recording on an iOS device/simulator against
  production, confirming upload → `stage_done` events (`.gcp/read-worker-logs.sh`) →
  assessment rendering, mirroring the Android verification.

### 3. CI/CD pipeline (`.github/workflows/ios.yml`)

- [ ] **Fastlane setup** (`mobile/ios/fastlane/`) — lanes: `test` (unit tests via
  `run_tests`/xcodebuild on a simulator, includes the shared-module Kotlin tests via Gradle),
  `build_adhoc` (archive + export Ad Hoc/Development IPA for App Distribution), and later
  `release` (App Store Connect upload).
- [ ] **Code signing** — decide Match vs. manual:
  - *Match (recommended)*: private certificates repo, `MATCH_PASSWORD` secret, a `match`
    lane populating signing identities on the runner; Ad Hoc profile listing tester device
    UDIDs (App Distribution needs testers' devices in the profile for Ad Hoc builds —
    collect UDIDs via App Distribution's device registration flow).
  - *Interim alternative*: a Development cert + profile exported as base64 secrets, imported
    into a throwaway keychain in CI (`fastlane` `setup_ci` + manual import).
- [ ] **Workflow structure** — mirror `mobile.yml`: trigger on `mobile/ios/**` +
  `mobile/shared/**` paths; jobs `test` → `build` (on `macos-latest`; Xcode pinned via
  `maxim-lobanov/setup-xcode`) → `distribute` (main pushes only). Cache Gradle (Kotlin/Native
  compilation of the shared framework is the slow step) and SPM.
- [ ] **Artifacts** — upload the IPA (retention 3 days, `if-no-files-found: error`) for the
  distribute job, matching the Android APK artifact pattern.

### 4. Firebase App Distribution

- [ ] **Distribute job** — on `push` to `main`, download the IPA artifact, authenticate with
  `google-github-actions/auth@v3` against the existing WIF pool
  (`projects/352579493765/.../providers/github-oidc`, service account
  `github-ftl-ci@pharmacy-opinion-3.iam.gserviceaccount.com` — WIF works identically on macOS
  runners, and the Firebase CLI picks up the exported ADC), then
  `npx firebase-tools@14 appdistribution:distribute <ipa> --app "$FIREBASE_APP_ID_IOS"
  --groups internal-testers` with the commit message + SHA as release notes, exactly as the
  Android `distribute` job does. No new service account or JSON key needed.
- [ ] **Tester onboarding** — testers install the App Distribution profile on iOS; for Ad Hoc
  builds, register their UDIDs in the Apple Developer portal and regenerate the profile
  (Match `match adhoc --force_for_new_devices`).

### 5. Secret management (GitHub Actions secrets)

- [ ] Document and create in the repo settings (names, not values, tracked here):
  | Secret | Purpose |
  |---|---|
  | `APP_STORE_CONNECT_KEY` | App Store Connect API key `.p8` (base64) — Fastlane auth for signing/profile management and future TestFlight |
  | `APP_STORE_CONNECT_KEY_ID` / `APP_STORE_CONNECT_ISSUER_ID` | Companion identifiers for the API key |
  | `MATCH_PASSWORD` | Encryption passphrase for the Match certificates repo |
  | `MATCH_GIT_TOKEN` | Read access to the private certificates repo (if Match over git) |
  | `FIREBASE_APP_ID_IOS` | Plain env var candidate (not secret) — iOS app ID for App Distribution, mirroring the Android `FIREBASE_APP_ID` env in `mobile.yml` |
  | `IOS_GOOGLE_SERVICE_INFO_PLIST` | Base64 plist, only if the team opts not to commit it |
  - Note: **no Firebase token secret** — WIF covers App Distribution auth, matching Android.
- [ ] **Apple Developer Program membership** — prerequisite for signing/distribution
  ($99/yr); decide the team account before the signing work starts.

### 6. Documentation

- [ ] **Create `docs/IOS_APP.md`** — mirror `docs/ANDROID_APP.md`'s structure: tech-stack
  table (SwiftUI, shared KMP modules, BGTaskScheduler, AVFoundation), module/file map,
  upload + polling flow diagram, and the state-machine parity note: the backend now moves
  FILTERING→ASSESSING directly (EXTRACTING remains in the enum but is never set —
  `docs/BACKEND.md` §2.2), and the shared stage mapping already folds both into one UI stage.
- [ ] **Cross-link** — update `docs/ANDROID_APP.md` ("iosMain later" note), `docs/BACKEND.md`
  §12 related-documents list, and this TODO as items complete.
