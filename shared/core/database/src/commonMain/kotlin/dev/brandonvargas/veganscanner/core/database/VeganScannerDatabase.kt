package dev.brandonvargas.veganscanner.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.brandonvargas.veganscanner.core.database.dao.ProductCacheDao
import dev.brandonvargas.veganscanner.core.database.dao.ScanHistoryDao
import dev.brandonvargas.veganscanner.core.database.entity.CachedProductEntity
import dev.brandonvargas.veganscanner.core.database.entity.ScanHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [CachedProductEntity::class, ScanHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(VeganScannerDatabaseConstructor::class)
abstract class VeganScannerDatabase : RoomDatabase() {
    abstract fun productCacheDao(): ProductCacheDao

    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        const val FILE_NAME = "vegan_scanner.db"
    }
}

// Room generates the actual implementations for each platform.
@Suppress("KotlinNoActualForExpect")
expect object VeganScannerDatabaseConstructor : RoomDatabaseConstructor<VeganScannerDatabase> {
    override fun initialize(): VeganScannerDatabase
}

fun RoomDatabase.Builder<VeganScannerDatabase>.buildDatabase(): VeganScannerDatabase =
    this
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
