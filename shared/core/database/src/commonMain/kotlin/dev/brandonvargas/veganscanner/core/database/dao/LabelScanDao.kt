package dev.brandonvargas.veganscanner.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.brandonvargas.veganscanner.core.database.entity.LabelScanEntity

@Dao
interface LabelScanDao {
    @Query("SELECT * FROM label_scan WHERE barcode = :barcode")
    suspend fun get(barcode: String): LabelScanEntity?

    @Upsert
    suspend fun upsert(entity: LabelScanEntity)

    @Query("DELETE FROM label_scan WHERE barcode = :barcode")
    suspend fun delete(barcode: String)
}
