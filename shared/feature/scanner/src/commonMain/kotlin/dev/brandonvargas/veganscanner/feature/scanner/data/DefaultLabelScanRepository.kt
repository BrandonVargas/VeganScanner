package dev.brandonvargas.veganscanner.feature.scanner.data

import dev.brandonvargas.veganscanner.core.database.dao.LabelScanDao
import dev.brandonvargas.veganscanner.core.database.entity.LabelScanEntity
import dev.brandonvargas.veganscanner.feature.scanner.domain.LabelScanRepository
import kotlin.time.Clock

internal class DefaultLabelScanRepository(
    private val dao: LabelScanDao,
    private val clock: Clock,
) : LabelScanRepository {
    override suspend fun get(barcode: String): String? = dao.get(barcode)?.ingredientsText

    override suspend fun save(barcode: String, ingredientsText: String) =
        dao.upsert(
            LabelScanEntity(barcode, ingredientsText, clock.now().toEpochMilliseconds()),
        )
}
