package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientKnowledge
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientRuleEngine

/**
 * The verdict pipeline the app runs, in order. Used by Koin and by tests, so both always exercise the same steps.
 * See docs/adr/0003-verdict-pipeline.md.
 */
internal fun appVerdictPipeline(knowledge: IngredientKnowledge): VerdictPipeline =
    VerdictPipeline(
        resolvers =
            listOf(
                OffAnalysisResolver(),
                OffIngredientsResolver(overrides = knowledge::overrideFor),
                RuleEngineResolver(IngredientRuleEngine(knowledge)),
            ),
    )
