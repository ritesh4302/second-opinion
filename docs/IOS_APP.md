# Second Opinion — iOS App Documentation

> **Purpose:** Everything a developer or coding agent needs to understand, navigate, and extend
> the iOS app: architecture, file layout, data flow, conventions, and build/CI instructions.
> The iOS app reuses the shared KMP modules documented in `docs/ANDROID_APP.md` — read that
> first for the domain/data/presentation layering; this document covers only what differs
> on iOS. For product context see `docs/PROJECT_DOCUMENTATION.md`.

**Last updated:** 2026-08-31
**Status:** SwiftUI app consuming the shared KMP modules as a single `SharedKit.framework`;
full login → legal consent → record → upload → assessment → decision → history flow wired to
the real backend through the same shared ViewModels/repositories as Android; Firebase
email/password auth (Google Sign-In deferred — button hidden); consent-gated Firebase
Analytics/Crashlytics; durable SQLDelight upload queue re-driven at launch/foreground with a
best-effort `BGTaskScheduler` catch-up; CI green (test lane), signed distribution gated on
Apple Developer Program membership (`docs/TODO.md` iOS §5).

---

## 1. Architectural Direction

- **Maximum reuse of shared KMP code**: all ViewModels, use cases, repositories, the Ktor
  backend client, SQLDelight persistence, and the upload-queue state machine come from
  `:shared:{domain,data,presentation}` — identical bytes to Android. Swift contributes only
  SwiftUI views, Firebase SDK glue, and OS scheduling.
- **SwiftUI + shared MVVM/UDF**: SwiftUI screens observe the same `StateFlow<UiState>`
  contracts as Compose does, via a small `ViewModelObserver` bridge; user actions call the
  same ViewModel event methods.
- **No Koin on iOS**: a manual composition root (`IosAppGraph`, Kotlin `iosMain`) mirrors
  Android's `di/AppModule.kt` one-for-one, keeping Koin types out of the Swift surface.
- **Ports get iOS actuals**: `AudioRecorder`/`AudioPlayer`/file/token/legal-store ports are
  implemented in `:shared:data` `iosMain` (AVFoundation, NSUserDefaults, NativeSqliteDriver);
  Firebase Auth is implemented in Swift behind a Kotlin bridge interface.

## 2. Tech Stack

| Concern | Choice | Notes |
|---|---|---|
| UI | SwiftUI (iOS 17+) | `NavigationStack` + `TabView`; no UIKit screens |
| Shared logic | KMP `SharedKit.framework` | Umbrella framework exported from `:shared:presentation` (re-exports domain + data) |
| Swift ↔ Kotlin state | `ViewModelObserver` + `FlowWatcher` | Republishes `StateFlow<UiState>` into `@Published`; deinit clears the ViewModel scope |
| DI | `IosAppGraph` (Kotlin, `iosMain`) | Manual composition root mirroring Android's Koin `appModule` |
| Auth | FirebaseAuth (SPM) behind `IosAuthBridge` | Email/password + reset; Google Sign-In deferred (`supportsGoogleSignIn=false` hides the button) |
| Telemetry | FirebaseAnalytics + FirebaseCrashlytics (SPM) | Deny-by-default in Info.plist; consent-gated like Android (§6) |
| Networking | Ktor Client (shared) | Darwin engine on iOS; same `createBackendApi` + bearer-token plugin as Android |
| Local storage | SQLDelight (shared) via `NativeSqliteDriver` | Same owner-scoped case/assessment/upload-queue schema as Android |
| Audio capture | `AVAudioRecorder` | 16 kHz mono AAC directly to `.m4a` (no separate encode step); **no VAD trimming yet** (follow-up) |
| Audio playback | `AVAudioPlayer` | Implements the shared `AudioPlayer` port |
| Background uploads | `BGTaskScheduler` (`BGProcessingTask`) | Best-effort catch-up; launch/foreground resume is the primary drive (§5.3) |
| Dependencies | Swift Package Manager | firebase-ios-sdk 12.x (up-to-next-major); pinned via `Package.resolved` |
| Build config | `.xcconfig` per configuration | `BACKEND_BASE_URL` + Info.plist selection (§9) |
| Automation | fastlane (Ruby 3.3, `.ruby-version`) | `test` and `build_adhoc` lanes (§9.3) |
| Min iOS | 17.0 | `IPHONEOS_DEPLOYMENT_TARGET` in the pbxproj |

