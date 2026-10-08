package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource

/**
 * Step 1: trust Open Food Facts' own product-level analysis when it is definitive.
 * For non-vegan products the offending ingredients are attached so the UI can explain why.
 */
class OffAnalysisResolver : VerdictResolver {
    override suspend fun resolve(product: Product): ResolverResult =
        when (product.sourceAnalysis) {
            VeganStatus.VEGAN -> {
                ResolverResult.Conclusive(
                    VeganVerdict(VeganStatus.VEGAN, VerdictSource.OPEN_FOOD_FACTS),
                )
            }

            VeganStatus.NON_VEGAN -> {
                ResolverResult.Conclusive(
                    VeganVerdict(
                        status = VeganStatus.NON_VEGAN,
                        source = VerdictSource.OPEN_FOOD_FACTS,
                        flaggedIngredients =
                            IngredientVeganEvaluator.evaluate(product.ingredients)
                                .filter { (_, status) -> status == IngredientVeganStatus.NO }
                                .map { (ingredient, status) -> FlaggedIngredient(ingredient.text, status) },
                    ),
                )
            }

            VeganStatus.MAYBE_VEGAN, VeganStatus.LIKELY_VEGAN, VeganStatus.UNKNOWN -> {
                ResolverResult.Inconclusive(
                    VeganVerdict(product.sourceAnalysis, VerdictSource.OPEN_FOOD_FACTS),
                )
            }
        }
}
