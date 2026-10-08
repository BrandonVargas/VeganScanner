package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.IngredientsSource
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientRuleEngine
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.RuleMatch

/**
 * Step 3: the bundled en/es dictionary applied to the raw ingredient text, from the database or a scanned label.
 *
 * - Any animal-derived ingredient → conclusive NON_VEGAN (catches what the database didn't recognize).
 * - Doubtful ingredients → MAYBE_VEGAN with the reasons.
 * - Nothing found → LIKELY_VEGAN. Never conclusive: a dictionary can't prove a product is vegan.
 */
internal class RuleEngineResolver(private val engine: IngredientRuleEngine) : VerdictResolver {
    override suspend fun resolve(product: Product): ResolverResult {
        val text = product.ingredientsText?.takeIf { it.isNotBlank() } ?: return ResolverResult.Inconclusive()
        val source =
            when (product.ingredientsSource) {
                IngredientsSource.LABEL_SCAN -> VerdictSource.LABEL_SCAN
                IngredientsSource.OPEN_FOOD_FACTS -> VerdictSource.RULE_ENGINE
            }
        val matches = engine.analyze(text)
        val nonVegan = matches.filter { it.status == IngredientVeganStatus.NO }
        val doubtful = matches.filter { it.status == IngredientVeganStatus.MAYBE }

        fun verdict(status: VeganStatus, flagged: List<RuleMatch>) =
            VeganVerdict(status, source, flagged.map { FlaggedIngredient(it.text, it.status) })

        return when {
            nonVegan.isNotEmpty() -> ResolverResult.Conclusive(verdict(VeganStatus.NON_VEGAN, nonVegan))
            doubtful.isNotEmpty() -> ResolverResult.Inconclusive(verdict(VeganStatus.MAYBE_VEGAN, doubtful))
            else -> ResolverResult.Inconclusive(verdict(VeganStatus.LIKELY_VEGAN, emptyList()))
        }
    }
}
