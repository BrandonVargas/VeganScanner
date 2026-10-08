package dev.brandonvargas.veganscanner.core.database

import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides a `RoomDatabase.Builder<VeganScannerDatabase>` for the current platform. */
internal expect val databaseBuilderModule: Module

val databaseModule =
    module {
        includes(databaseBuilderModule)
        single { get<androidx.room.RoomDatabase.Builder<VeganScannerDatabase>>().buildDatabase() }
        single { get<VeganScannerDatabase>().productCacheDao() }
        single { get<VeganScannerDatabase>().scanHistoryDao() }
        single { get<VeganScannerDatabase>().labelScanDao() }
    }
