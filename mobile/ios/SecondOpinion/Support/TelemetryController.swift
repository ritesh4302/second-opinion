import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics

/// Consent gate (DPDP) for Firebase telemetry — the iOS counterpart of
/// Android's ConsentGatedTelemetry/FirebaseTelemetrySink. Collection starts
/// disabled via Info.plist (FirebaseAnalyticsCollectionEnabled /
/// FirebaseCrashlyticsCollectionEnabled = NO) and is toggled at the same
/// gates as Android: off while signed out, on/off per the legal-consent
/// acceptance of the current legal version.
final class TelemetryController {
    static let shared = TelemetryController()
    private init() {}

    func setCollectionEnabled(_ enabled: Bool) {
        guard FirebaseApp.app() != nil else { return }
        Analytics.setAnalyticsCollectionEnabled(enabled)
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
        if !enabled {
            // Consent withdrawn/absent: drop identifiers and queued reports,
            // matching the Android sink.
            Analytics.resetAnalyticsData()
            Crashlytics.crashlytics().deleteUnsentReports()
        }
    }
}
