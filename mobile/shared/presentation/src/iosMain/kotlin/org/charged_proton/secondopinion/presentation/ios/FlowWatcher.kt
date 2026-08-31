package org.charged_proton.secondopinion.presentation.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Cancels the underlying flow collection when the SwiftUI view disappears. */
class FlowWatcherHandle internal constructor(private val job: Job) {
    fun close() = job.cancel()
}

/**
 * Bridges a Kotlin [Flow] (typically a ViewModel's `uiState`) to Swift: each
 * emission is delivered to [onEach] on the main dispatcher, so SwiftUI can
 * assign it straight to an `@Published` property.
 */
class FlowWatcher<T>(private val flow: Flow<T>) {

    fun watch(onEach: (T) -> Unit): FlowWatcherHandle {
        val job = scope.launch {
            flow.collect { onEach(it) }
        }
        return FlowWatcherHandle(job)
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}

/**
 * Cancels a shared ViewModel's coroutine scope when its SwiftUI owner is
 * deallocated (the KMP ViewModel has no lifecycle owner on iOS).
 */
fun clearViewModel(viewModel: ViewModel) {
    viewModel.viewModelScope.cancel()
}
