package dev.brandonvargas.veganscanner.feature.scanner.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brandonvargas.veganscanner.core.model.ScanHistoryEntry
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<ScanHistoryEntry> = emptyList(),
)

sealed interface HistoryAction {
    data class Delete(val barcode: String) : HistoryAction

    data object ClearAll : HistoryAction
}

class HistoryViewModel(
    private val repository: ScanHistoryRepository,
) : ViewModel() {
    val state: StateFlow<HistoryUiState> =
        repository.observeHistory()
            .map { HistoryUiState(isLoading = false, entries = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    fun onAction(action: HistoryAction) {
        viewModelScope.launch {
            when (action) {
                is HistoryAction.Delete -> repository.delete(action.barcode)
                HistoryAction.ClearAll -> repository.clear()
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
