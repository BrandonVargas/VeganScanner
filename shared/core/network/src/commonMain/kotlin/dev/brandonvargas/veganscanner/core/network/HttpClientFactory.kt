package dev.brandonvargas.veganscanner.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger as KermitLogger

/** Platform engine: OkHttp on Android, NSURLSession (Darwin) on iOS. */
expect fun defaultHttpEngine(): HttpClientEngine

val NetworkJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

fun createHttpClient(
    engine: HttpClientEngine,
    config: NetworkConfig,
    json: Json = NetworkJson,
): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(UserAgent) { agent = config.userAgent }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
        if (config.enableLogging) {
            install(Logging) {
                level = LogLevel.INFO
                logger =
                    object : Logger {
                        private val log = KermitLogger.withTag("Http")

                        override fun log(message: String) = log.d { message }
                    }
            }
        }
    }
