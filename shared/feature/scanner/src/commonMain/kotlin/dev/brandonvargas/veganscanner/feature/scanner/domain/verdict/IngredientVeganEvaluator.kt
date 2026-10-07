package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.Ingredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.MAYBE
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.NO
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.YES

/** Resolves the effective vegan status of an ingredient, taking its sub-ingredients into account. */
object IngredientVeganEvaluator {
    /**
     * - Any `no` in the ingredient or its children → `NO`.
     * - Every child `yes` → `YES` (e.g. "lecithin [maybe] (soy [yes])" is vegan).
     * - Any child `maybe` → `MAYBE`.
     * - Otherwise the ingredient's own flag; children without a flag (functional labels like
     *   "preservative") don't downgrade a parent that is already `yes`.
     */
    fun effectiveStatus(ingredient: Ingredient): IngredientVeganStatus {
        val own = ingredient.vegan
        val children = ingredient.subIngredients.map(::effectiveStatus)
        return when {
            own == NO || NO in children -> NO
            children.isEmpty() -> own
            children.all { it == YES } -> YES
            MAYBE in children -> MAYBE
            own == YES -> YES
            else -> own
        }
    }

    /** Top-level ingredients paired with their effective status. */
    fun evaluate(ingredients: List<Ingredient>): List<Pair<Ingredient, IngredientVeganStatus>> =
        ingredients.map { it to effectiveStatus(it) }
}
