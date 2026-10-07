package dev.brandonvargas.veganscanner.core.network.off

import co.touchlab.kermit.Logger
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Client for the Open Food Facts product API (v2).
 * Docs: https://openfoodfacts.github.io/openfoodfacts-server/api/
 */
class OpenFoodFactsApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private val log = Logger.withTag("OpenFoodFactsApi")

    /** Returns the product, `null` when Open Food Facts doesn't know the barcode, or a failure. */
    suspend fun fetchProduct(barcode: String): AppResult<OffProductDto?> =
        try {
            val response =
                client.get("$baseUrl/api/v2/product/$barcode") {
                    parameter("fields", FIELDS)
                }
            val isJson = response.contentType()?.match(ContentType.Application.Json) == true
            when {
                response.status == HttpStatusCode.TooManyRequests -> {
                    AppResult.Failure(AppError.RateLimited)
                }

                response.status.value >= 500 -> {
                    AppResult.Failure(AppError.ServiceUnavailable)
                }

                // OFF serves an HTML "temporarily unavailable" page when overloaded.
                !isJson -> {
                    AppResult.Failure(AppError.ServiceUnavailable)
                }

                else -> {
                    val body = response.body<OffProductResponseDto>()
                    AppResult.Success(body.product.takeIf { body.status == STATUS_FOUND })
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            log.w(e) { "Timeout fetching $barcode" }
            AppResult.Failure(AppError.Network)
        } catch (e: IOException) {
            log.w(e) { "I/O error fetching $barcode" }
            AppResult.Failure(AppError.Network)
        } catch (e: SerializationException) {
            log.e(e) { "Malformed response for $barcode" }
            AppResult.Failure(AppError.ServiceUnavailable)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.e(e) { "Unexpected error fetching $barcode" }
            AppResult.Failure(AppError.Unexpected(e.message))
        }

    companion object {
        private const val STATUS_FOUND = 1
        val FIELDS =
            listOf(
                "product_name",
                "brands",
                "image_front_url",
                "ingredients_text",
                "ingredients",
                "ingredients_analysis_tags",
                "lang",
            ).joinToString(",")
    }
}
