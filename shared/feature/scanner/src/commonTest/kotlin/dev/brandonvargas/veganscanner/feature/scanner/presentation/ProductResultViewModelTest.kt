package dev.brandonvargas.veganscanner.feature.scanner.presentation

import app.cash.turbine.test
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.testing.MainDispatcherOverride
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.feature.scanner.FakeProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanProductUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.OffAnalysisResolver
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.VerdictPipeline
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultAction
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultUiState
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultViewModel
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProductResultViewModelTest {
    private val main = MainDispatcherOverride()
    private val products = FakeProductRepository()
    private val useCase =
        ScanProductUseCase(
            productRepository = products,
            verdictPipeline = VerdictPipeline(listOf(OffAnalysisResolver())),
            historyRepository = FakeScanHistoryRepository(),
            clock = TestClock(),
        )

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun loadsFoundProduct() =
        runTest(main.dispatcher) {
            products.result = AppResult.Success(productFromFixture(OffFixtures.vegan))

            ProductResultViewModel(OffFixtures.VEGAN_BARCODE, useCase).state.test {
                assertEquals(ProductResultUiState.Loading, awaitItem())
                val found = assertIs<ProductResultUiState.Found>(awaitItem())
                assertEquals(VeganStatus.VEGAN, found.verdict.status)
            }
        }

    @Test
    fun notFoundProduct() =
        runTest(main.dispatcher) {
            products.result = AppResult.Success(null)

            ProductResultViewModel(OffFixtures.NOT_FOUND_BARCODE, useCase).state.test {
                assertEquals(ProductResultUiState.Loading, awaitItem())
                assertEquals(ProductResultUiState.NotFound(OffFixtures.NOT_FOUND_BARCODE), awaitItem())
            }
        }

    @Test
    fun invalidBarcodeIsNotFoundWithoutNetwork() =
        runTest(main.dispatcher) {
            val viewModel = ProductResultViewModel("123", useCase)

            assertEquals(ProductResultUiState.NotFound("123"), viewModel.state.value)
            assertEquals(0, products.calls)
        }

    @Test
    fun errorThenRetrySucceeds() =
        runTest(main.dispatcher) {
            products.result = AppResult.Failure(AppError.Network)
            val viewModel = ProductResultViewModel(OffFixtures.VEGAN_BARCODE, useCase)

            viewModel.state.test {
                assertEquals(ProductResultUiState.Loading, awaitItem())
                assertEquals(ProductResultUiState.Error(AppError.Network), awaitItem())

                products.result = AppResult.Success(productFromFixture(OffFixtures.vegan))
                viewModel.onAction(ProductResultAction.Retry)

                assertEquals(ProductResultUiState.Loading, awaitItem())
                assertIs<ProductResultUiState.Found>(awaitItem())
            }
        }
}
