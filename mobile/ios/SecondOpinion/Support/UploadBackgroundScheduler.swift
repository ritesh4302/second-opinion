import BackgroundTasks
import SharedKit
import UIKit

/// BGTaskScheduler shim for the durable upload queue — the iOS counterpart of
/// Android's WorkManager-backed AssessmentUploadWorker. The queue state
/// machine and retry policy (five bounded exponential-backoff attempts) live
/// in the shared UploadQueueProcessor/InProcessAssessmentScheduler; this class
/// only decides *when* they run:
///
/// - at launch and on foregrounding, pending rows are re-driven immediately;
/// - when the app backgrounds with work still pending, a
///   BGProcessingTaskRequest (requiresNetworkConnectivity) is submitted so the
///   run can continue after suspension. BGTaskScheduler timing is
///   opportunistic — iOS decides when queued work runs, unlike WorkManager's
///   constraint-based guarantees — so the launch/foreground resume remains the
///   primary drive and the background task is best-effort catch-up.
final class UploadBackgroundScheduler {
    /// Listed in Info.plist under BGTaskSchedulerPermittedIdentifiers.
    static let taskIdentifier = "org.charged-proton.secondopinion.upload-queue"
    static let shared = UploadBackgroundScheduler()
    private var graph: IosAppGraph?

    private init() {}

    /// Must be called before application(_:didFinishLaunching) returns
    /// (BGTaskScheduler requirement for handler registration).
    func start(graph: IosAppGraph) {
        self.graph = graph

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.taskIdentifier,
            using: nil
        ) { [weak self] task in
            self?.handle(task: task as! BGProcessingTask)
        }

        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in self?.scheduleIfPending() }

        NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in self?.resume() }

        // Launch-time re-drive of work interrupted by a previous termination.
        resume()
    }

    /// Re-runs every pending queue entry in-process (no-op when none).
    private func resume() {
        graph?.resumePendingUploads {}
    }

    /// Backgrounding with pending work: ask iOS for a processing window.
    private func scheduleIfPending() {
        graph?.hasPendingUploads { pending in
            guard pending.boolValue else { return }
            let request = BGProcessingTaskRequest(identifier: Self.taskIdentifier)
            request.requiresNetworkConnectivity = true
            // Uploads are short; no charger requirement (matches Android's
            // NetworkType.CONNECTED-only constraint).
            request.requiresExternalPower = false
            try? BGTaskScheduler.shared.submit(request)
        }
    }

    private func handle(task: BGProcessingTask) {
        guard let graph else { return task.setTaskCompleted(success: false) }
        task.expirationHandler = {
            // Rows stay pending; the next launch/foreground/BG window re-drives.
            graph.cancelPendingUploads()
            task.setTaskCompleted(success: false)
        }
        graph.resumePendingUploads {
            // Anything still pending (retries exhausted this run) waits for
            // the next drive; re-submit only if work remains.
            graph.hasPendingUploads { pending in
                if pending.boolValue {
                    let request = BGProcessingTaskRequest(identifier: Self.taskIdentifier)
                    request.requiresNetworkConnectivity = true
                    try? BGTaskScheduler.shared.submit(request)
                }
                task.setTaskCompleted(success: true)
            }
        }
    }
}
