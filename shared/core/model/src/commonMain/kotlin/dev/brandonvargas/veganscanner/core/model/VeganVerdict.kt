package dev.brandonvargas.veganscanner.core.model

enum class VeganStatus {
    VEGAN,
    NON_VEGAN,
    MAYBE_VEGAN,

    /** No animal-derived ingredient was found, but nothing confirmed the product is vegan either. */
    LIKELY_VEGAN,
    UNKNOWN,
}

/** Which step of the verdict pipeline produced the final answer. Shown to users as "why". */
enum class VerdictSource {
    /** Open Food Facts' product-level `ingredients_analysis_tags`. */
    OPEN_FOOD_FACTS,

    /** The app's evaluation of Open Food Facts' per-ingredient vegan flags. */
    OPEN_FOOD_FACTS_INGREDIENTS,

    /** The bundled ingredient dictionary applied to the product's ingredient list. */
    RULE_ENGINE,

    /** The ingredient dictionary applied to text the user scanned from the product label. */
    LABEL_SCAN,

    /** Nothing could decide; the verdict is a best-effort partial result. */
    UNDETERMINED,
}

data class FlaggedIngredient(
    val name: String,
    val status: IngredientVeganStatus,
)

data class VeganVerdict(
    val status: VeganStatus,
    val source: VerdictSource,
    /** Ingredients explaining the verdict: non-vegan ones for NON_VEGAN, doubtful ones for MAYBE/UNKNOWN. */
    val flaggedIngredients: List<FlaggedIngredient> = emptyList(),
) {
    val isConclusive: Boolean
        get() = status == VeganStatus.VEGAN || status == VeganStatus.NON_VEGAN

    companion object {
        val Unknown = VeganVerdict(VeganStatus.UNKNOWN, VerdictSource.UNDETERMINED)
    }
}
