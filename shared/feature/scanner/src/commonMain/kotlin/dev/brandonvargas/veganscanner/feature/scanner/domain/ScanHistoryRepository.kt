package dev.brandonvargas.veganscanner.feature.scanner.domain

import dev.brandonvargas.veganscanner.core.model.ScanHistoryEntry
import kotlinx.coroutines.flow.Flow

interface ScanHistoryRepository {
    fun observeHistory(): Flow<List<ScanHistoryEntry>>

    suspend fun record(entry: ScanHistoryEntry)

    suspend fun delete(barcode: String)

    suspend fun clear()
}
