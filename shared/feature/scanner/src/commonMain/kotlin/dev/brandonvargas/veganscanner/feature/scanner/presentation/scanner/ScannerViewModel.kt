package dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brandonvargas.veganscanner.core.model.Barcode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The manual-entry text itself lives in the UI (text fields must update synchronously);
 * the ViewModel only validates on submit.
 */
data class ScannerUiState(
    val isManualEntryInvalid: Boolean = false,
)

sealed interface ScannerAction {
    /** A camera frame decoded a barcode. Fired repeatedly while the code stays in view. */
    data class BarcodeDetected(val rawValue: String) : ScannerAction

    /** The user edited the manual-entry field; clears any previous validation error. */
    data object ManualEntryEdited : ScannerAction

    data class ManualEntrySubmitted(val value: String) : ScannerAction
}

sealed interface ScannerEffect {
    data class OpenResult(val barcode: String) : ScannerEffect
}

class ScannerViewModel(
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val _effects = Channel<ScannerEffect>(Channel.BUFFERED)
    val effects: Flow<ScannerEffect> = _effects.receiveAsFlow()

    private var lastEmitted: Pair<String, Instant>? = null

    fun onAction(action: ScannerAction) {
        when (action) {
            is ScannerAction.BarcodeDetected -> onBarcodeDetected(action.rawValue)
            ScannerAction.ManualEntryEdited -> _state.update { it.copy(isManualEntryInvalid = false) }
            is ScannerAction.ManualEntrySubmitted -> onManualEntrySubmitted(action.value)
        }
    }

    private fun onBarcodeDetected(raw: String) {
        // Ignore unreadable/partial reads silently; the camera keeps trying.
        val barcode = Barcode.parse(raw) ?: return
        val now = clock.now()
        val previous = lastEmitted
        if (previous != null && previous.first == barcode.value && now - previous.second < DUPLICATE_WINDOW) return
        lastEmitted = barcode.value to now
        emit(ScannerEffect.OpenResult(barcode.value))
    }

    private fun onManualEntrySubmitted(value: String) {
        val barcode = Barcode.parse(value)
        if (barcode == null) {
            _state.update { it.copy(isManualEntryInvalid = true) }
            return
        }
        _state.update { it.copy(isManualEntryInvalid = false) }
        emit(ScannerEffect.OpenResult(barcode.value))
    }

    private fun emit(effect: ScannerEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    companion object {
        private const val MAX_DIGITS = 14
        private val DUPLICATE_WINDOW = 3.seconds

        /** Input formatting shared by both UIs: digits only, at most GTIN-14 length. */
        fun sanitizeManualEntry(input: String): String = input.filter(Char::isDigit).take(MAX_DIGITS)
    }
}
