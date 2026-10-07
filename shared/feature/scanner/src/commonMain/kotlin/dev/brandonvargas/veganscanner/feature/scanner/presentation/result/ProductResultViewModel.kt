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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProductResultUiState {
    data object Loading : ProductResultUiState

    data class Found(val product: Product, val verdict: VeganVerdict) : ProductResultUiState

    data class NotFound(val barcode: String) : ProductResultUiState

    data class Error(val error: AppError) : ProductResultUiState
}

sealed interface ProductResultAction {
    data object Retry : ProductResultAction
}

class ProductResultViewModel(
    private val barcode: String,
    private val scanProduct: ScanProductUseCase,
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
                _state.value =
                    when (val result = scanProduct(parsed)) {
                        is AppResult.Failure -> {
                            ProductResultUiState.Error(result.error)
                        }

                        is AppResult.Success -> {
                            when (val outcome = result.value) {
                                is ScanOutcome.Found -> ProductResultUiState.Found(outcome.product, outcome.verdict)
                                is ScanOutcome.NotFound -> ProductResultUiState.NotFound(barcode)
                            }
                        }
                    }
            }
    }
}