## 3. Module & File Map

The Xcode project lives in `mobile/ios/`; shared Kotlin `iosMain` sources live in
`mobile/shared/`. All ViewModels and business logic are in the shared modules — see
`docs/ANDROID_APP.md` §3/§7 for that inventory; it applies verbatim to iOS.

```
mobile/ios/
├── SecondOpinion.xcodeproj             # pbxproj + shared scheme + SPM Package.resolved
├── Config/
│   ├── Debug.xcconfig                  # BACKEND_BASE_URL=http://127.0.0.1:8000, Info-Debug.plist
│   └── Release.xcconfig                # BACKEND_BASE_URL=<Cloud Run URL>, Info.plist
├── SecondOpinion/
│   ├── SecondOpinionApp.swift          # @main: FirebaseApp.configure → IosAppGraph → BG scheduler
│   ├── GoogleService-Info.plist        # Firebase iOS app config (committed, like Android)
│   ├── Info.plist                      # Release: mic usage, BGTask ids, telemetry off, BackendBaseURL
│   ├── Info-Debug.plist                # + NSAllowsLocalNetworking ATS exception (dev stack only)
│   ├── Screens/
│   │   ├── RootView.swift              # auth gate → legal gate → tabs; telemetry toggles
│   │   ├── LoginView.swift             # email/password sign-in/sign-up/reset (Google hidden)
│   │   ├── LegalConsentView.swift      # versioned legal acceptance screen
│   │   ├── RecordView.swift            # consent dialog, Speak/Stop, mic permission, → assessment
│   │   ├── AssessmentView.swift        # pipeline progress → result → pharmacist decision
│   │   └── HistoryView.swift           # case list, play/stop, delete confirm, → assessment
│   └── Support/
│       ├── AppConfig.swift             # reads BackendBaseURL from Info.plist
│       ├── ViewModelObserver.swift     # StateFlow → @Published bridge; deinit clears the VM
│       ├── FirebaseAuthBridge.swift    # IosAuthBridge impl over FirebaseAuth (≈ FirebaseAuthClient)
│       ├── TelemetryController.swift   # consent gate for Analytics/Crashlytics (≈ AppTelemetry)
│       └── UploadBackgroundScheduler.swift  # BGTaskScheduler shim (≈ WorkManager scheduling)
├── fastlane/                           # Appfile, Matchfile (env-driven), Fastfile (test, build_adhoc)
├── Gemfile / Gemfile.lock / .ruby-version  # fastlane on Ruby 3.3
└── build/                              # untracked: xcodebuild + fastlane output

mobile/shared/*/src/iosMain/            # iOS actuals for the shared ports
├── data/.../auth/IosAuthBridge.kt          # callback bridge interface Swift implements
├── data/.../auth/BridgedAuthClient.kt      # adapts the bridge to the AuthClient port
├── data/.../auth/UserDefaultsAuthTokenStore.kt
├── data/.../legal/UserDefaultsLegalAcceptanceStore.kt
├── data/.../local/IosDatabaseFactory.kt    # SQLDelight NativeSqliteDriver
├── data/.../platform/IosAudioFileReader.kt / IosAudioFileDeleter.kt
├── data/.../player/AvAudioPlayerAudioPlayer.kt
├── data/.../recorder/AvAudioRecorder.kt
├── data/.../remote/BackendApiFactory.kt    # Darwin-engine Ktor client factory
└── presentation/.../ios/
    ├── IosAppGraph.kt                  # composition root + ViewModel factories
    ├── InProcessAssessmentScheduler.kt # AssessmentWorkScheduler impl (coroutines in-process)
    └── FlowWatcher.kt                  # StateFlow collection handle for Swift + clearViewModel
```

