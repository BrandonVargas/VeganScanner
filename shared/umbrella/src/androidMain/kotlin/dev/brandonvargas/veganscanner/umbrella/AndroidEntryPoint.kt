package dev.brandonvargas.veganscanner.umbrella

import android.content.Context
import dev.brandonvargas.veganscanner.core.common.AppInfo
import dev.brandonvargas.veganscanner.core.common.BackendConfig
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.OnDeviceLanguageModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.Locale

/**
 * Starts shared code. Online features are off when the Supabase settings are blank (e.g. in forks).
 * [onDeviceModel] wraps Gemini Nano (ML Kit Prompt API), implemented in the app.
 */
fun startVeganKit(
    context: Context,
    versionName: String,
    isDebug: Boolean,
    supabaseHost: String?,
    supabasePublishableKey: String?,
    onDeviceModel: OnDeviceLanguageModel? = null,
) {
    val appInfo =
        AppInfo(
            versionName = versionName,
            isDebug = isDebug,
            platform = "Android",
            deviceLanguage = Locale.getDefault().language,
            backend = BackendConfig.fromBuildSettings(supabaseHost, supabasePublishableKey),
        )
    startKoin {
        androidContext(context)
        modules(appModules(appInfo, onDeviceModel))
    }
}
