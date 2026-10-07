package dev.brandonvargas.veganscanner.umbrella

import android.content.Context
import dev.brandonvargas.veganscanner.core.common.AppInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

fun startVeganKit(context: Context, versionName: String, isDebug: Boolean) {
    startKoin {
        androidContext(context)
        modules(appModules(AppInfo(versionName = versionName, isDebug = isDebug, platform = "Android")))
    }
}
