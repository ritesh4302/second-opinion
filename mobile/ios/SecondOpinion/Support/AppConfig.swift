import Foundation

enum AppConfig {
    /// Backend base URL injected from the BACKEND_BASE_URL build setting via
    /// Info.plist (Debug: docker-compose loopback, Release: Cloud Run).
    static var backendBaseURL: String {
        Bundle.main.object(forInfoDictionaryKey: "BackendBaseURL") as? String
            ?? "http://127.0.0.1:8000"
    }
}
