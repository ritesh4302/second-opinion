# Second Opinion — Android App Documentation

> **Purpose:** Everything a developer or coding agent needs to understand, navigate, and extend
> the Android app: architecture, module layout, data flow, conventions, and migration plan.
> For product context (problem, solution, decisions D1–D6, roadmap) see
> `docs/PROJECT_DOCUMENTATION.md`.

**Last updated:** 2026-07-24
**Status:** KMM layer-per-module; full case → assessment → decision flow **wired to the real
backend** (Ktor multipart upload → status polling → assessment fetch → feedback POST, verified
end-to-end against the docker-compose stack); on-device VAD silence trimming (Silero via
sherpa-onnx) in the recording pipeline; consent step before recording and recording playback
from history (Phase 1 capture POC complete); Firebase Google Sign-In login gate with
bearer-token backend auth (real Credential Manager → Firebase Auth SDK adapter
`FirebaseAuthClient`, selected at runtime once google-services.json is provisioned;
fake Google adapter otherwise); DPDP flows:
consent flag sent with the upload + per-case deletion from history (backend erasure +
local audio cleanup); cases still in-memory (SQLDelight pending)

---

## 1. Architectural Direction (agreed)

- **KMM (Kotlin Multiplatform)**: business logic, domain models, networking, and persistence
  live in shared multiplatform modules. Android is the first platform; iOS is a future target.
- **Layer-per-module**: domain, data, and presentation are **separate Gradle modules**
  (`:shared:domain`, `:shared:data`, `:shared:presentation`) so layer boundaries are enforced
  by the build system, not just by convention.
- **Jetpack Compose + Material3** for all UI (dynamic color supported). No XML layouts.
- **MVVM with Unidirectional Data Flow (UDF)**: UI observes a single immutable `UiState` from a
  ViewModel; user actions flow up as events, state flows down.
- Platform-specific capabilities (audio recording, permissions) are exposed to shared code via
  `expect`/`actual` declarations or interfaces implemented per platform.

## 2. Tech Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.2.x | |
| UI | Jetpack Compose + Material3 (BOM 2026.02.01) | Dynamic color on Android 12+ |
| Architecture | MVVM + UDF | `StateFlow<UiState>` per screen |
| Shared logic | Kotlin Multiplatform (`:shared:domain`, `:shared:data`, `:shared:presentation`) | Each with commonMain / androidMain (iosMain later) |
| Async | Kotlin Coroutines + Flow | Structured concurrency; no callbacks |
| Networking | Ktor Client | Multiplatform; OkHttp engine on Android |
| Serialization | kotlinx.serialization | JSON payloads to backend |
| DI | Koin | Multiplatform-friendly, low ceremony |
| Local storage | SQLDelight | Multiplatform DB for recordings/metadata queue |
| Audio capture | `AudioRecord` + Silero VAD (sherpa-onnx) + `MediaCodec` | 16 kHz mono PCM → silence trim → AAC/M4A |
| Min/target SDK | 29 / 37 | Android 10+ |
| Build | Gradle (Kotlin DSL) + version catalog (`gradle/libs.versions.toml`) | |

> SQLDelight is **planned** — add it via the version catalog when persistence is implemented,
> not before. Ktor + kotlinx.serialization landed with the backend integration.

### 2.1 Standard Library Selections (KMP-verified)

Selection criteria: works in `commonMain` (true multiplatform), actively maintained on GitHub,
strong community adoption. Verified as of 2026-07.

