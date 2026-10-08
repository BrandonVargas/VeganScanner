package dev.brandonvargas.veganscanner.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.brandonvargas.veganscanner.core.database.entity.CachedProductEntity

@Dao
interface ProductCacheDao {
    @Query("SELECT * FROM cached_product WHERE barcode = :barcode")
    suspend fun get(barcode: String): CachedProductEntity?

    @Upsert
    suspend fun upsert(entity: CachedProductEntity)

    @Query("DELETE FROM cached_product WHERE fetchedAtEpochMs < :olderThanEpochMs")
    suspend fun deleteOlderThan(olderThanEpochMs: Long)
}
