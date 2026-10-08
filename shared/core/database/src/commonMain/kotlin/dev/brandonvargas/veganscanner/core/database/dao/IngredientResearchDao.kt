package dev.brandonvargas.veganscanner.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.brandonvargas.veganscanner.core.database.entity.IngredientResearchEntity

@Dao
interface IngredientResearchDao {
    @Query("SELECT * FROM ingredient_research WHERE normalizedName IN (:normalizedNames)")
    suspend fun get(normalizedNames: List<String>): List<IngredientResearchEntity>

    @Upsert
    suspend fun upsert(entities: List<IngredientResearchEntity>)

    @Query("DELETE FROM ingredient_research WHERE normalizedName = :normalizedName")
    suspend fun delete(normalizedName: String)
}
