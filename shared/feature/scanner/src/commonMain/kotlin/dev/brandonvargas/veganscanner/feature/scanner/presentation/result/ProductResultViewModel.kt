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
import dev.brandonvargas.veganscanner.feature.scanner.domain.community.CommunityVerdictsUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.IngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.RefineWithWebResearchUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.ResearchIssue
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
        /** Ingredients that couldn't be researched online this time (retried on the next visit), and why. */
        val unresearchedCount: Int = 0,
        val researchIssue: ResearchIssue? = null,
        /** The ingredient list shared with a community verdict, when this phone has none for the product. */
        val communityIngredients: String? = null,
        /** The user reported the community verdict shown here as wrong. */
        val communityReported: Boolean = false,
    ) : ProductResultUiState

    data class NotFound(val barcode: String) : ProductResultUiState

    data class Error(val error: AppError) : ProductResultUiState
}

sealed interface ProductResultAction {
    data object Retry : ProductResultAction

    /** The user thinks a web-researched ingredient verdict is wrong. */
    data class ReportResearched(val researchKey: String) : ProductResultAction

    /** The user thinks the community verdict for this product is wrong. */
    data object ReportCommunityVerdict : ProductResultAction
}

/**
 * Shows the offline verdict as soon as it's ready, then refines it in the background when it's inconclusive:
 * with a verdict another user shared for this product, or else with web research (which may then be shared).
 */
class ProductResultViewModel(
    private val barcode: String,
    private val scanProduct: ScanProductUseCase,
    private val refineWithWebResearch: RefineWithWebResearchUseCase,
    private val research: IngredientResearchRepository,
    private val community: CommunityVerdictsUseCase,
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
            ProductResultAction.ReportCommunityVerdict -> reportCommunityVerdict()
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
                            is ScanOutcome.NotFound -> showMissing(parsed)
                            is ScanOutcome.Found -> showAndRefine(outcome)
                        }
                    }
                }
            }
    }

    /** Not in Open Food Facts: another user may have shared a label scan and verdict for it. */
    private suspend fun showMissing(barcode: Barcode) {
        val (product, match) =
            community.lookupMissing(barcode) ?: run {
                _state.value = ProductResultUiState.NotFound(barcode.value)
                return
            }
        _state.value = ProductResultUiState.Found(product, match.verdict, communityIngredients = match.ingredientsText)
    }

    private suspend fun showAndRefine(outcome: ScanOutcome.Found) {
        val shouldResearch = refineWithWebResearch.shouldResearch(outcome.verdict)
        _state.value = ProductResultUiState.Found(outcome.product, outcome.verdict, isResearching = shouldResearch)

        community.lookup(outcome.product, outcome.verdict)?.let { match ->
            _state.update { current ->
                (current as? ProductResultUiState.Found)
                    ?.copy(verdict = match.verdict, isResearching = false, communityIngredients = match.ingredientsText)
                    ?: current
            }
            return
        }
        if (!shouldResearch) return
        val refinement = refineWithWebResearch(outcome.product, outcome.verdict)
        _state.update { current ->
            if (current is ProductResultUiState.Found) {
                current.copy(
                    verdict = refinement.verdict ?: current.verdict,
                    isResearching = false,
                    unresearchedCount = refinement.unresearchedCount,
                    researchIssue = refinement.issue,
                )
            } else {
                current
            }
        }
        refinement.verdict?.let { community.shareIfEligible(outcome.product, it) }
    }

    private fun reportCommunityVerdict() {
        val current = _state.value as? ProductResultUiState.Found ?: return
        if (current.communityReported) return
        _state.value = current.copy(communityReported = true)
        viewModelScope.launch { community.report(current.product.barcode.value, reason = null) }
    }

    private fun report(researchKey: String) {
        val current = _state.value as? ProductResultUiState.Found ?: return
        if (researchKey in current.reportedKeys) return
        _state.value = current.copy(reportedKeys = current.reportedKeys + researchKey)
        viewModelScope.launch { research.report(researchKey, reason = null) }
    }
}
