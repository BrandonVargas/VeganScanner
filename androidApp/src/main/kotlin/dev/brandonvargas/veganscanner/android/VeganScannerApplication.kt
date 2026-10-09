package dev.brandonvargas.veganscanner.android

import android.app.Application
import dev.brandonvargas.veganscanner.BuildConfig
import dev.brandonvargas.veganscanner.android.ai.GeminiNanoModel
import dev.brandonvargas.veganscanner.umbrella.startVeganKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VeganScannerApplication : Application() {
    /** Work that outlives screens, such as the one-time Gemini Nano download. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startVeganKit(
            context = this,
            versionName = BuildConfig.VERSION_NAME,
            isDebug = BuildConfig.DEBUG,
            supabaseHost = BuildConfig.SUPABASE_HOST,
            supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            onDeviceModel = GeminiNanoModel(appScope).also { it.prepare() },
        )
    }
}
