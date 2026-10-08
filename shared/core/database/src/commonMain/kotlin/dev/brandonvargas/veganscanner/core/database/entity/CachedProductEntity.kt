package dev.brandonvargas.veganscanner.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Raw Open Food Facts product JSON cached for offline use; mapping happens in the feature layer. */
@Entity(tableName = "cached_product")
data class CachedProductEntity(
    @PrimaryKey val barcode: String,
    val sourceJson: String,
    val fetchedAtEpochMs: Long,
)
