package dev.brandonvargas.veganscanner.feature.scanner.presentation

import app.cash.turbine.test
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.MainDispatcherOverride
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.core.testing.TestDispatcherProvider
import dev.brandonvargas.veganscanner.feature.scanner.FakeIngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeLabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanProductUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.RefineWithWebResearchUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.ResearchIssue
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.ResearchedIngredient
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientKnowledge
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.OffAnalysisResolver
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.VerdictPipeline
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.appVerdictPipeline
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultAction
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultUiState
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultViewModel
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProductResultViewModelTest {
    private val main = MainDispatcherOverride()
    private val products = FakeProductRepository()
    private val useCase =
        ScanProductUseCase(
            productRepository = products,
            labelScanRepository = FakeLabelScanRepository(),
            verdictPipeline = VerdictPipeline(listOf(OffAnalysisResolver())),
            historyRepository = FakeScanHistoryRepository(),
            clock = TestClock(),
            dispatchers = TestDispatcherProvider(),
        )
    private val research = FakeIngredientResearchRepository()
    private val refine =
        RefineWithWebResearchUseCase(research, FakeScanHistoryRepository(), TestClock(), deviceLanguage = "en")

    private fun viewModel(barcode: String) = ProductResultViewModel(barcode, useCase, refine, research)

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun loadsFoundProduct() =
        runTest(main.dispatcher) {
            products.result = AppResult.Success(productFromFixture(OffFixtures.vegan))

            viewModel(OffFixtures.VEGAN_BARCODE).state.test {
                assertEquals(ProductResultUiState.Loading, awaitItem())
                val found = assertIs<ProductResultUiState.Found>(awaitItem())
                assertEquals(VeganStatus.VEGAN, found.verdict.status)
            }
        }

    @Test
    fun notFoundProduct() =
        runTest(main.dispatcher) {
            products.result = AppResult.Success(null)

            viewModel(OffFixtures.NOT_FOUND_BARCODE).state.test {
                assertEquals(ProductResultUiState.Loading, awaitItem())
                assertEquals(ProductResultUiState.NotFound(OffFixtures.NOT_FOUND_BARCODE), awaitItem())
            }
        }

    @Test
    fun invalidBarcodeIsNotFoundWithoutNetwork() =
        runTest(main.dispatcher) {
            val viewModel = viewModel("123")

            assertEquals(ProductResultUiState.NotFound("123"), viewModel.state.value)
            assertEquals(0, products.calls)
        }

    @Test
    fun errorThenRetrySucceeds() =
        runTest(main.dispatcher) {
            products.result = AppResult.Failure(AppError.Network)
            val viewModel = viewModel(OffFixtures.VEGAN_BARCODE)

            viewModel.state.test {
                assertEquals(ProductResultUiState.Loading, awaitItem())
                assertEquals(ProductResultUiState.Error(AppError.Network), awaitItem())

                products.result = AppResult.Success(productFromFixture(OffFixtures.vegan))
                viewModel.onAction(ProductResultAction.Retry)

                assertEquals(ProductResultUiState.Loading, awaitItem())
                assertIs<ProductResultUiState.Found>(awaitItem())
            }
        }

    @Test
    fun unrecognizedIngredientsAreResearchedAfterTheResultIsShown() =
        runTest(main.dispatcher) {
            // Unknown status and an ingredient no dictionary knows: the offline verdict is "probably vegan".
            val product = productFromFixture(OffFixtures.unknownStatusNoIngredients)
            products.result = AppResult.Success(product.copy(ingredientsText = "Agua, xantolina"))
            research.results =
                listOf(
                    ResearchedIngredient(
                        name = "xantolina",
                        key = "xantolina",
                        status = IngredientVeganStatus.YES,
                        reasonEn = "A plant extract.",
                        reasonEs = null,
                        sources = emptyList(),
                    ),
                )
            val pipelineUseCase =
                ScanProductUseCase(
                    productRepository = products,
                    labelScanRepository = FakeLabelScanRepository(),
                    verdictPipeline = appVerdictPipeline(IngredientKnowledge.Bundled),
                    historyRepository = FakeScanHistoryRepository(),
                    clock = TestClock(),
                    dispatchers = TestDispatcherProvider(),
                )

            val viewModel =
                ProductResultViewModel(OffFixtures.UNKNOWN_STATUS_BARCODE, pipelineUseCase, refine, research)
            viewModel.state.test {
                assertEquals(ProductResultUiState.Loading, awaitItem())
                val offline = assertIs<ProductResultUiState.Found>(awaitItem())
                assertEquals(VeganStatus.LIKELY_VEGAN, offline.verdict.status)
                assertTrue(offline.isResearching)

                val refined = assertIs<ProductResultUiState.Found>(awaitItem())
                assertEquals(VeganStatus.VEGAN, refined.verdict.status)
                assertEquals(VerdictSource.WEB_RESEARCH, refined.verdict.source)
                assertFalse(refined.isResearching)
            }
        }

    @Test
    fun reportingIsSentOnceAndRemembered() =
        runTest(main.dispatcher) {
            products.result = AppResult.Success(productFromFixture(OffFixtures.vegan))
            val viewModel = viewModel(OffFixtures.VEGAN_BARCODE)
            advanceUntilIdle()

            viewModel.onAction(ProductResultAction.ReportResearched("xantolina"))
            viewModel.onAction(ProductResultAction.ReportResearched("xantolina"))
            advanceUntilIdle()

            val found = assertIs<ProductResultUiState.Found>(viewModel.state.value)
            assertEquals(setOf("xantolina"), found.reportedKeys)
            assertEquals(listOf("xantolina"), research.reported)
        }

    @Test
    fun researchProblemsAreShownInsteadOfFailingSilently() =
        runTest(main.dispatcher) {
            val product = productFromFixture(OffFixtures.unknownStatusNoIngredients)
            products.result = AppResult.Success(product.copy(ingredientsText = "Agua, xantolina"))
            research.failure = AppError.Network
            val pipelineUseCase =
                ScanProductUseCase(
                    productRepository = products,
                    labelScanRepository = FakeLabelScanRepository(),
                    verdictPipeline = appVerdictPipeline(IngredientKnowledge.Bundled),
                    historyRepository = FakeScanHistoryRepository(),
                    clock = TestClock(),
                    dispatchers = TestDispatcherProvider(),
                )

            val viewModel =
                ProductResultViewModel(OffFixtures.UNKNOWN_STATUS_BARCODE, pipelineUseCase, refine, research)
            advanceUntilIdle()

            val found = assertIs<ProductResultUiState.Found>(viewModel.state.value)
            assertEquals(VeganStatus.LIKELY_VEGAN, found.verdict.status)
            assertFalse(found.isResearching)
            assertEquals(1, found.unresearchedCount)
            assertEquals(ResearchIssue.OFFLINE, found.researchIssue)
        }
}
