package dev.brandonvargas.veganscanner.umbrella

import dev.brandonvargas.veganscanner.core.common.AppInfo
import org.koin.core.context.startKoin

/** Called once from the SwiftUI `App` initializer. */
fun startVeganKit(versionName: String, isDebug: Boolean) {
    startKoin {
        modules(appModules(AppInfo(versionName = versionName, isDebug = isDebug, platform = "iOS")))
    }
}
