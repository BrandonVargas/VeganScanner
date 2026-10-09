package dev.brandonvargas.veganscanner.feature.scanner.presentation.label

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brandonvargas.veganscanner.feature.scanner.domain.LabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientLabelText
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.OcrLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LabelScanStep { CAPTURE, REVIEW }

enum class LabelScanError {
    /** OCR found no readable text in the photo. */
    NOTHING_RECOGNIZED,

    /** The reviewed text is too short to be an ingredient list. */
    TEXT_TOO_SHORT,
}

/**
 * Text to load into the review field. The text field itself lives in the UI (see ADR 0005); [revision] changes
 * every time the ViewModel wants to replace its content (a new capture, or typing manually).
 */
data class LabelDraft(val text: String, val revision: Int)

data class LabelScanUiState(
    val step: LabelScanStep = LabelScanStep.CAPTURE,
    val draft: LabelDraft? = null,
    val isRecognizing: Boolean = false,
    val isSaving: Boolean = false,
    val error: LabelScanError? = null,
)

sealed interface LabelScanAction {
    /** The platform started on-device text recognition on a captured photo. */
    data object CaptureStarted : LabelScanAction

    /** OCR finished: every recognized line with its position on the photo (see [OcrLine]). */
    data class TextRecognized(val lines: List<OcrLine>) : LabelScanAction

    data object RecognitionFailed : LabelScanAction

    data object TypeManually : LabelScanAction

    data object Retake : LabelScanAction

    data object TextEdited : LabelScanAction

    data class Submit(val text: String) : LabelScanAction
}

sealed interface LabelScanEffect {
    /** The label was saved; show the re-evaluated result for this barcode. */
    data class ShowResult(val barcode: String) : LabelScanEffect
}

/**
 * Scan an ingredient label → review/fix the recognized text → save it for this barcode.
 * OCR runs on-device in the platform UI (ML Kit / Vision); only the resulting text reaches shared code.
 */
class LabelScanViewModel(
    private val barcode: String,
    private val repository: LabelScanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LabelScanUiState())
    val state: StateFlow<LabelScanUiState> = _state.asStateFlow()

    private val _effects = Channel<LabelScanEffect>(Channel.BUFFERED)
    val effects: Flow<LabelScanEffect> = _effects.receiveAsFlow()

    fun onAction(action: LabelScanAction) {
        when (action) {
            LabelScanAction.CaptureStarted -> {
                _state.update { it.copy(isRecognizing = true, error = null) }
            }

            is LabelScanAction.TextRecognized -> {
                onTextRecognized(action.lines)
            }

            LabelScanAction.RecognitionFailed -> {
                _state.update {
                    it.copy(isRecognizing = false, error = LabelScanError.NOTHING_RECOGNIZED)
                }
            }

            LabelScanAction.TypeManually -> {
                showReview("")
            }

            LabelScanAction.Retake -> {
                _state.update {
                    it.copy(step = LabelScanStep.CAPTURE, isRecognizing = false, error = null)
                }
            }

            LabelScanAction.TextEdited -> {
                _state.update { it.copy(error = null) }
            }

            is LabelScanAction.Submit -> {
                submit(action.text)
            }
        }
    }

    private fun onTextRecognized(lines: List<OcrLine>) {
        val ingredients = IngredientLabelText.extract(lines)
        if (ingredients.count(Char::isLetter) < MIN_LETTERS) {
            _state.update { it.copy(isRecognizing = false, error = LabelScanError.NOTHING_RECOGNIZED) }
        } else {
            showReview(ingredients)
        }
    }

    private fun showReview(text: String) =
        _state.update {
            it.copy(
                step = LabelScanStep.REVIEW,
                draft = LabelDraft(text, revision = (it.draft?.revision ?: 0) + 1),
                isRecognizing = false,
                error = null,
            )
        }

    private fun submit(text: String) {
        val ingredients = text.trim()
        if (ingredients.count(Char::isLetter) < MIN_LETTERS) {
            _state.update { it.copy(error = LabelScanError.TEXT_TOO_SHORT) }
            return
        }
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repository.save(barcode, ingredients)
            _state.update { it.copy(isSaving = false) }
            _effects.send(LabelScanEffect.ShowResult(barcode))
        }
    }

    private companion object {
        const val MIN_LETTERS = 3
    }
}
