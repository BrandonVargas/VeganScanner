package dev.brandonvargas.veganscanner.feature.scanner.domain

import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.ScanHistoryEntry
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import kotlin.time.Instant

internal fun Product.historyEntry(verdict: VeganVerdict, at: Instant) =
    ScanHistoryEntry(
        barcode = barcode.value,
        productName = name,
        brands = brands,
        imageUrl = imageUrl,
        status = verdict.status,
        source = verdict.source,
        scannedAt = at,
    )
