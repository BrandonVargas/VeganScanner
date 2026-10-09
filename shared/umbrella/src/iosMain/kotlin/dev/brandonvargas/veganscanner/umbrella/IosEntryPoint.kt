package dev.brandonvargas.veganscanner.umbrella

import dev.brandonvargas.veganscanner.core.common.AppInfo
import dev.brandonvargas.veganscanner.core.common.BackendConfig
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.OnDeviceLanguageModel
import org.koin.core.context.startKoin

/**
 * Called once from the SwiftUI `App` initializer. Online features are off when the Supabase settings are blank.
 * [onDeviceModel] wraps Apple Foundation Models (implemented in Swift); `nil` where the framework isn't available.
 */
fun startVeganKit(
    versionName: String,
    isDebug: Boolean,
    deviceLanguage: String,
    supabaseHost: String?,
    supabasePublishableKey: String?,
    onDeviceModel: OnDeviceLanguageModel?,
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
        modules(appModules(appInfo, onDeviceModel))
    }
}
