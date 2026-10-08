package dev.brandonvargas.veganscanner.core.database

import android.content.Context
import androidx.room.Room
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val databaseBuilderModule: Module =
    module {
        single {
            val context = get<Context>().applicationContext
            Room.databaseBuilder<VeganScannerDatabase>(
                context = context,
                name = context.getDatabasePath(VeganScannerDatabase.FILE_NAME).absolutePath,
            )
        }
    }
