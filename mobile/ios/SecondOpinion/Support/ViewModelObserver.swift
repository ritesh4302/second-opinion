import Foundation
import SharedKit

/// Owns a shared-code ViewModel for the lifetime of a SwiftUI view
/// (`@StateObject`), republishing its `uiState` StateFlow into SwiftUI via
/// the Kotlin `FlowWatcher` bridge. On deinit it cancels the collection and
/// clears the ViewModel's coroutine scope.
final class ViewModelObserver<VM: Lifecycle_viewmodelViewModel, State: AnyObject>: ObservableObject {
    let vm: VM
    @Published private(set) var state: State
    private var handle: FlowWatcherHandle?

    init(_ vm: VM, _ stateFlow: Kotlinx_coroutines_coreStateFlow) {
        self.vm = vm
        self.state = stateFlow.value as! State
        self.handle = FlowWatcher<AnyObject>(flow: stateFlow).watch { [weak self] emitted in
            if let emitted = emitted as? State {
                self?.state = emitted
            }
        }
    }

    deinit {
        handle?.close()
        FlowWatcherKt.clearViewModel(viewModel: vm)
    }
}
