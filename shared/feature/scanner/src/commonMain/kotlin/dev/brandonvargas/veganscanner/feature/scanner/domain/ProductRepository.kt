package dev.brandonvargas.veganscanner.feature.scanner.domain

import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.Product

interface ProductRepository {
    /** Succeeds with `null` when no data source knows the barcode. */
    suspend fun getProduct(barcode: Barcode): AppResult<Product?>
}
