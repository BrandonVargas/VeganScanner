package dev.brandonvargas.veganscanner.feature.scanner.domain

import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.feature.scanner.FakeProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.OffAnalysisResolver
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.OffIngredientsResolver
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.VerdictPipeline
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScanProductUseCaseTest {
    private val products = FakeProductRepository()
    private val history = FakeScanHistoryRepository()
    private val clock = TestClock()
    private val useCase =
        ScanProductUseCase(
            productRepository = products,
            verdictPipeline = VerdictPipeline(listOf(OffAnalysisResolver(), OffIngredientsResolver())),
            historyRepository = history,
            clock = clock,
        )

    @Test
    fun foundProductGetsVerdictAndIsRecorded() =
        runTest {
            products.result = AppResult.Success(productFromFixture(OffFixtures.nonVegan))
            val barcode = Barcode.parse(OffFixtures.NON_VEGAN_BARCODE)!!

            val outcome = assertIs<ScanOutcome.Found>(assertIs<AppResult.Success<ScanOutcome>>(useCase(barcode)).value)

            assertEquals(VeganStatus.NON_VEGAN, outcome.verdict.status)
            val entry = history.entries.value.single()
            assertEquals(barcode.value, entry.barcode)
            assertEquals("Nutella", entry.productName)
            assertEquals(VeganStatus.NON_VEGAN, entry.status)
            assertEquals(VerdictSource.OPEN_FOOD_FACTS, entry.source)
            assertEquals(clock.now(), entry.scannedAt)
        }

    @Test
    fun notFoundIsNotRecorded() =
        runTest {
            products.result = AppResult.Success(null)
            val barcode = Barcode.parse(OffFixtures.NOT_FOUND_BARCODE)!!

            val outcome = assertIs<AppResult.Success<ScanOutcome>>(useCase(barcode)).value

            assertEquals(ScanOutcome.NotFound(barcode), outcome)
            assertTrue(history.entries.value.isEmpty())
        }

    @Test
    fun failureIsPropagated() =
        runTest {
            products.result = AppResult.Failure(AppError.Network)

            val result = useCase(Barcode.parse(OffFixtures.VEGAN_BARCODE)!!)

            assertEquals(AppResult.Failure(AppError.Network), result)
        }
}
