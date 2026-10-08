package dev.brandonvargas.veganscanner.feature.scanner.data

import co.touchlab.kermit.Logger
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.database.dao.ProductCacheDao
import dev.brandonvargas.veganscanner.core.database.entity.CachedProductEntity
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.network.off.OffProductDto
import dev.brandonvargas.veganscanner.core.network.off.OpenFoodFactsApi
import dev.brandonvargas.veganscanner.feature.scanner.domain.ProductRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Offline-first: fresh cache → network → stale cache as a fallback when the network fails.
 * "Not found" is never cached so newly added Open Food Facts products show up right away.
 */
internal class DefaultProductRepository(
    private val api: OpenFoodFactsApi,
    private val cache: ProductCacheDao,
    private val json: Json,
    private val clock: Clock,
    private val cacheTtl: Duration = 7.days,
) : ProductRepository {
    private val log = Logger.withTag("ProductRepository")

    override suspend fun getProduct(barcode: Barcode): AppResult<Product?> {
        val cached = cache.get(barcode.value)
        val cachedProduct = cached?.decode()
        val isFresh = cached != null && clock.now() - Instant.fromEpochMilliseconds(cached.fetchedAtEpochMs) < cacheTtl
        if (cachedProduct != null && isFresh) return AppResult.Success(cachedProduct.toDomain(barcode))

        return when (val remote = api.fetchProduct(barcode.value)) {
            is AppResult.Success -> {
                remote.value?.let { dto ->
                    cache.upsert(
                        CachedProductEntity(
                            barcode = barcode.value,
                            sourceJson = json.encodeToString(OffProductDto.serializer(), dto),
                            fetchedAtEpochMs = clock.now().toEpochMilliseconds(),
                        ),
                    )
                }
                AppResult.Success(remote.value?.toDomain(barcode))
            }

            is AppResult.Failure -> {
                if (cachedProduct != null) {
                    log.i { "Serving stale cache for $barcode after ${remote.error}" }
                    AppResult.Success(cachedProduct.toDomain(barcode))
                } else {
                    remote
                }
            }
        }
    }

    private fun CachedProductEntity.decode(): OffProductDto? =
        try {
            json.decodeFromString(OffProductDto.serializer(), sourceJson)
        } catch (e: SerializationException) {
            log.w(e) { "Discarding unreadable cache entry for $barcode" }
            null
        }
}
