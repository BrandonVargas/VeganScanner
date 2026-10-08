package dev.brandonvargas.veganscanner.core.model

import kotlin.time.Instant

data class ScanHistoryEntry(
    val barcode: String,
    val productName: String?,
    val brands: String?,
    val imageUrl: String?,
    val status: VeganStatus,
    val source: VerdictSource,
    val scannedAt: Instant,
)
