package dev.brandonvargas.veganscanner.feature.scanner.domain

import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.ScanHistoryEntry
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.VerdictPipeline
import kotlin.time.Clock

sealed interface ScanOutcome {
    data class Found(val product: Product, val verdict: VeganVerdict) : ScanOutcome

    data class NotFound(val barcode: Barcode) : ScanOutcome
}

/** Looks up a barcode, runs the verdict pipeline and records the scan in history. */
class ScanProductUseCase(
    private val productRepository: ProductRepository,
    private val verdictPipeline: VerdictPipeline,
    private val historyRepository: ScanHistoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(barcode: Barcode): AppResult<ScanOutcome> {
        val result = productRepository.getProduct(barcode)
        if (result is AppResult.Failure) return result
        val product = (result as AppResult.Success).value ?: return AppResult.Success(ScanOutcome.NotFound(barcode))

        val verdict = verdictPipeline.evaluate(product)
        historyRepository.record(
            ScanHistoryEntry(
                barcode = barcode.value,
                productName = product.name,
                brands = product.brands,
                imageUrl = product.imageUrl,
                status = verdict.status,
                source = verdict.source,
                scannedAt = clock.now(),
            ),
        )
        return AppResult.Success(ScanOutcome.Found(product, verdict))
    }
}
