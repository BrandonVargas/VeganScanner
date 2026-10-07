package dev.brandonvargas.veganscanner.android

import android.app.Application
import dev.brandonvargas.veganscanner.BuildConfig
import dev.brandonvargas.veganscanner.umbrella.startVeganKit

class VeganScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startVeganKit(context = this, versionName = BuildConfig.VERSION_NAME, isDebug = BuildConfig.DEBUG)
    }
}
