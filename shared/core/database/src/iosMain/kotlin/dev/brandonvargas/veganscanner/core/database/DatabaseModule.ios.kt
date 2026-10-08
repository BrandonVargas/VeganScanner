package dev.brandonvargas.veganscanner.core.database

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual val databaseBuilderModule: Module =
    module {
        single { Room.databaseBuilder<VeganScannerDatabase>(name = databasePath()) }
    }

@OptIn(ExperimentalForeignApi::class)
private fun databasePath(): String {
    val documents =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(documents?.path) { "Documents directory unavailable" } + "/" + VeganScannerDatabase.FILE_NAME
}
