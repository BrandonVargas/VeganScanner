package dev.brandonvargas.veganscanner.feature.scanner.domain

import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.common.DispatcherProvider
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.IngredientsSource
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.ScanHistoryEntry
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.VerdictPipeline
import kotlinx.coroutines.withContext
import kotlin.time.Clock

sealed interface ScanOutcome {
    data class Found(val product: Product, val verdict: VeganVerdict) : ScanOutcome

    data class NotFound(val barcode: Barcode) : ScanOutcome
}

/**
 * Looks up a barcode, runs the verdict pipeline and records the scan in history.
 *
 * If the user scanned this product's ingredient label, that text is used instead of the database's
 * ingredient list, and it also works when the product isn't in the database or the device is offline.
 */
class ScanProductUseCase(
    private val productRepository: ProductRepository,
    private val labelScanRepository: LabelScanRepository,
    private val verdictPipeline: VerdictPipeline,
    private val historyRepository: ScanHistoryRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(barcode: Barcode): AppResult<ScanOutcome> {
        val labelText = labelScanRepository.get(barcode.value)
        val remote = productRepository.getProduct(barcode)
        if (remote is AppResult.Failure && labelText == null) return remote

        val product =
            (remote as? AppResult.Success)?.value
                ?.withLabel(labelText)
                ?: labelText?.let { labelOnlyProduct(barcode, it) }
                ?: return AppResult.Success(ScanOutcome.NotFound(barcode))

        // CPU-bound (and the first call loads the ingredient knowledge), so keep it off the main thread.
        val verdict = withContext(dispatchers.default) { verdictPipeline.evaluate(product) }
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

    private fun Product.withLabel(labelText: String?): Product =
        if (labelText ==
            null
        ) {
            this
        } else {
            copy(ingredientsText = labelText, ingredientsSource = IngredientsSource.LABEL_SCAN)
        }

    private fun labelOnlyProduct(barcode: Barcode, labelText: String) =
        Product(
            barcode = barcode,
            name = null,
            brands = null,
            imageUrl = null,
            ingredientsText = labelText,
            ingredients = emptyList(),
            sourceAnalysis = VeganStatus.UNKNOWN,
            ingredientsSource = IngredientsSource.LABEL_SCAN,
        )
}
