package dev.brandonvargas.veganscanner.core.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.brandonvargas.veganscanner.core.database.dao.IngredientResearchDao
import dev.brandonvargas.veganscanner.core.database.dao.LabelScanDao
import dev.brandonvargas.veganscanner.core.database.dao.ProductCacheDao
import dev.brandonvargas.veganscanner.core.database.dao.ScanHistoryDao
import dev.brandonvargas.veganscanner.core.database.entity.CachedProductEntity
import dev.brandonvargas.veganscanner.core.database.entity.IngredientResearchEntity
import dev.brandonvargas.veganscanner.core.database.entity.LabelScanEntity
import dev.brandonvargas.veganscanner.core.database.entity.ScanHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        CachedProductEntity::class,
        ScanHistoryEntity::class,
        LabelScanEntity::class,
        IngredientResearchEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: label_scan table for ingredient text scanned from product labels.
        AutoMigration(from = 1, to = 2),
        // v3: ingredient_research, local copy of web-researched ingredients.
        AutoMigration(from = 2, to = 3),
    ],
)
@ConstructedBy(VeganScannerDatabaseConstructor::class)
abstract class VeganScannerDatabase : RoomDatabase() {
    abstract fun productCacheDao(): ProductCacheDao

    abstract fun scanHistoryDao(): ScanHistoryDao

    abstract fun labelScanDao(): LabelScanDao

    abstract fun ingredientResearchDao(): IngredientResearchDao

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
