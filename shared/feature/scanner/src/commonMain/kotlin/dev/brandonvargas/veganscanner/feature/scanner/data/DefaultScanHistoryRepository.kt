package dev.brandonvargas.veganscanner.feature.scanner.data

import dev.brandonvargas.veganscanner.core.database.dao.ScanHistoryDao
import dev.brandonvargas.veganscanner.core.database.entity.ScanHistoryEntity
import dev.brandonvargas.veganscanner.core.model.ScanHistoryEntry
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class DefaultScanHistoryRepository(
    private val dao: ScanHistoryDao,
    private val limit: Int = 200,
) : ScanHistoryRepository {
    override fun observeHistory(): Flow<List<ScanHistoryEntry>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(entry: ScanHistoryEntry) = dao.upsert(entry.toEntity())

    override suspend fun delete(barcode: String) = dao.delete(barcode)

    override suspend fun clear() = dao.clear()
}

private fun ScanHistoryEntity.toDomain() =
    ScanHistoryEntry(
        barcode = barcode,
        productName = productName,
        brands = brands,
        imageUrl = imageUrl,
        status = VeganStatus.entries.firstOrNull { it.name == status } ?: VeganStatus.UNKNOWN,
        source = VerdictSource.entries.firstOrNull { it.name == source } ?: VerdictSource.UNDETERMINED,
        scannedAt = Instant.fromEpochMilliseconds(scannedAtEpochMs),
    )

private fun ScanHistoryEntry.toEntity() =
    ScanHistoryEntity(
        barcode = barcode,
        productName = productName,
        brands = brands,
        imageUrl = imageUrl,
        status = status.name,
        source = source.name,
        scannedAtEpochMs = scannedAt.toEpochMilliseconds(),
    )