## 4. Composition Root & the Swift/Kotlin Boundary

`SecondOpinionApp` builds one `IosAppGraph` for the process lifetime:

1. `FirebaseApp.configure()`; if a FirebaseApp exists, Swift passes a `FirebaseAuthBridge`
   into the graph → `BridgedAuthClient`. Otherwise `authBridge` is nil and the graph falls
   back to `FakeGoogleAuthClient` (pairs with the backend's `SO_AUTH_PROVIDER=fake`) — the
   same presence check Android performs on `google-services.json`.
2. The graph wires the identical object graph as Android's Koin module: SQLDelight
   case/assessment/queue stores (owner-scoped by the signed-in uid), `createBackendApi`
   (bearer token provider + 401 → sign-out), `BackendAssessmentRepository`,
   `UploadQueueProcessor`, `QueuedAssessmentRepository`, and per-screen ViewModel factories
   (`authViewModel()`, `loginViewModel()`, `legalConsentViewModel(userId)`,
   `symptomViewModel()`, `assessmentViewModel(caseId)`, `historyViewModel()`).
3. SwiftUI owns ViewModel lifetimes: each screen creates its ViewModel via the graph inside a
   `ViewModelObserver` `@StateObject`; on deinit the observer closes the `FlowWatcher` handle
   and calls `clearViewModel` to cancel the `viewModelScope`.

Kotlin→Swift state crosses the boundary as `StateFlow` objects observed through
`FlowWatcher`; Swift→Kotlin auth results cross through the callback-based `IosAuthBridge`
(Kotlin suspend functions wrap the callbacks). Error codes are collapsed to the same domain
errors as Android's `FirebaseAuthClient` (wrong password/unknown user/disabled →
`INVALID_CREDENTIALS`, plus `EMAIL_ALREADY_IN_USE`, `WEAK_PASSWORD`, `INVALID_EMAIL`).

## 5. Core Data Flows

### 5.1 Screen flow (mirrors Android's AuthGate → LegalGate → NavHost)

```
RootView
  ├─ AuthState.Unknown   → ProgressView (session restore)
  ├─ signed out          → LoginView (email/password; Google hidden until the SDK is wired)
  └─ signed in           → LegalConsentView until the current legal version is accepted
                           → TabView: Record | History (NavigationStack each)
Record tab: consent dialog → mic permission → Speak/Stop → case created → AssessmentView
History tab: case list → play/stop, delete (confirm), tap → AssessmentView
```

### 5.2 Upload + polling flow (shared code; iOS differences only)

The case & assessment flow is byte-identical to `docs/ANDROID_APP.md` §5.2 — the same
`QueuedAssessmentRepository` → durable SQLDelight queue → `UploadQueueProcessor` →
`BackendAssessmentRepository` (multipart upload → 2 s status polling → assessment fetch →
feedback POST → DPDP delete). iOS-specific pieces:

```
AssessmentViewModel(caseId) init
  → RequestAssessmentUseCase → QueuedAssessmentRepository.requestAssessment(caseId): Flow
      ├─ Queued              SQLDelight queue row + InProcessAssessmentScheduler.enqueue
      │                        (coroutine job; five attempts, exponential backoff from 10 s —
      │                        the same retry budget as Android's AssessmentUploadWorker)
      ├─ InProgress(...)     identical upload/poll/stage mapping as Android
      └─ Completed/Failed    identical
Recording: AVAudioRecorder captures 16 kHz mono AAC straight to Documents/recordings/*.m4a
  (no VAD trim yet); IosAudioFileReader/Deleter handle upload bytes and DPDP erasure.
```

