package dev.brandonvargas.veganscanner.core.supabase

import dev.brandonvargas.veganscanner.core.common.AppInfo
import dev.brandonvargas.veganscanner.core.common.BackendConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.module.Module
import org.koin.dsl.module

fun createVeganScannerSupabase(config: BackendConfig, engine: HttpClientEngine): SupabaseClient =
    createSupabaseClient(supabaseUrl = config.url, supabaseKey = config.publishableKey) {
        httpEngine = engine
        install(Auth)
        install(Functions)
        install(Postgrest)
    }

/**
 * Online features need a user for rate limiting and reports, but the app never asks people to sign up for the
 * scanner: a silent anonymous session is created on first use and persisted on the device.
 */
class AnonymousSession(private val client: SupabaseClient) {
    private val mutex = Mutex()

    suspend fun ensureSignedIn() =
        mutex.withLock {
            client.auth.awaitInitialization()
            if (client.auth.currentSessionOrNull() == null) client.auth.signInAnonymously()
        }
}

/** Only registered when the build has a Supabase project configured (see `AppInfo.backend`). */
fun supabaseModules(appInfo: AppInfo): List<Module> {
    val backend = appInfo.backend ?: return emptyList()
    return listOf(
        module {
            single { createVeganScannerSupabase(backend, engine = get()) }
            single { AnonymousSession(get()) }
        },
    )
}
