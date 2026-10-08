package dev.brandonvargas.veganscanner.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per barcode; scanning again refreshes the verdict and timestamp. */
@Entity(tableName = "scan_history", indices = [Index("scannedAtEpochMs")])
data class ScanHistoryEntity(
    @PrimaryKey val barcode: String,
    val productName: String?,
    val brands: String?,
    val imageUrl: String?,
    /** [dev.brandonvargas.veganscanner.core.model.VeganStatus] name. */
    val status: String,
    /** [dev.brandonvargas.veganscanner.core.model.VerdictSource] name. */
    val source: String,
    val scannedAtEpochMs: Long,
)
