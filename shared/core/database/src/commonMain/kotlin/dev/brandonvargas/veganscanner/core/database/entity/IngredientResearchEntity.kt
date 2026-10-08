package dev.brandonvargas.veganscanner.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local copy of web-researched ingredients (shared server cache), so repeat scans don't need the network. */
@Entity(tableName = "ingredient_research")
data class IngredientResearchEntity(
    /** Folded ingredient name, same key as the server's `ingredient_knowledge.normalized_name`. */
    @PrimaryKey val normalizedName: String,
    val name: String,
    /** `vegan`, `non_vegan`, `maybe` or `unknown`. */
    val status: String,
    val reasonEn: String?,
    val reasonEs: String?,
    /** JSON array of `{title, url}`. */
    val sourcesJson: String,
    val fetchedAtEpochMs: Long,
)
