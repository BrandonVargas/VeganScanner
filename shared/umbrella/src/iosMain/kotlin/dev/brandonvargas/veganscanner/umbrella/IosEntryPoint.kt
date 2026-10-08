package dev.brandonvargas.veganscanner.umbrella

import dev.brandonvargas.veganscanner.core.common.AppInfo
import dev.brandonvargas.veganscanner.core.common.BackendConfig
import org.koin.core.context.startKoin

/** Called once from the SwiftUI `App` initializer. Online features are off when the Supabase settings are blank. */
fun startVeganKit(
    versionName: String,
    isDebug: Boolean,
    deviceLanguage: String,
    supabaseHost: String?,
    supabasePublishableKey: String?,
) {
    val appInfo =
        AppInfo(
            versionName = versionName,
            isDebug = isDebug,
            platform = "iOS",
            deviceLanguage = deviceLanguage,
            backend = BackendConfig.fromBuildSettings(supabaseHost, supabasePublishableKey),
        )
    startKoin {
        modules(appModules(appInfo))
    }
}
