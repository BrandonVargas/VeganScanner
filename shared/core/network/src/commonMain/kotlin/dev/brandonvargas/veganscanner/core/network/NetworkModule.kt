package dev.brandonvargas.veganscanner.core.network

import dev.brandonvargas.veganscanner.core.network.off.OpenFoodFactsApi
import org.koin.dsl.module

/** Requires a [NetworkConfig] to be provided by the app module. */
val networkModule =
    module {
        single { defaultHttpEngine() }
        single { createHttpClient(engine = get(), config = get()) }
        single { OpenFoodFactsApi(client = get(), baseUrl = get<NetworkConfig>().openFoodFactsBaseUrl) }
    }
