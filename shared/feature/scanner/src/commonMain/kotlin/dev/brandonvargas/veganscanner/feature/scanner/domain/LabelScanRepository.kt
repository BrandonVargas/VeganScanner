package dev.brandonvargas.veganscanner.feature.scanner.domain

/** Ingredient text scanned from product labels, kept on the device per barcode. */
interface LabelScanRepository {
    suspend fun get(barcode: String): String?

    suspend fun save(barcode: String, ingredientsText: String)
}