**State-machine parity note:** the backend now moves `FILTERING→ASSESSING` directly
(`EXTRACTING` remains in the enum but is never set — `docs/BACKEND.md` §2.2). The shared
stage mapping already folds both into one UI stage, so both apps render the same progress
sequence with no iOS-specific handling.

### 5.3 Background upload scheduling (WorkManager ↔ BGTaskScheduler)

Unlike WorkManager, in-process coroutine jobs die with the app, and `BGTaskScheduler` offers
no constraint-based execution guarantee — iOS decides when (or whether) a queued
`BGProcessingTask` runs. The durable queue is therefore the source of truth and is re-driven
opportunistically by `UploadBackgroundScheduler`:

```
launch / willEnterForeground → graph.resumePendingUploads (re-enqueue every pending row)
didEnterBackground + pending  → submit BGProcessingTaskRequest
                                (requiresNetworkConnectivity, id
                                 org.charged-proton.secondopinion.upload-queue,
                                 listed in Info.plist BGTaskSchedulerPermittedIdentifiers)
BGProcessingTask granted      → resumePendingUploads → awaitIdle → re-submit if rows remain
task expiration               → scheduler.cancelAll() — rows stay pending for the next drive
```

## 6. Permissions & Privacy

| Concern | Declared | Handling |
|---|---|---|
| Microphone | `NSMicrophoneUsageDescription` (Info.plist) | iOS prompts on first `AVAudioSession` record; RecordView surfaces denial as a status message |
| Background processing | `UIBackgroundModes: processing` + `BGTaskSchedulerPermittedIdentifiers` | §5.3 |
| Local networking (Debug only) | `NSAllowsLocalNetworking` in `Info-Debug.plist` | ATS exception for the docker-compose dev stack; the Release plist deliberately omits it (HTTPS-only) |

**Telemetry consent (DPDP):** `FirebaseAnalyticsCollectionEnabled` /
`FirebaseCrashlyticsCollectionEnabled` are `NO` in Info.plist, so collection is off at
install. `TelemetryController` toggles both at the same gates as Android: off whenever
signed out (RootView), on/off following the versioned legal acceptance (SignedInView).
Disabling also calls `resetAnalyticsData()` and `deleteUnsentReports()`. The PHI-free event
schema (screen views, workflow events, sanitized non-fatals) is not yet emitted from iOS
screens — see `docs/TODO.md` iOS §1.

## 7. Conventions for Developers & Coding Agents

1. **Shared-first:** any logic that is not SwiftUI or an Apple-OS integration belongs in the
   shared modules (`commonMain`, or `iosMain` for platform actuals) — never duplicate shared
   behavior in Swift.
2. **One graph:** all object construction goes through `IosAppGraph`; keep it in lockstep
   with Android's `di/AppModule.kt` when the graph changes.
3. **ViewModel lifetime:** always hold shared ViewModels in a `ViewModelObserver`
   `@StateObject`; never create one outside it (leaks the coroutine scope).
4. **Swift/Kotlin bridges:** new Swift-implemented capabilities follow the `IosAuthBridge`
   pattern — a callback-based Kotlin interface in `iosMain`, adapted to the domain port in
   Kotlin, so error mapping stays in shared code.
5. **Parity:** UI copy, flows, and error handling mirror the Android screens; deviations are
   deliberate decisions recorded in `docs/TODO.md`.
6. **Audio artifacts:** recordings are `.m4a` in the app container (`Documents/recordings`);
   treat them as sensitive health data, delete via the shared erasure flow only.

## 8. Known Gaps (intentional, tracked in docs/TODO.md iOS roadmap)

- No VAD silence trimming (Android uses Silero via sherpa-onnx); the port tolerates
  untrimmed audio.
- Google Sign-In SDK not wired; email/password only (`supportsGoogleSignIn=false`).
- No XCTest target — the Swift layer is verified by compiling for the simulator; all
  unit-testable logic lives in the shared modules' 96 host tests.
- PHI-free telemetry events not yet emitted from iOS screens (§6).
- Signed distribution blocked on Apple Developer Program membership (TODO iOS §5).

