package dev.brandonvargas.veganscanner.feature.scanner.presentation

import app.cash.turbine.test
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.MainDispatcherOverride
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.core.testing.TestDispatcherProvider
import dev.brandonvargas.veganscanner.feature.scanner.FakeCommunityVerdictRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeIngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeLabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanProductUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.community.CommunityVerdict
import dev.brandonvargas.veganscanner.feature.scanner.domain.community.CommunityVerdictsUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.community.IngredientsHash
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

    private val communityRepository = FakeCommunityVerdictRepository()
    private val community = CommunityVerdictsUseCase(communityRepository, FakeScanHistoryRepository(), TestClock())

    private fun viewModel(barcode: String) = ProductResultViewModel(barcode, useCase, refine, research, community)

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
                ProductResultViewModel(OffFixtures.UNKNOWN_STATUS_BARCODE, pipelineUseCase, refine, research, community)
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
                ProductResultViewModel(OffFixtures.UNKNOWN_STATUS_BARCODE, pipelineUseCase, refine, research, community)
            advanceUntilIdle()

            val found = assertIs<ProductResultUiState.Found>(viewModel.state.value)
            assertEquals(VeganStatus.LIKELY_VEGAN, found.verdict.status)
            assertFalse(found.isResearching)
            assertEquals(1, found.unresearchedCount)
            assertEquals(ResearchIssue.OFFLINE, found.researchIssue)
        }

    private fun pipelineUseCase() =
        ScanProductUseCase(
            productRepository = products,
            labelScanRepository = FakeLabelScanRepository(),
            verdictPipeline = appVerdictPipeline(IngredientKnowledge.Bundled),
            historyRepository = FakeScanHistoryRepository(),
            clock = TestClock(),
            dispatchers = TestDispatcherProvider(),
        )

    private fun sharedVegan(ingredients: String) =
        CommunityVerdict(
            barcode = OffFixtures.UNKNOWN_STATUS_BARCODE,
            status = VeganStatus.VEGAN,
            concludedBy = VerdictSource.WEB_RESEARCH,
            ingredientsText = ingredients,
            ingredientsHash = IngredientsHash.of(ingredients),
            researched = listOf(FlaggedIngredient("xantolina", IngredientVeganStatus.YES, note = "A plant.")),
        )

    @Test
    fun aMatchingCommunityVerdictIsUsedInsteadOfResearch() =
        runTest(main.dispatcher) {
            val product = productFromFixture(OffFixtures.unknownStatusNoIngredients)
            products.result = AppResult.Success(product.copy(ingredientsText = "Agua, XANTOLINA."))
            communityRepository.verdict = sharedVegan("agua, xantolina")

            val viewModel =
                ProductResultViewModel(
                    OffFixtures.UNKNOWN_STATUS_BARCODE,
                    pipelineUseCase(),
                    refine,
                    research,
                    community,
                )
            advanceUntilIdle()

            val found = assertIs<ProductResultUiState.Found>(viewModel.state.value)
            assertEquals(VeganStatus.VEGAN, found.verdict.status)
            assertEquals(VerdictSource.COMMUNITY, found.verdict.source)
            assertEquals(null, found.communityIngredients, "the product already has its own ingredients")
            assertEquals(emptyList(), research.requested)
        }

    @Test
    fun productsWithoutIngredientsGetTheSharedList() =
        runTest(main.dispatcher) {
            products.result = AppResult.Success(productFromFixture(OffFixtures.unknownStatusNoIngredients))
            communityRepository.verdict = sharedVegan("agua, xantolina")

            val viewModel =
                ProductResultViewModel(
                    OffFixtures.UNKNOWN_STATUS_BARCODE,
                    pipelineUseCase(),
                    refine,
                    research,
                    community,
                )
            advanceUntilIdle()

            val found = assertIs<ProductResultUiState.Found>(viewModel.state.value)
            assertEquals(VerdictSource.COMMUNITY, found.verdict.source)
            assertEquals("agua, xantolina", found.communityIngredients)
        }

    @Test
    fun aVerdictForDifferentIngredientsIsIgnoredAndResearchedResultsAreShared() =
        runTest(main.dispatcher) {
            val product = productFromFixture(OffFixtures.unknownStatusNoIngredients)
            products.result = AppResult.Success(product.copy(ingredientsText = "Agua, xantolina"))
            communityRepository.verdict = sharedVegan("agua, xantolina, leche")
            research.results = listOf(researchedVegan("xantolina"))

            val viewModel =
                ProductResultViewModel(
                    OffFixtures.UNKNOWN_STATUS_BARCODE,
                    pipelineUseCase(),
                    refine,
                    research,
                    community,
                )
            advanceUntilIdle()

            val found = assertIs<ProductResultUiState.Found>(viewModel.state.value)
            assertEquals(VerdictSource.WEB_RESEARCH, found.verdict.source)
            val shared = communityRepository.shared.single()
            assertEquals("Agua, xantolina", shared.ingredientsText)
            assertEquals(IngredientsHash.of("agua xantolina"), shared.ingredientsHash)
            assertEquals(listOf("xantolina"), shared.researched.map { it.name })
        }

    @Test
    fun productsMissingFromOpenFoodFactsCanComeFromTheCommunity() =
        runTest(main.dispatcher) {
            products.result = AppResult.Success(null)
            communityRepository.verdict = sharedVegan("agua, xantolina")

            val viewModel =
                ProductResultViewModel(
                    OffFixtures.UNKNOWN_STATUS_BARCODE,
                    pipelineUseCase(),
                    refine,
                    research,
                    community,
                )
            advanceUntilIdle()

            val found = assertIs<ProductResultUiState.Found>(viewModel.state.value)
            assertEquals(VerdictSource.COMMUNITY, found.verdict.source)
            assertEquals("agua, xantolina", found.communityIngredients)
        }

    @Test
    fun communityReportIsSentOnce() =
        runTest(main.dispatcher) {
            products.result = AppResult.Success(productFromFixture(OffFixtures.unknownStatusNoIngredients))
            communityRepository.verdict = sharedVegan("agua, xantolina")
            val viewModel =
                ProductResultViewModel(
                    OffFixtures.UNKNOWN_STATUS_BARCODE,
                    pipelineUseCase(),
                    refine,
                    research,
                    community,
                )
            advanceUntilIdle()

            viewModel.onAction(ProductResultAction.ReportCommunityVerdict)
            viewModel.onAction(ProductResultAction.ReportCommunityVerdict)
            advanceUntilIdle()

            assertTrue(assertIs<ProductResultUiState.Found>(viewModel.state.value).communityReported)
            assertEquals(listOf(OffFixtures.UNKNOWN_STATUS_BARCODE), communityRepository.reported)
        }

    private fun researchedVegan(name: String) =
        ResearchedIngredient(name, name, IngredientVeganStatus.YES, "r", "r", emptyList())
}
