package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.Ingredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource

/**
 * Step 2: evaluate Open Food Facts' per-ingredient flags ourselves, including nested ingredients.
 * Catches cases the product-level analysis leaves open, and lists the doubtful ingredients otherwise.
 *
 * [overrides] applies the app's curated corrections by Open Food Facts ingredient id (e.g. `en:sugar` → vegan).
 */
class OffIngredientsResolver(
    private val overrides: (taxonomyId: String?) -> IngredientVeganStatus? = { null },
) : VerdictResolver {
    override suspend fun resolve(product: Product): ResolverResult {
        if (product.ingredients.isEmpty()) return ResolverResult.Inconclusive()

        val evaluated = IngredientVeganEvaluator.evaluate(product.ingredients, overrides)
        val nonVegan = evaluated.filter { (_, status) -> status == IngredientVeganStatus.NO }
        val doubtful =
            evaluated.filter { (_, status) ->
                status == IngredientVeganStatus.MAYBE || status == IngredientVeganStatus.UNKNOWN
            }

        return when {
            nonVegan.isNotEmpty() -> ResolverResult.Conclusive(verdict(VeganStatus.NON_VEGAN, nonVegan))
            doubtful.isEmpty() -> ResolverResult.Conclusive(verdict(VeganStatus.VEGAN, emptyList()))
            else -> ResolverResult.Inconclusive(verdict(VeganStatus.MAYBE_VEGAN, doubtful))
        }
    }

    private fun verdict(
        status: VeganStatus,
        flagged: List<Pair<Ingredient, IngredientVeganStatus>>,
    ) = VeganVerdict(
        status = status,
        source = VerdictSource.OPEN_FOOD_FACTS_INGREDIENTS,
        flaggedIngredients = flagged.map { (ingredient, s) -> FlaggedIngredient(ingredient.text, s) },
    )
}
