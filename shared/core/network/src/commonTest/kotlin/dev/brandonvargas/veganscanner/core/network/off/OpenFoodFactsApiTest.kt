package dev.brandonvargas.veganscanner.core.network.off

import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.network.NetworkConfig
import dev.brandonvargas.veganscanner.core.network.createHttpClient
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFoodFactsApiTest {
    private val config =
        NetworkConfig(
            openFoodFactsBaseUrl = "https://off.test",
            userAgent = "VeganScanner/test (tests)",
            enableLogging = false,
        )

    private fun api(handler: MockRequestHandler) =
        OpenFoodFactsApi(createHttpClient(MockEngine(handler), config), config.openFoodFactsBaseUrl)

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun sendsUserAgentAndRequestedFields() =
        runTest {
            var userAgent: String? = null
            var fields: String? = null
            var path: String? = null
            api { request ->
                userAgent = request.headers[HttpHeaders.UserAgent]
                fields = request.url.parameters["fields"]
                path = request.url.encodedPath
                respond(OffFixtures.vegan, HttpStatusCode.OK, jsonHeaders)
            }.fetchProduct(OffFixtures.VEGAN_BARCODE)

            assertEquals("VeganScanner/test (tests)", userAgent)
            assertEquals(OpenFoodFactsApi.FIELDS, fields)
            assertEquals("/api/v2/product/${OffFixtures.VEGAN_BARCODE}", path)
        }

    @Test
    fun parsesFoundProductIncludingNestedIngredients() =
        runTest {
            val result =
                api { respond(OffFixtures.nonVegan, HttpStatusCode.OK, jsonHeaders) }
                    .fetchProduct(OffFixtures.NON_VEGAN_BARCODE)

            val product = assertIs<AppResult.Success<OffProductDto?>>(result).value
            assertEquals("Nutella", product?.productName)
            assertTrue("en:non-vegan" in product!!.ingredientsAnalysisTags)
            val lecithin = product.ingredients.first { it.id == "en:e322" }
            assertEquals("yes", lecithin.ingredients.single().vegan)
        }

    @Test
    fun notFoundWith404IsSuccessWithNull() =
        runTest {
            val result =
                api { respond(OffFixtures.notFound, HttpStatusCode.NotFound, jsonHeaders) }
                    .fetchProduct(OffFixtures.NOT_FOUND_BARCODE)

            assertNull(assertIs<AppResult.Success<OffProductDto?>>(result).value)
        }

    @Test
    fun invalidCodeWith200IsSuccessWithNull() =
        runTest {
            val result =
                api { respond(OffFixtures.invalidCode, HttpStatusCode.OK, jsonHeaders) }
                    .fetchProduct("00000000")

            assertNull(assertIs<AppResult.Success<OffProductDto?>>(result).value)
        }

    @Test
    fun htmlMaintenancePageIsServiceUnavailable() =
        runTest {
            val html = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            val result =
                api { respond(OffFixtures.maintenanceHtml, HttpStatusCode.OK, html) }
                    .fetchProduct(OffFixtures.VEGAN_BARCODE)

            assertEquals(AppError.ServiceUnavailable, assertIs<AppResult.Failure>(result).error)
        }

    @Test
    fun serverErrorIsServiceUnavailable() =
        runTest {
            val result = api { respond("", HttpStatusCode.BadGateway) }.fetchProduct(OffFixtures.VEGAN_BARCODE)

            assertEquals(AppError.ServiceUnavailable, assertIs<AppResult.Failure>(result).error)
        }

    @Test
    fun tooManyRequestsIsRateLimited() =
        runTest {
            val result = api { respond("", HttpStatusCode.TooManyRequests) }.fetchProduct(OffFixtures.VEGAN_BARCODE)

            assertEquals(AppError.RateLimited, assertIs<AppResult.Failure>(result).error)
        }

    @Test
    fun ioFailureIsNetworkError() =
        runTest {
            val result = api { throw IOException("offline") }.fetchProduct(OffFixtures.VEGAN_BARCODE)

            assertEquals(AppError.Network, assertIs<AppResult.Failure>(result).error)
        }
}
