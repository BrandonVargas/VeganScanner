package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.IngredientsSource
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientRuleEngine

/**
 * Step 3: the curated dictionary plus the Open Food Facts taxonomy, applied to the raw ingredient text
 * from the database or a scanned label.
 *
 * - Any animal-derived ingredient → conclusive NON_VEGAN.
 * - Every ingredient recognized as vegan → conclusive VEGAN.
 * - Doubtful ingredients → MAYBE_VEGAN; only unrecognized ones → LIKELY_VEGAN. Both list what's unclear,
 *   which is exactly what later steps (web research, on-device AI) need.
 */
internal class RuleEngineResolver(private val engine: IngredientRuleEngine) : VerdictResolver {
    override suspend fun resolve(product: Product): ResolverResult {
        val text = product.ingredientsText?.takeIf { it.isNotBlank() } ?: return ResolverResult.Inconclusive()
        val source =
            when (product.ingredientsSource) {
                IngredientsSource.LABEL_SCAN -> VerdictSource.LABEL_SCAN
                IngredientsSource.OPEN_FOOD_FACTS -> VerdictSource.RULE_ENGINE
            }
        val analysis = engine.analyze(text)
        val nonVegan = analysis.flagged.filter { it.status == IngredientVeganStatus.NO }
        val doubtful =
            analysis.flagged.filter { it.status == IngredientVeganStatus.MAYBE }
                .map { FlaggedIngredient(it.text, it.status) }
        val unrecognized =
            analysis.unrecognized.take(MAX_UNRECOGNIZED)
                .map { FlaggedIngredient(it.text, IngredientVeganStatus.UNKNOWN) }
                .distinctBy { it.name.lowercase() }

        fun verdict(status: VeganStatus, flagged: List<FlaggedIngredient>) = VeganVerdict(status, source, flagged)

        return when {
            nonVegan.isNotEmpty() -> {
                ResolverResult.Conclusive(
                    verdict(VeganStatus.NON_VEGAN, nonVegan.map { FlaggedIngredient(it.text, it.status) }),
                )
            }

            doubtful.isNotEmpty() -> {
                ResolverResult.Inconclusive(
                    verdict(
                        VeganStatus.MAYBE_VEGAN,
                        doubtful + unrecognized,
                    ),
                )
            }

            unrecognized.isNotEmpty() -> {
                ResolverResult.Inconclusive(verdict(VeganStatus.LIKELY_VEGAN, unrecognized))
            }

            analysis.allVegan -> {
                ResolverResult.Conclusive(verdict(VeganStatus.VEGAN, emptyList()))
            }

            else -> {
                ResolverResult.Inconclusive()
            }
        }
    }

    private companion object {
        const val MAX_UNRECOGNIZED = 10
    }
}
