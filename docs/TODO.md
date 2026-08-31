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

- [x] **Add iOS targets to the shared modules** — `iosArm64()` + `iosSimulatorArm64()` in
  `mobile/shared/{domain,data,presentation}/build.gradle.kts`; `:shared:presentation`
  builds the umbrella `SharedKit.xcframework` (exports domain + data) via
  `./gradlew :shared:presentation:assembleSharedKitDebugXCFramework`.
- [x] **Provide `iosMain` implementations of the platform ports** (Android counterparts in
  `shared/data/src/androidMain`; iOS ones in `shared/data/src/iosMain`):
  - `AudioRecorder` — `AvAudioRecorder` (AVAudioRecorder, 16 kHz mono AAC `.m4a` written
    directly). VAD silence trimming not yet applied (the port tolerates untrimmed audio);
    sherpa-onnx iOS xcframework integration is a follow-up.
  - `AudioPlayer` — `AvAudioPlayerAudioPlayer` (AVAudioPlayer).
  - `AudioFileReader` / `AudioFileDeleter` — `IosAudioFileReader` (NSData) /
    `IosAudioFileDeleter` (NSFileManager).
  - SQLDelight driver — `IosDatabaseFactory` on `NativeSqliteDriver`
    (`sqldelight-native-driver`).
  - Ktor engine — `createBackendApi` on `ktor-client-darwin` (OkHttp stays Android-only).
  - `AuthTokenStore` / `LegalAcceptanceStore` — `UserDefaultsAuthTokenStore` /
    `UserDefaultsLegalAcceptanceStore` (NSUserDefaults); token store to migrate to
    Keychain before wider distribution.