## 9. Build, Run, Test

### 9.1 Prerequisites

- Xcode 16.4+ (CI uses latest-stable), JDK 21 (`JAVA_HOME` or `/usr/libexec/java_home`)
  for the Kotlin/Native framework build, Ruby 3.3 for fastlane
  (`brew install ruby@3.3`; `mobile/ios/.ruby-version` pins 3.3.12).

### 9.2 Xcode / xcodebuild

Open `mobile/ios/SecondOpinion.xcodeproj`. A run-script build phase invokes
`./gradlew :shared:presentation:embedAndSignAppleFrameworkForXcode`, which builds the
`SharedKit.framework` slice for the requested configuration/SDK — no manual Gradle step.

```bash
cd mobile/ios
# Unsigned Debug simulator build (what CI runs):
xcodebuild -project SecondOpinion.xcodeproj -target SecondOpinion \
  -sdk iphonesimulator -arch arm64 -configuration Debug \
  CODE_SIGNING_ALLOWED=NO SYMROOT="$PWD/build/sym" OBJROOT="$PWD/build/obj" build
```

Shared-module tests run from `mobile/`: `./gradlew check` (see `docs/ANDROID_APP.md` §9).

**Talking to the dev backend:** Debug builds point at `http://127.0.0.1:8000`
(`Config/Debug.xcconfig` → Info.plist `BackendBaseURL` → `AppConfig.backendBaseURL`); the
simulator shares the host loopback, so the docker-compose stack is reachable directly (no
`adb reverse` equivalent needed). Release builds use the Cloud Run URL
(`Config/Release.xcconfig`).

### 9.3 fastlane

```bash
cd mobile/ios
PATH=/opt/homebrew/opt/ruby@3.3/bin:$PATH bundle install   # once
PATH=/opt/homebrew/opt/ruby@3.3/bin:$PATH bundle exec fastlane test         # host tests + sim build
PATH=/opt/homebrew/opt/ruby@3.3/bin:$PATH bundle exec fastlane build_adhoc  # signed IPA + dSYM upload
```

- `test` — the three shared-module host-test tasks via Gradle, then the unsigned Debug
  simulator compile above.
- `build_adhoc` — CI keychain (`setup_ci`), read-only Match (Ad Hoc profile; refresh tester
  UDIDs with `bundle exec fastlane match adhoc --force_for_new_devices`), Release
  archive/IPA export, then Crashlytics dSYM upload using `upload-symbols` from the
  firebase-ios-sdk SPM checkout. Requires `APPLE_TEAM_ID`, `MATCH_GIT_URL`,
  `MATCH_PASSWORD` (+ `MATCH_GIT_TOKEN` in CI) — see `docs/TODO.md` iOS §5.

### 9.4 CI (`.github/workflows/ios.yml`)

Mirrors the Android `mobile.yml` pipeline on `macos-latest`:

- **test** — every push/PR touching `mobile/ios/**` or `mobile/shared/**`: latest-stable
  Xcode, JDK 21 + Gradle caching, Ruby 3.3 bundler cache, SPM cache keyed on
  `Package.resolved`, then `bundle exec fastlane test`.
- **build-adhoc → distribute** — main pushes only, gated on the repository variable
  `IOS_SIGNING_READY=true` (unset until the Apple Developer membership and Match secrets
  exist). `distribute` uploads the IPA to Firebase App Distribution (`internal-testers`)
  using the same keyless WIF auth as Android (`github-ftl-ci` service account, no key
  files).

## 10. Related Documents

- `docs/ANDROID_APP.md` — shared-module architecture, inventory, and testing strategy (the
  bulk of this app's logic)
- `docs/BACKEND.md` — backend architecture and the API surface the app talks to (§3)
- `docs/PROJECT_DOCUMENTATION.md` — product context, decisions, roadmap
- `docs/TODO.md` — iOS roadmap status (§1–§6)
