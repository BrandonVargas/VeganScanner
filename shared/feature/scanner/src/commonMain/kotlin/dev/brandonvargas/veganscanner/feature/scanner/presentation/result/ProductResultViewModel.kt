package dev.brandonvargas.veganscanner.feature.scanner.presentation.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanOutcome
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanProductUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.IngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.RefineWithWebResearchUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProductResultUiState {
    data object Loading : ProductResultUiState

    data class Found(
        val product: Product,
        val verdict: VeganVerdict,
        /** Unrecognized ingredients are being researched online; the verdict may still change. */
        val isResearching: Boolean = false,
        /** Research keys the user has reported as wrong in this screen. */
        val reportedKeys: Set<String> = emptySet(),
    ) : ProductResultUiState

    data class NotFound(val barcode: String) : ProductResultUiState

    data class Error(val error: AppError) : ProductResultUiState
}

sealed interface ProductResultAction {
    data object Retry : ProductResultAction

    /** The user thinks a web-researched ingredient verdict is wrong. */
    data class ReportResearched(val researchKey: String) : ProductResultAction
}

/**
 * Shows the offline verdict as soon as it's ready, then refines it with web research in the background when some
 * ingredients weren't recognized.
 */
class ProductResultViewModel(
    private val barcode: String,
    private val scanProduct: ScanProductUseCase,
    private val refineWithWebResearch: RefineWithWebResearchUseCase,
    private val research: IngredientResearchRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ProductResultUiState>(ProductResultUiState.Loading)
    val state: StateFlow<ProductResultUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: ProductResultAction) {
        when (action) {
            ProductResultAction.Retry -> load()
            is ProductResultAction.ReportResearched -> report(action.researchKey)
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        val parsed = Barcode.parse(barcode)
        if (parsed == null) {
            _state.value = ProductResultUiState.NotFound(barcode)
            return
        }
        _state.value = ProductResultUiState.Loading
        loadJob =
            viewModelScope.launch {
                when (val result = scanProduct(parsed)) {
                    is AppResult.Failure -> {
                        _state.value = ProductResultUiState.Error(result.error)
                    }

                    is AppResult.Success -> {
                        when (val outcome = result.value) {
                            is ScanOutcome.NotFound -> _state.value = ProductResultUiState.NotFound(barcode)
                            is ScanOutcome.Found -> showAndRefine(outcome)
                        }
                    }
                }
            }
    }

    private suspend fun showAndRefine(outcome: ScanOutcome.Found) {
        val shouldResearch = refineWithWebResearch.shouldResearch(outcome.verdict)
        _state.value = ProductResultUiState.Found(outcome.product, outcome.verdict, isResearching = shouldResearch)
        if (!shouldResearch) return
        val refined = refineWithWebResearch(outcome.product, outcome.verdict)
        _state.update { current ->
            if (current is ProductResultUiState.Found) {
                current.copy(verdict = refined ?: current.verdict, isResearching = false)
            } else {
                current
            }
        }
    }

    private fun report(researchKey: String) {
        val current = _state.value as? ProductResultUiState.Found ?: return
        if (researchKey in current.reportedKeys) return
        _state.value = current.copy(reportedKeys = current.reportedKeys + researchKey)
        viewModelScope.launch { research.report(researchKey, reason = null) }
    }
}
