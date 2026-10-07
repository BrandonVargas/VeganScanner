package dev.brandonvargas.veganscanner.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.brandonvargas.veganscanner.core.database.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY scannedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScanHistoryEntity>>

    @Upsert
    suspend fun upsert(entity: ScanHistoryEntity)

    @Query("DELETE FROM scan_history WHERE barcode = :barcode")
    suspend fun delete(barcode: String)

    @Query("DELETE FROM scan_history")
    suspend fun clear()
}
