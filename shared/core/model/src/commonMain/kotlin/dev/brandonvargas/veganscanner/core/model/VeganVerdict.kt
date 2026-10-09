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

    /** Unrecognized ingredients were researched on the web by AI (Gemini + web search). Show with a warning. */
    WEB_RESEARCH,

    /** Online research wasn't possible, so the phone's own AI model estimated the ingredients. Show with a warning. */
    ON_DEVICE_AI,

    /** Nothing could decide; the verdict is a best-effort partial result. */
    UNDETERMINED,
}

data class FlaggedIngredient(
    val name: String,
    val status: IngredientVeganStatus,
    /** Short explanation, when one is available (e.g. from web research). */
    val note: String? = null,
    val sources: List<SourceLink> = emptyList(),
    /** Key of the shared web-research entry, so users can report it as wrong. `null` when not web-researched. */
    val researchKey: String? = null,
    /** Estimated by the phone's own AI model rather than researched online. */
    val onDevice: Boolean = false,
)

data class SourceLink(val title: String, val url: String)

data class VeganVerdict(
    val status: VeganStatus,
    val source: VerdictSource,
    /** Ingredients explaining the verdict: non-vegan ones for NON_VEGAN, doubtful ones for MAYBE/UNKNOWN. */
    val flaggedIngredients: List<FlaggedIngredient> = emptyList(),
    /** Ingredients resolved by AI web research (any status), shown with their reasons, sources and a report option. */
    val researched: List<FlaggedIngredient> = emptyList(),
) {
    val isConclusive: Boolean
        get() = status == VeganStatus.VEGAN || status == VeganStatus.NON_VEGAN

    companion object {
        val Unknown = VeganVerdict(VeganStatus.UNKNOWN, VerdictSource.UNDETERMINED)
    }
}
