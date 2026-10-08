package dev.brandonvargas.veganscanner.feature.scanner.data

import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.database.entity.CachedProductEntity
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.network.NetworkConfig
import dev.brandonvargas.veganscanner.core.network.NetworkJson
import dev.brandonvargas.veganscanner.core.network.createHttpClient
import dev.brandonvargas.veganscanner.core.network.off.OpenFoodFactsApi
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.feature.scanner.FakeProductCacheDao
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

class DefaultProductRepositoryTest {
    private val clock = TestClock()
    private val cache = FakeProductCacheDao()
    private var networkCalls = 0
    private var nextResponse: Pair<String, HttpStatusCode> = OffFixtures.vegan to HttpStatusCode.OK

    private val repository =
        run {
            val config = NetworkConfig("https://off.test", "test", enableLogging = false)
            val engine =
                MockEngine {
                    networkCalls++
                    val (body, status) = nextResponse
                    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            DefaultProductRepository(
                api = OpenFoodFactsApi(createHttpClient(engine, config), config.openFoodFactsBaseUrl),
                cache = cache,
                json = NetworkJson,
                clock = clock,
                cacheTtl = 7.days,
            )
        }

    private val barcode = Barcode.parse(OffFixtures.VEGAN_BARCODE)!!

    @Test
    fun fetchesAndCachesFoundProduct() =
        runTest {
            val product = assertIs<AppResult.Success<Product?>>(repository.getProduct(barcode)).value

            assertEquals("Rice cakes con quinoa", product?.name)
            assertEquals(1, networkCalls)
            assertEquals(clock.now().toEpochMilliseconds(), cache.rows[barcode.value]?.fetchedAtEpochMs)
        }

    @Test
    fun freshCacheSkipsNetwork() =
        runTest {
            repository.getProduct(barcode)
            clock.advanceBy(6.days)

            repository.getProduct(barcode)

            assertEquals(1, networkCalls)
        }

    @Test
    fun expiredCacheRefetches() =
        runTest {
            repository.getProduct(barcode)
            clock.advanceBy(8.days)

            repository.getProduct(barcode)

            assertEquals(2, networkCalls)
        }

    @Test
    fun staleCacheIsServedWhenNetworkFails() =
        runTest {
            repository.getProduct(barcode)
            clock.advanceBy(30.days)
            nextResponse = "" to HttpStatusCode.ServiceUnavailable

            val product = assertIs<AppResult.Success<Product?>>(repository.getProduct(barcode)).value

            assertEquals("Rice cakes con quinoa", product?.name)
        }

    @Test
    fun networkFailureWithoutCacheIsFailure() =
        runTest {
            nextResponse = "" to HttpStatusCode.ServiceUnavailable

            val result = repository.getProduct(barcode)

            assertEquals(AppError.ServiceUnavailable, assertIs<AppResult.Failure>(result).error)
        }

    @Test
    fun notFoundIsNotCached() =
        runTest {
            nextResponse = OffFixtures.notFound to HttpStatusCode.NotFound
            val notFound = Barcode.parse(OffFixtures.NOT_FOUND_BARCODE)!!

            assertNull(assertIs<AppResult.Success<Product?>>(repository.getProduct(notFound)).value)
            assertNull(cache.rows[notFound.value])
        }

    @Test
    fun corruptedCacheEntryFallsBackToNetwork() =
        runTest {
            cache.upsert(
                CachedProductEntity(
                    barcode = barcode.value,
                    sourceJson = "{not json",
                    fetchedAtEpochMs = clock.now().toEpochMilliseconds(),
                ),
            )

            val product = assertIs<AppResult.Success<Product?>>(repository.getProduct(barcode)).value

            assertEquals("Rice cakes con quinoa", product?.name)
            assertEquals(1, networkCalls)
        }
}
