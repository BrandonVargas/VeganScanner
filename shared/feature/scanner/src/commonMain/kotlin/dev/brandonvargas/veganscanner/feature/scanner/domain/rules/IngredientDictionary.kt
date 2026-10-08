package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Animal-derived ingredients in English and Spanish, maintained as data in
 * `shared/feature/scanner/dictionary/ingredients.json` and compiled into the app at build time.
 */
@Serializable
internal data class IngredientDictionary(
    val version: Int,
    val entries: List<Entry>,
    /** Plant-based phrases that contain a flagged word, e.g. "leche de coco" or "manteca de cacao". */
    val plantBasedExceptions: Map<String, List<String>> = emptyMap(),
    /** Phrases introducing allergen cross-contamination warnings ("puede contener trazas de…"), not ingredients. */
    val crossContaminationMarkers: Map<String, List<String>> = emptyMap(),
) {
    @Serializable
    data class Entry(
        val id: String,
        val status: Status,
        val category: String,
        val terms: Map<String, List<String>>,
        val eNumbers: List<String> = emptyList(),
    )

    @Serializable
    enum class Status(val ingredientStatus: IngredientVeganStatus) {
        @SerialName("no")
        NO(IngredientVeganStatus.NO),

        @SerialName("maybe")
        MAYBE(IngredientVeganStatus.MAYBE),
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(source: String): IngredientDictionary = json.decodeFromString(serializer(), source)

        /** The dictionary bundled with the app. */
        val Bundled: IngredientDictionary by lazy { parse(INGREDIENT_DICTIONARY_JSON) }
    }
}
