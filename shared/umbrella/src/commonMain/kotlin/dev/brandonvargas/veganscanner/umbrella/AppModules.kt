package dev.brandonvargas.veganscanner.umbrella

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import dev.brandonvargas.veganscanner.core.common.AppInfo
import dev.brandonvargas.veganscanner.core.common.commonModule
import dev.brandonvargas.veganscanner.core.database.databaseModule
import dev.brandonvargas.veganscanner.core.network.NetworkConfig
import dev.brandonvargas.veganscanner.core.network.networkModule
import dev.brandonvargas.veganscanner.feature.scanner.di.scannerModule
import org.koin.core.module.Module
import org.koin.dsl.module

/** Every Koin module the app needs. Both platforms start Koin with this list. */
fun appModules(appInfo: AppInfo): List<Module> {
    Logger.setMinSeverity(if (appInfo.isDebug) Severity.Debug else Severity.Warn)
    val configModule =
        module {
            single { appInfo }
            single { NetworkConfig.from(appInfo) }
        }
    return listOf(configModule, commonModule, networkModule, databaseModule, scannerModule)
}
