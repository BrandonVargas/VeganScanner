package dev.brandonvargas.veganscanner.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ingredient text the user scanned (and reviewed) from a product label. Only the text is kept, never the photo. */
@Entity(tableName = "label_scan")
data class LabelScanEntity(
    @PrimaryKey val barcode: String,
    val ingredientsText: String,
    val scannedAtEpochMs: Long,
)