- [x] **Create the Xcode project** (`mobile/ios/`) — `SecondOpinion.xcodeproj` with a
  filesystem-synchronized `SecondOpinion/` group; SwiftUI screens mirroring
  Record/Assessment/History/Login/Legal driven by the shared ViewModels via a
  `ViewModelObserver` + Kotlin `FlowWatcher` bridge. DI is a manual `IosAppGraph`
  (in `shared/presentation/src/iosMain`) mirroring Koin's `appModule` — Koin itself is not
  initialized on iOS, keeping Koin types out of the Swift surface. A run-script phase invokes
  `./gradlew :shared:presentation:embedAndSignAppleFrameworkForXcode`; the target links
  SharedKit statically plus `-lsqlite3` (SQLDelight native driver). `BACKEND_BASE_URL` is a
  build setting (Debug: `http://127.0.0.1:8000`, Release: Cloud Run) surfaced via Info.plist.
  Upload scheduling uses an interim in-process `InProcessAssessmentScheduler` (same 5-attempt
  backoff as Android's worker) until the BGTaskScheduler shim (§2) lands. Simulator build is
  green (`xcodebuild -project mobile/ios/SecondOpinion.xcodeproj -target SecondOpinion
  -sdk iphonesimulator -arch arm64 build`).
- [x] **Auth parity decision** — Firebase Auth iOS SDK with **email/password only** for the
  App-Distribution-only phase: App Store review requires **Sign in with Apple** whenever
  third-party sign-in is offered, so Google Sign-In (GoogleSignIn SDK) is deferred and the
  login screen hides the Google button (`IosAppGraph.supportsGoogleSignIn`). The graph wires
  `BridgedAuthClient` over the Swift `FirebaseAuthBridge` when FirebaseApp configures, and
  falls back to `FakeGoogleAuthClient` (pairs with the backend's `SO_AUTH_PROVIDER=fake`)
  otherwise — the same presence check Android uses.

### 1. Firebase integration (`pharmacy-opinion-3`)

- [x] **Register the iOS app** in the Firebase console — bundle ID
  `org.charged-proton.secondopinion` (underscores are invalid in bundle IDs, so the Android
  package `org.charged_proton.secondopinion` cannot be reused verbatim);
  `FIREBASE_APP_ID_IOS` is `1:352579493765:ios:2034da767b6af732226eec`.
- [x] **`GoogleService-Info.plist`** committed at `mobile/ios/SecondOpinion/GoogleService-Info.plist`,
  matching the Android decision to commit `google-services.json` as non-secret (no CI
  base64 secret needed).
- [x] **Add Firebase SDK via SPM** — firebase-ios-sdk 12.x (`upToNextMajorVersion` from
  12.18.0) with FirebaseAuth, FirebaseAnalytics, FirebaseCrashlytics products;
  `Package.resolved` pins the graph.
- [x] **Port the consent gate** — `TelemetryController` (Swift) is the iOS counterpart of
  `mobile/app/.../telemetry/AppTelemetry.kt`: Analytics/Crashlytics collection **off by
  default** (`FirebaseAnalyticsCollectionEnabled` / `FirebaseCrashlyticsCollectionEnabled`
  set to `NO` in Info.plist), enabled only after the current legal version is accepted and
  disabled on sign-out (same gates as Android's AuthGate/LegalGate), with
  `resetAnalyticsData()` + `deleteUnsentReports()` on disable. The PHI-free event schema
  (screen views/events/sanitized non-fatals) is not yet emitted from iOS screens — port
  alongside the BGTaskScheduler work in §2.
- [x] **Crashlytics dSYM upload** — the Fastlane `build_adhoc` lane runs
  `upload_symbols_to_crashlytics` after `build_app`, using the `upload-symbols` binary from
  the firebase-ios-sdk SPM checkout (no run-script phase, so Debug builds stay fast).
  Unverified until the first signed archive exists (§3 signing).

### 2. Backend connectivity

- [x] **Build-time backend URL** — `mobile/ios/Config/{Debug,Release}.xcconfig` drive
  `BACKEND_BASE_URL` (Debug: `http://127.0.0.1:8000` for the compose stack via the
  simulator's host loopback; Release/CI: `https://so-api-7i4kw4366a-el.a.run.app`) as base
  configurations on the target. The ATS localhost exception is Debug-only: Debug selects
  `Info-Debug.plist` (adds `NSAllowsLocalNetworking`), Release uses `Info.plist` with no
  ATS exceptions.
- [x] **Upload queue parity (`AssessmentUploadWorker` equivalent)** — the queue state machine
  and `UploadQueueProcessor` live in `commonMain`; Swift `UploadBackgroundScheduler`
  registers a `BGProcessingTaskRequest` (`requiresNetworkConnectivity`) under
  `org.charged-proton.secondopinion.upload-queue` and drives the shared processor through
  `IosAppGraph.resumePendingUploads`/`hasPendingUploads`/`cancelPendingUploads` (backed by
  a new `UploadQueueStore.pending()` query). Pending rows are re-driven at launch and on
  foregrounding; backgrounding with pending work submits a BG task, which re-submits itself
  if work remains and cancels cleanly on expiration (rows stay pending). The five bounded
  exponential-backoff attempts are unchanged. **Note**: BGTaskScheduler timing is
  opportunistic — iOS decides when queued work runs, unlike WorkManager's constraints — so
  launch/foreground resume is the primary drive. A background `URLSession` with an on-disk
  multipart body (upload surviving suspension mid-transfer) remains a follow-up; today a
  suspended upload retries from scratch on the next drive.
- [x] **Status polling parity** — verified: shared `BackendDtos.kt` maps both `filtering`
  and `extracting` to `PipelineStage.EXTRACTING` and the iOS `AssessmentView` switch
  renders all five stages with a default fallback, so the backend's FILTERING→ASSESSING
  skip needs no iOS work.
- [ ] **End-to-end verification** — real recording on an iOS device/simulator against
  production, confirming upload → `stage_done` events (`.gcp/read-worker-logs.sh`) →
  assessment rendering, mirroring the Android verification.

### 3. CI/CD pipeline (`.github/workflows/ios.yml`)

- [x] **Fastlane setup** (`mobile/ios/fastlane/`) — fastlane 2.230 pinned via
  `mobile/ios/Gemfile{,.lock}`. Lanes: `test` (shared-module Kotlin host tests via Gradle,
  then an unsigned Debug simulator compile — built by target with an explicit SDK because
  gym's destination discovery is flaky when simulator runtimes lag Xcode; SYMROOT/OBJROOT
  must be absolute or SPM resource bundles scatter) and `build_adhoc` (Match-signed Release
  archive → Ad Hoc IPA → Crashlytics dSYM upload). `release` (App Store Connect) is
  deferred until the App-Distribution-only phase ends. `test` verified green locally.
- [x] **Code signing** — Match chosen (private certs repo, `storage_mode: git`,
  `readonly` in CI): `fastlane/Matchfile` reads `MATCH_GIT_URL`/`MATCH_PASSWORD` from env
  and `build_adhoc` passes `MATCH_GIT_TOKEN` as basic auth, so no credentials are
  committed. Blocked on the Apple Developer Program membership (§5) before the certs repo
  can be populated (`match adhoc`); tester UDIDs go into the Ad Hoc profile via
  `match adhoc --force_for_new_devices`.
- [x] **Workflow structure** — `.github/workflows/ios.yml` mirrors `mobile.yml`: triggers
  on `mobile/ios/**` + `mobile/shared/**` (+ Gradle root files); jobs `test` →
  `build-adhoc` → `distribute`. `test` runs on every push/PR (`macos-latest`,
  `latest-stable` Xcode via `maxim-lobanov/setup-xcode` — the macos-26 image ships only
  Xcode 26.x, so hard pins rot; JDK 21 + Gradle caching for the Kotlin/Native framework
  build, Ruby 3.3 with bundler cache, SPM checkouts cached on `Package.resolved`).
  `build-adhoc`/`distribute` run on main pushes only and are gated on the repo variable
  `IOS_SIGNING_READY=true` so the pipeline stays green until signing secrets exist.
- [x] **Artifacts** — `build-adhoc` uploads the IPA (retention 3 days,
  `if-no-files-found: error`); `distribute` downloads it, matching the Android APK pattern.

### 4. Firebase App Distribution

- [x] **Distribute job** — implemented in `ios.yml` (gated with `build-adhoc` on
  `IOS_SIGNING_READY`): downloads the IPA artifact, authenticates with
  `google-github-actions/auth@v3` against the existing WIF pool
  (`projects/352579493765/.../providers/github-oidc`, service account
  `github-ftl-ci@pharmacy-opinion-3.iam.gserviceaccount.com`), then
  `npx firebase-tools@14 appdistribution:distribute <ipa> --app "$FIREBASE_APP_ID_IOS"
  --groups internal-testers` with the commit message + SHA as release notes, exactly as the
  Android `distribute` job does. No new service account or JSON key needed. Unexercised
  until signing lands.
- [ ] **Tester onboarding** — testers install the App Distribution profile on iOS; for Ad Hoc
  builds, register their UDIDs in the Apple Developer portal and regenerate the profile
  (Match `match adhoc --force_for_new_devices`).

### 5. Secret management (GitHub Actions secrets)

- [ ] Document and create in the repo settings (names, not values, tracked here). The
  `build-adhoc` job consumes `APPLE_TEAM_ID`, `MATCH_GIT_URL`, `MATCH_GIT_TOKEN`, and
  `MATCH_PASSWORD`; set the repo **variable** `IOS_SIGNING_READY=true` once they exist to
  un-gate `build-adhoc` + `distribute`:
  | Secret | Purpose |
  |---|---|
  | `APPLE_TEAM_ID` | Apple Developer team ID — Appfile/Match |
  | `MATCH_GIT_URL` | Private certificates repo URL (Match over git) |
  | `MATCH_PASSWORD` | Encryption passphrase for the Match certificates repo |
  | `MATCH_GIT_TOKEN` | Read access to the private certificates repo |
  | `APP_STORE_CONNECT_KEY` | App Store Connect API key `.p8` (base64) — Fastlane auth for signing/profile management and future TestFlight |
  | `APP_STORE_CONNECT_KEY_ID` / `APP_STORE_CONNECT_ISSUER_ID` | Companion identifiers for the API key |
  | `IOS_GOOGLE_SERVICE_INFO_PLIST` | Base64 plist, only if the team opts not to commit it |
  - `FIREBASE_APP_ID_IOS` is a plain env var in `ios.yml` (not secret), mirroring Android.
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
