package dev.brandonvargas.veganscanner.feature.scanner

import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.database.dao.ProductCacheDao
import dev.brandonvargas.veganscanner.core.database.dao.ScanHistoryDao
import dev.brandonvargas.veganscanner.core.database.entity.CachedProductEntity
import dev.brandonvargas.veganscanner.core.database.entity.ScanHistoryEntity
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.ScanHistoryEntry
import dev.brandonvargas.veganscanner.core.network.NetworkJson
import dev.brandonvargas.veganscanner.core.network.off.OffProductResponseDto
import dev.brandonvargas.veganscanner.feature.scanner.data.toDomain
import dev.brandonvargas.veganscanner.feature.scanner.domain.LabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Decodes an [dev.brandonvargas.veganscanner.core.testing.OffFixtures] response into a domain product. */
fun productFromFixture(json: String): Product {
    val response = NetworkJson.decodeFromString(OffProductResponseDto.serializer(), json)
    return requireNotNull(response.product).toDomain(requireNotNull(Barcode.parse(requireNotNull(response.code))))
}

class FakeProductCacheDao : ProductCacheDao {
    val rows = mutableMapOf<String, CachedProductEntity>()

    override suspend fun get(barcode: String) = rows[barcode]

    override suspend fun upsert(entity: CachedProductEntity) {
        rows[entity.barcode] = entity
    }

    override suspend fun deleteOlderThan(olderThanEpochMs: Long) {
        rows.values.removeAll { it.fetchedAtEpochMs < olderThanEpochMs }
    }
}

class FakeScanHistoryDao : ScanHistoryDao {
    private val rows = MutableStateFlow<Map<String, ScanHistoryEntity>>(emptyMap())

    override fun observeRecent(limit: Int): Flow<List<ScanHistoryEntity>> =
        rows.map { it.values.sortedByDescending(ScanHistoryEntity::scannedAtEpochMs).take(limit) }

    override suspend fun upsert(entity: ScanHistoryEntity) = rows.update { it + (entity.barcode to entity) }

    override suspend fun delete(barcode: String) = rows.update { it - barcode }

    override suspend fun clear() = rows.update { emptyMap() }
}

class FakeProductRepository(var result: AppResult<Product?> = AppResult.Success(null)) : ProductRepository {
    var calls = 0

    override suspend fun getProduct(barcode: Barcode): AppResult<Product?> {
        calls++
        return result
    }
}

class FakeScanHistoryRepository : ScanHistoryRepository {
    val entries = MutableStateFlow<List<ScanHistoryEntry>>(emptyList())

    override fun observeHistory(): Flow<List<ScanHistoryEntry>> = entries

    override suspend fun record(entry: ScanHistoryEntry) =
        entries.update { list -> listOf(entry) + list.filterNot { it.barcode == entry.barcode } }

    override suspend fun delete(barcode: String) = entries.update { list -> list.filterNot { it.barcode == barcode } }

    override suspend fun clear() = entries.update { emptyList() }
}

class FakeLabelScanRepository : LabelScanRepository {
    val labels = mutableMapOf<String, String>()

    override suspend fun get(barcode: String): String? = labels[barcode]

    override suspend fun save(barcode: String, ingredientsText: String) {
        labels[barcode] = ingredientsText
    }
}