| Category | Selected | KMP status | Maintenance & community | Alternatives considered |
|---|---|---|---|---|
| Coroutines / async | **kotlinx.coroutines** | Native KMP | JetBrains official, ~13k★, very active | None — de-facto standard |
| ViewModel + Lifecycle | **androidx.lifecycle `lifecycle-viewmodel`** (2.9+) | Stable KMP since 2.9 (May 2025); `ViewModel` + `viewModelScope` usable directly in `commonMain` | Google/AndroidX, official | moko-mvvm (obsolete now that AndroidX is KMP) |
| DI | **Koin** (4.x) | Native KMP + Compose Multiplatform support | ~10k★, commits within days, backed by Kotzilla | kotlin-inject (~1.5k★, slower cadence), Metro (~1.1k★, promising but young), Hilt (Android-only — not KMP) |
| Database | **SQLDelight** | KMP-first since inception | CashApp/Square, ~6.5k★, active | Room 2.7+/3.0 (now stable KMP, Google-backed) — valid choice, but SQLDelight preferred for greenfield KMP; revisit if team prefers DAO/annotation style |
| Networking | **Ktor Client** | Native KMP | JetBrains official, ~13k★, very active | OkHttp/Retrofit (Android-only — used only as Ktor's Android engine) |
| Serialization | **kotlinx.serialization** | Native KMP | JetBrains official, ~5.5k★, active | Moshi/Gson (JVM-only) |
| Date/time | **kotlinx-datetime** | Native KMP | JetBrains official, active | java.time (JVM-only) |
| Key-value prefs | **androidx DataStore** (1.1+) | KMP support | Google/AndroidX, official | multiplatform-settings (~1.2k★) — fine, but DataStore aligns with AndroidX KMP direction |
| Navigation | **androidx Navigation Compose** | KMP artifacts available via JetBrains; we use it Android-side only for now | Google/AndroidX, official | Voyager / Decompose (~2–3k★ each; reconsider only if shared iOS navigation becomes a requirement) |

Notes:
- Prefer AndroidX artifacts wherever they have stable KMP support (ViewModel, Lifecycle,
  DataStore) — largest community, longest support horizon, and smooth Android interop.
- `@HiltViewModel` and other Hilt features do not work in `commonMain`; ViewModel creation is
  wired through Koin.
- All versions go through `gradle/libs.versions.toml`; the table intentionally omits exact
  versions to avoid drift.

## 3. Module Structure

The repository root contains `mobile/` (this Android/KMM project), `backend/` (backend
services — see `docs/BACKEND.md`), and `docs/`. All paths below are relative to `mobile/`.

### 3.1 Current (KMM, layer-per-module)

The KMM migration (steps 1–3 of §10) is done. The entry module is still named `:app`
(rename to `:androidApp` is deferred — §10 step 5). Shared modules use the
`com.android.kotlin.multiplatform.library` plugin with an Android-only target for now.

```
mobile/
├── app/                                # :app — Android entry point
│   └── src/main/java/org/charged_proton/secondopinion/
│       ├── SecondOpinionApp.kt         # Application — starts Koin, registers activity tracker
│       ├── MainActivity.kt             # Sets Compose content, hosts AppNavHost
│       ├── auth/FirebaseAuthClient.kt  # Credential Manager → Firebase Auth adapter
│       ├── auth/CurrentActivityTracker.kt  # resumed-Activity provider for Credential Manager
│       ├── di/AppModule.kt             # Koin module: recorder, repos, use cases, ViewModels
│       ├── queue/                       # WorkManager upload worker + scheduler
│       └── ui/
│           ├── navigation/AppNavHost.kt        # record / history / assessment/{caseId}
│           ├── navigation/AuthGate.kt          # signed out → LoginScreen, signed in → AppNavHost
│           ├── login/LoginScreen.kt            # email/password form + "Sign in with Google" button
│           ├── record/RecordScreen.kt          # consent dialog, Speak/Stop, permission,
│           │                                   #   → assessment/history
│           ├── assessment/AssessmentScreen.kt  # progress → result → pharmacist decision
│           ├── history/HistoryScreen.kt        # case list, play/stop, delete (confirm
│           │                                   #   dialog), tap → assessment
│           └── theme/                  # Material3 theme (Color, Theme, Type)
└── shared/
    ├── domain/                         # :shared:domain — pure Kotlin commonMain
    │   └── .../domain/
    │       ├── auth/AuthClient.kt      # AuthState + AuthClient port (Firebase Google + email/password)
    │       ├── model/                  # Recording, SymptomCase, Assessment,
    │       │                           #   AssessmentProgress, Feedback
    │       ├── platform/               # AudioRecorder, AudioPlayer (port interfaces)
    │       ├── repository/             # CaseRepository, AssessmentRepository (ports)
    │       └── usecase/                # auth + recording + playback + case (incl. delete)
    │                                   #   + assessment + feedback
    ├── data/                           # :shared:data — implements domain ports
    │   ├── src/androidMain/.../data/
    │   │   ├── audio/                  # SileroVadTrimmer (sherpa-onnx), AacM4aEncoder
    │   │   ├── auth/SharedPreferencesAuthTokenStore.kt  # persists the session token
    │   │   ├── local/AndroidDatabaseFactory.kt     # app-private SQLDelight database
    │   │   ├── platform/AndroidAudioFileReader.kt  # reads recording bytes for upload
    │   │   ├── platform/AndroidAudioFileDeleter.kt # removes the local .m4a on case delete
    │   │   ├── player/MediaPlayerAudioPlayer.kt    # MediaPlayer impl of AudioPlayer
    │   │   └── recorder/VadTrimmingAudioRecorder.kt
    │   └── src/commonMain/.../data/
    │       ├── auth/                   # AuthTokenStore port, FakeGoogleAuthClient (dev)
    │       ├── local/                   # SQLDelight case/result/upload-queue persistence
    │       ├── mock/MockAssessmentScenarios.kt  # canned assessments (kept for tests/demo)
    │       ├── platform/                # AudioFileReader + AudioFileDeleter (fun interface ports)
    │       ├── queue/                   # scheduler port + background queue processor
    │       ├── remote/                 # BackendApi (Ktor + bearer token), DTOs + mappers
    │       └── repository/             # SqlDelightCaseRepository, BackendAssessmentRepository,
    │                                   #   in-memory/mock implementations (tests/demo only)
    └── presentation/                   # :shared:presentation — commonMain ViewModels
        └── .../presentation/
            ├── auth/AuthViewModel.kt   # exposes AuthState for the login gate
            ├── login/                  # LoginViewModel + LoginUiState (Google + email/password sign-in)
            ├── symptom/                # SymptomViewModel + SymptomUiState (consent step,
            │                           #   creates case on stop)
            ├── assessment/             # AssessmentViewModel + AssessmentUiState
            └── history/HistoryViewModel.kt      # case list + playback toggle + delete confirmation
```

### 3.2 Target (full)

```
androidApp/                             # :androidApp — Android entry point, thin
└── src/main/.../
    ├── MainActivity.kt                 # Sets Compose content, hosts navigation
    ├── ui/                             # Compose screens, components, theme
    │   ├── symptom/                    # SymptomScreen (binds to SymptomViewModel)
    │   ├── assessment/                 # Assessment result screen (Phase 3)
    │   └── theme/
    └── di/                             # Koin app definition — wires all modules together

shared/
├── domain/                             # :shared:domain — pure Kotlin KMP, zero dependencies
│   └── src/commonMain/kotlin/.../domain/
│       ├── model/                      # Recording, SymptomReport, Assessment, RedFlag
│       ├── repository/                 # Repository *interfaces* (ports)
│       ├── platform/                   # Platform *interfaces* (ports): AudioRecorder, FileStore
│       └── usecase/                    # RecordSymptomsUseCase, UploadRecordingUseCase, ...
│
├── data/                               # :shared:data — implements domain ports
│   └── src/
│       ├── commonMain/kotlin/.../data/
│       │   ├── repository/             # RecordingRepositoryImpl, AssessmentRepositoryImpl
│       │   ├── remote/                 # Ktor API client, DTOs, mappers
│       │   └── local/                  # SQLDelight queries (recording queue, results cache)
│       ├── androidMain/kotlin/.../data/
│       │   └── platform/               # VadTrimmingAudioRecorder, AndroidFileStore
│       └── iosMain/                    # future
│
└── presentation/                       # :shared:presentation — ViewModels, UiState/UiEvent
    └── src/commonMain/kotlin/.../presentation/
        ├── symptom/                    # SymptomViewModel, SymptomUiState, SymptomUiEvent
        └── assessment/                 # AssessmentViewModel, ... (Phase 3)
```

**Rule of thumb:** if code doesn't need an Android API, it belongs in a `shared` module's
`commonMain`. UI (Compose) and OS integrations (permissions, AudioRecord/MediaCodec) stay in
`androidApp` / `androidMain` source sets.

## 4. Layered Architecture & Module Dependency Graph

```
┌─ :androidApp ───────────────────────────────────────────────┐
│  Compose UI (screens, components) + Koin wiring              │
│    ↓ observes StateFlow<UiState>      ↑ sends UiEvent        │
├─ :shared:presentation ──────────────────────────────────────┤
│  ViewModel: reduces events → state; calls use cases          │
├─ :shared:domain ────────────────────────────────────────────┤
│  Use cases + pure models + ports (repository/platform ifaces)│
├─ :shared:data ──────────────────────────────────────────────┤
│  Port implementations: remote (Ktor), local (SQLDelight),    │
│  platform (AudioRecord + VAD in androidMain)                 │
└──────────────────────────────────────────────────────────────┘
```

Allowed module dependencies (enforced by Gradle — a module cannot import what it does not
declare):

| Module | Depends on |
|---|---|
| `:shared:domain` | nothing (pure Kotlin + coroutines only) |
| `:shared:data` | `:shared:domain` |
| `:shared:presentation` | `:shared:domain` |
| `:androidApp` | `:shared:presentation`, `:shared:domain`, `:shared:data` (DI wiring only) |

Key rules:
- Domain owns all interfaces (repositories, platform ports); data implements them. Domain never
  sees implementations — this is dependency inversion, enforced at the module level.
- `:shared:presentation` and `:shared:data` never depend on each other; they meet only through
  domain interfaces, bound together by Koin in `:androidApp`.
- `:androidApp` references `:shared:data` **only** to register implementations in DI — never
  call data classes directly from UI.

## 5. Core Data Flows

### 5.1 Symptom recording flow (implemented today, target shape)

```
Pharmacist taps "Speak"
  → SymptomViewModel.onRecordRequested → UiState(awaitingConsent = true)
  → patient-consent AlertDialog (tap-to-confirm; decline → CONSENT_DECLINED status, no recording)
  → consent confirmed → UI checks RECORD_AUDIO permission (Android runtime permission, androidApp)
      ├─ not granted → system permission dialog → denied → status message
      └─ granted ↓
  → SymptomViewModel.onStartRecording
  → StartRecordingUseCase → AudioRecorder.start()   (actual: AudioRecord, 16 kHz mono PCM)
  → UiState(isRecording = true)
Pharmacist taps "Stop"
  → suspend AudioRecorder.stop()                    (VAD trim → AAC → app-private recordings dir)
  → Recording(file, durationMs, createdAt, consentConfirmed)   (consent dialog outcome)
  → RecordingRepository.save(recording)             (SQLDelight metadata + file reference)
  → UiState(isRecording = false, lastRecording = ...)

History screen: Play on a case → PlayRecordingUseCase → AudioPlayer.play(filePath)
  (Android: MediaPlayer; one playback at a time; Stop or completion clears playingCaseId)
```

### 5.2 Case & assessment flow (wired to the real backend)

```
Recording saved (Stop)
  → CreateCaseUseCase → CaseRepository.createCase → SymptomCase(RECORDED)
      (case id is a client-generated UUID — doubles as the backend recording id)
  → UiState.lastCaseId set → "Get assessment" button → navigate assessment/{caseId}
AssessmentViewModel(caseId) init
  → RequestAssessmentUseCase → QueuedAssessmentRepository.requestAssessment(caseId): Flow
      ├─ Queued                  SQLDelight queue + unique network-constrained WorkManager job
      ├─ InProgress(UPLOADING)   POST /v1/recordings (multipart: id, duration_ms, locale, audio)
      ├─ InProgress(...)         GET /v1/recordings/{id} polled every 2 s (≤ 8 min per attempt);
      │                            backend status → PipelineStage (diarizing→DIARIZING,
      │                            transcribing/translating→TRANSCRIBING, extracting→EXTRACTING,
      │                            assessing→ASSESSING); case status mirrors the progress
      └─ Completed(Assessment)   GET /v1/recordings/{id}/assessment → DTO → domain mapping
         or retry wait           transient failure → exponential backoff, at most five attempts
         or Failed(reason)       permanent pipeline failure / exhausted retry budget
  → Assessment screen renders result ("Refer to a doctor" escalation banner first when red
      flags exist; guidance items with prescription=true carry a "Prescription drug" badge)
  → Pharmacist accepts / rejects / overrides → SubmitFeedbackUseCase
      → POST /v1/assessments/{id}/feedback (decision lowercased, optional note)
HistoryScreen
  → ObserveCasesUseCase → case list (newest first) → tap → assessment/{caseId}
      (already-assessed case: Flow emits Completed immediately, decision preloaded)
  → Delete on a case → confirmation dialog → DeleteCaseUseCase
      → DELETE /v1/recordings/{id} (backend erasure cascade; 404 tolerated —
        never-uploaded cases still erase locally) → local .m4a deleted → case removed
        (network failure keeps the case so erasure can be retried)
```

Cases, assessment results, feedback, and upload progress are owner-scoped in SQLDelight and
survive process death. WorkManager resumes queued work after process/device restart; reopening a
terminal failure and tapping Retry creates a fresh unique work request without duplicating the
backend recording ID.

### 5.3 UiState contract (per screen)

Each screen owns exactly one `UiState` data class and one `UiEvent` sealed interface. Example:

```kotlin
data class SymptomUiState(
    val isRecording: Boolean = false,
    val statusMessage: StringResourceKey = StringResourceKey.PromptDescribeSymptoms,
    val lastRecording: Recording? = null,
)

sealed interface SymptomUiEvent {
    data object StartRecording : SymptomUiEvent
    data object StopRecording : SymptomUiEvent
    data object PermissionDenied : SymptomUiEvent
}
```

## 6. Permissions

| Permission | Declared | Handling |
|---|---|---|
| `RECORD_AUDIO` | `AndroidManifest.xml` | Dangerous permission — requested at runtime via `rememberLauncherForActivityResult(RequestPermission())` before first recording |
| `INTERNET` | `AndroidManifest.xml` | Install-time permission for the backend upload/polling; debug builds additionally allow cleartext HTTP to the dev stack via `app/src/debug/AndroidManifest.xml` (`usesCleartextTraffic`) |

Permission checks are UI-layer (Android) concerns; shared code never asks for permissions — it
receives a ready-to-use `AudioRecorder` or a failure.

## 7. Current Implementation Inventory

| File | Responsibility |
|---|---|
| `mobile/app/.../SecondOpinionApp.kt` | `Application`; starts Koin with `appModule`, registers `CurrentActivityTracker` as activity-lifecycle callbacks |
| `mobile/app/.../auth/FirebaseAuthClient.kt` | Production `AuthClient`: Credential Manager account picker (`GetGoogleIdOption` with the `default_web_client_id` generated by the google-services plugin) → `GoogleIdTokenCredential` → `FirebaseAuth.signInWithCredential`, plus email/password sign-in/sign-up and enumeration-safe `sendPasswordResetEmail` → Firebase ID token as the bearer; session persisted by Firebase Auth itself; dismissed picker → `SignInCancelledException`; sign-out also clears Credential Manager state |
| `mobile/app/.../auth/CurrentActivityTracker.kt` | `ActivityLifecycleCallbacks` holding a `WeakReference` to the resumed Activity — Credential Manager needs an Activity (not the application context) to show its UI |
| `mobile/app/.../di/AppModule.kt` | Koin bindings: recorder + player ports, auth (`SharedPreferencesAuthTokenStore`; `AuthClient` = `FirebaseAuthClient` when a `FirebaseApp` is initialised, i.e. google-services.json is present, else `FakeGoogleAuthClient`), `InMemoryCaseRepository`, `BackendApi` (base URL from `BuildConfig.BACKEND_BASE_URL`, token from `AuthClient`, 401 → sign-out) + `BackendAssessmentRepository`, use-case factories, five ViewModels (`AssessmentViewModel` takes `caseId` via `parametersOf`) |
| `mobile/app/.../MainActivity.kt` | Sets Compose content; hosts `AuthGate` inside `Scaffold` |
| `mobile/app/.../ui/navigation/AuthGate.kt` | Login gate: observes `AuthViewModel.authState` — signed out → `LoginScreen`, signed in → `AppNavHost` |
| `mobile/app/.../ui/login/LoginScreen.kt` | Sign-in gate: email/password form (sign in ↔ create account toggle) plus a "Sign in with Google" button that launches the account picker; localized error message on failure |
| `mobile/app/.../ui/navigation/AppNavHost.kt` | Navigation Compose graph: `record` (start), `history`, `assessment/{caseId}` |
| `mobile/app/.../ui/record/RecordScreen.kt` | Patient-consent `AlertDialog` (tap-to-confirm before recording), Speak/Stop, `RECORD_AUDIO` permission, "Get assessment" (when case created), "View history" |
| `mobile/app/.../ui/assessment/AssessmentScreen.kt` | Pipeline progress spinner, assessment result (referral banner → summary → conditions → medicine guidance with prescription badges → disclaimer), accept/reject/override decision bar |
| `mobile/app/.../ui/history/HistoryScreen.kt` | Case list (timestamp + status), per-case Play/Stop recording playback and Delete (confirmation `AlertDialog` before erasure), tap → assessment; empty state |
| `mobile/app/.../ui/theme/*` | Material3 theme, dynamic color (Android 12+), dark/light |
| `mobile/app/src/main/res/values/strings.xml` | UI strings (login/record/consent/assessment/history/playback/deletion/decision/case-status) |
| `mobile/app/src/main/AndroidManifest.xml` | `RECORD_AUDIO`, Application class, single launcher activity |
| `mobile/shared/domain/.../auth/AuthClient.kt` | `AuthClient` port (Google/email sign-in, email sign-up, password reset, token, sign-out + `authState`); production flow: Credential Manager (Google) or email/password → Firebase Auth SDK → Firebase ID token as the bearer; reset requests use normalized local validation and generic success messaging to avoid account enumeration |
| `mobile/shared/domain/.../model/*` | `Recording` (incl. `consentConfirmed`), `SymptomCase` + `CaseStatus`, `Assessment` (+`ConditionHypothesis`, `RedFlag`, `OtcAdvice`), `AssessmentProgress` + `PipelineStage`, `Feedback` + `PharmacistDecision` |
| `mobile/shared/domain/.../platform/AudioRecorder.kt` | Port interface: `start()`, `suspend stop(): Recording?` (post-processing happens in stop), `release()`, `isRecording` |
| `mobile/shared/domain/.../platform/AudioPlayer.kt` | Port interface: `play(filePath, onCompleted)` (throws if playback cannot start), `stop()`; one playback at a time |
| `mobile/shared/domain/.../repository/*` | Ports: `CaseRepository` (observe/create/get/updateStatus/delete), `AssessmentRepository` (requestAssessment `Flow`, getAssessment, submit/getFeedback, deleteCase) |
| `mobile/shared/domain/.../usecase/*` | Auth (ObserveAuthState/SignIn), recording (Start/Stop/Release), playback (Play/Stop), case (Create/Observe/Get/Delete), assessment (Request/Get), feedback (Submit/Get) use cases |
| `mobile/shared/data/.../recorder/VadTrimmingAudioRecorder.kt` | `AudioRecord` impl of the port (androidMain): captures 16 kHz mono PCM on a thread; `stop()` trims silence via VAD and writes `symptom_recording_<ts>.m4a` to `cacheDir` (falls back to the full buffer when no speech detected) |
| `mobile/shared/data/.../audio/SileroVadTrimmer.kt` | Silero VAD via sherpa-onnx: finds the padded speech range; model `silero_vad.onnx` loaded from app assets |
| `mobile/shared/data/.../audio/AacM4aEncoder.kt` | Mono 16-bit PCM → AAC-LC/.m4a via `MediaCodec` + `MediaMuxer` |
| `mobile/shared/data/.../player/MediaPlayerAudioPlayer.kt` | `MediaPlayer` impl of the `AudioPlayer` port (androidMain): plays the cached `.m4a`, completion listener releases and fires `onCompleted` |
| `mobile/shared/data/.../remote/BackendApi.kt` | Ktor client (`expectSuccess`, kotlinx JSON) + the five backend calls: multipart upload (incl. `consent_confirmed`), recording status, assessment fetch, feedback POST, recording DELETE; `BackendAuth` plugin attaches `Authorization: Bearer <token>` from a `tokenProvider` and fires `onUnauthorized` on 401; `createBackendApi(baseUrl, tokenProvider, onUnauthorized)` used by DI (OkHttp engine on Android) |
| `mobile/shared/data/.../remote/BackendDtos.kt` | `@Serializable` DTOs mirroring `RecordingOut`/`AssessmentOut`/`FeedbackIn` + DTO → domain mappers |
| `mobile/shared/data/.../repository/BackendAssessmentRepository.kt` | Real `AssessmentRepository`: upload → poll (2 s interval, 15 min cap) → assessment fetch as a cold `Flow<AssessmentProgress>`; maps backend statuses to `PipelineStage`; mirrors progress into `CaseRepository`; caches assessments + feedback in memory; `deleteCase` = backend DELETE (404 tolerated) → local audio file + case removal (network failure keeps the case) |
| `mobile/shared/data/.../platform/AudioFileReader.kt` (+ `AndroidAudioFileReader`) | `fun interface` port reading recording bytes for upload; Android impl reads the `.m4a` from cache |
| `mobile/shared/data/.../platform/AudioFileDeleter.kt` (+ `AndroidAudioFileDeleter`) | `fun interface` port deleting the local recording file on case erasure |
| `mobile/shared/data/.../repository/InMemoryCaseRepository.kt` | `StateFlow`-backed case store (newest first); UUID case ids (shared with the backend) |
| `mobile/shared/data/.../repository/MockAssessmentRepository.kt` | Mock kept for tests/demo: simulates pipeline with staged delays, rotates canned scenarios (no longer wired in `AppModule`) |
| `mobile/shared/data/.../mock/MockAssessmentScenarios.kt` | Three canned assessments: viral URI, gastroenteritis (incl. a prescription-labeled medicine), red-flag chest pain (no OTC, urgent referral) |
| `mobile/shared/data/.../auth/AuthTokenStore.kt` (+ `SharedPreferencesAuthTokenStore`) | Token persistence port; Android impl stores the session token in app-private SharedPreferences |
| `mobile/shared/data/.../auth/FakeGoogleAuthClient.kt` | Dev `AuthClient`: Google sign-in mints a stable dev identity, email sign-in/sign-up an identity derived from the given email → `fake:<uid>:<email>:<name>` bearer token (accepted by the backend's `SO_AUTH_PROVIDER=fake` verifier), standing in for the production Firebase ID token; restores the session from the token store on start |
| `mobile/shared/presentation/.../auth/AuthViewModel.kt` | Exposes `AuthClient.authState` for the app-level login gate |
| `mobile/shared/presentation/.../login/*` | `LoginViewModel` (Google sign-in action + email/password form with sign-in ↔ sign-up toggle, `isSubmitting` re-entry guard, error mapping), `LoginUiState` + `LoginError` |
| `mobile/shared/presentation/.../symptom/*` | `SymptomViewModel` (consent step via `awaitingConsent`; the dialog outcome sets `consentConfirmed` on the stopped recording; creates case on stop → `lastCaseId`), `SymptomUiState` + `SymptomStatus` (incl. `CONSENT_DECLINED`) |
| `mobile/shared/presentation/.../assessment/*` | `AssessmentViewModel` (streams progress, loads prior decision, submits feedback), `AssessmentUiState` |
| `mobile/shared/presentation/.../history/HistoryViewModel.kt` | `stateIn`-shared case list + `playingCaseId` playback toggle + delete flow (`confirmingDeleteCaseId` drives the confirmation dialog; confirm stops playback if needed → `DeleteCaseUseCase`) (`HistoryUiState`) |

Known gaps (intentional): no persistence (SQLDelight — cases and cached assessments vanish on
process death), builds run with the fake Google adapter until the Firebase project
(google-services.json) is provisioned — the google-services plugin and `FirebaseAuthClient`
are only activated when that file exists (no sign-out UI yet; a backend 401 drops the
session), single hardcoded locale (`hi-IN`) in the upload.

## 8. Conventions for Developers & Coding Agents

1. **Package root:** `org.charged_proton.secondopinion`.
2. **Dependencies:** always add via `gradle/libs.versions.toml` version catalog; never hardcode
   versions in build files.
3. **UI:** Compose + Material3 only. Use `MaterialTheme.colorScheme` / `typography` — never
   hardcode colors or text styles. All user-facing text goes in `strings.xml`.
4. **State:** composables are stateless where possible; hoist state. One `UiState` per screen,
   exposed as `StateFlow`. No `LiveData`, no RxJava.
5. **Async:** coroutines + Flow only. Use cases are `suspend` functions or return `Flow`.
6. **Layering:** respect the module dependency graph (§4). Domain never imports Android APIs;
   presentation and data never depend on each other; new inter-module dependencies require a
   deliberate decision, not a convenience import.
7. **Audio artifacts:** recordings are `.m4a` (AAC) in app cache; never store audio in external
   storage; treat recordings as sensitive health data (see D-privacy notes in project doc §9).
8. **Errors:** repositories return `Result<T>` (or sealed results); ViewModels map failures to
   user-readable status messages — never crash on recorder/network failure.
9. **Previews:** provide `@Preview` composables for new screens using `SecondOpinionTheme`.

## 9. Build, Run, Test

All Gradle commands run from the `mobile/` directory:

```bash
cd mobile
./gradlew :app:compileDebugKotlin     # compile check
./gradlew :app:installDebug           # build + install on connected device/emulator
./gradlew check                       # all unit tests (shared modules run as host tests)
./gradlew :shared:domain:testAndroidHostTest  # single shared module's tests
./gradlew :app:connectedDebugAndroidTest  # instrumented + Compose UI tests (device required)
```

- SDK location: `mobile/local.properties` (`sdk.dir`). Known AVD: `Pixel_10_Pro`.
- Launch after install:
  `adb shell monkey -p org.charged_proton.secondopinion -c android.intent.category.LAUNCHER 1`

**Talking to the dev backend:** debug builds point at `http://127.0.0.1:8000`
(`BACKEND_BASE_URL` in `app/build.gradle.kts`). Bridge the device/emulator to the
docker-compose stack with `adb reverse tcp:8000 tcp:8000` after boot. Loopback + reverse is
used instead of the classic `10.0.2.2` host alias because the current emulator's WiFi stack
does not route app traffic through it reliably (shell traffic works, app sockets time out).

**Testing strategy (implemented):** each shared module owns its tests in `commonTest`
(96 tests total, run on the JVM via the AGP-KMP `withHostTest {}` DSL):
- `:shared:domain` `commonTest` — all 15 use cases against hand-written fakes of the five
  ports (`testutil/Fakes.kt`); verifies `Result` wrapping and delegation (24 tests)
- `:shared:data` `commonTest` — `InMemoryCaseRepository` (Turbine on `observeCases`,
  delete), `MockAssessmentRepository` (full stage sequence, status transitions, cached
  replay, scenario rotation, feedback round-trip, delete), `BackendAssessmentRepository`
  against a scripted Ktor `MockEngine` (upload/poll/mapping happy path, consent field in
  the upload body, pipeline failure stage, unknown case, poll timeout, network error,
  404 → null, feedback body, delete: backend + local erasure / 404 tolerated / network
  failure keeps the case), `FakeGoogleAuthClient` (session restore, sign-in flow,
  sign-out), and the `BackendAuth` plugin (bearer header on/off,
  401 → `onUnauthorized`) (34 tests)
- `:shared:presentation` `commonTest` — the four ViewModels with fakes,
  `Dispatchers.setMain(UnconfinedTestDispatcher())`, and Turbine for `StateFlow`
  transitions; a `MutableSharedFlow`-driven fake steps the assessment pipeline; includes
  a gated-suspend fake proving double-stop re-entry is ignored, the consent step (incl.
  the `consentConfirmed` flag on the stopped recording and its reset per flow),
  history playback toggling/completion/error handling, the delete confirmation flow
  (request/dismiss/confirm, playback stopped on delete), and the Google sign-in
  flow with error mapping (38 tests)
- `:app` `androidTest` — Compose UI tests (`ui-test-junit4`) for all four screens
  (27 tests, device/emulator required): login (button shown, tap → signed in,
  failure error), record (consent dialog confirm/decline,
  Speak/Stop toggle, saved-case → "Get assessment" navigation, permission-denied
  messaging, history navigation), assessment (pipeline progress, failure reason,
  referral banner, prescription-drug badge, accept/reject/override decision bar,
  decision recording/preloading), and history (empty state, status labels, playback
  Play/Stop toggle, tap-to-open, delete: confirmation dialog / confirm removes /
  cancel keeps). Screens get ViewModels built on androidTest fakes —
  no microphone, backend, or Koin graph involved; `RECORD_AUDIO` granted via
  `UiAutomation` in `@Before`
- Convention: hand-written fakes over mocking libraries (pure Kotlin, KMP-compatible);
  no mocking framework is in the catalog

## 10. Migration Plan: single module → KMM (layer-per-module)

Incremental — the app must stay buildable/runnable at every step:

1. ✅ **Create `:shared:domain`** — `Recording` model, `AudioRecorder` port, recording use cases.
2. ✅ **Create `:shared:data`** — `MediaRecorderAudioRecorder` (androidMain) behind the port.
3. ✅ **Create `:shared:presentation`** — `SymptomViewModel` + `SymptomUiState`; Koin wired in
   `:app` (`SecondOpinionApp` + `di/AppModule.kt`).
4. 🔶 **Fill the data layer** — Ktor client + `BackendAssessmentRepository` done; SQLDelight
   case/result persistence remains.
5. **Rename `app` → `androidApp`** (optional, cosmetic) once the shared modules are established.

Gradle note: shared modules apply `org.jetbrains.kotlin.multiplatform` and
`com.android.kotlin.multiplatform.library` *without versions* — AGP 9 already puts both on the
build classpath via the root `com.android.application` plugin, and re-requesting a version fails.

## 11. Related Documents

- `docs/PROJECT_DOCUMENTATION.md` — product context, decisions D1–D6, system architecture, roadmap
- `docs/BACKEND.md` — backend architecture, API surface the app talks to (§3)
- `docs/ideation.txt` — original rough sketch
