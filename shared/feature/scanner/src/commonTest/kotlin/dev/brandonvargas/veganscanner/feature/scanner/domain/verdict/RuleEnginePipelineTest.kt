package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.Ingredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientDictionary
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientRuleEngine
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The full pipeline as wired in the app: OFF analysis → OFF ingredients → rule engine. */
class RuleEnginePipelineTest {
    private val pipeline =
        VerdictPipeline(
            listOf(
                OffAnalysisResolver(),
                OffIngredientsResolver(),
                RuleEngineResolver(IngredientRuleEngine(IngredientDictionary.Bundled)),
            ),
        )

    @Test
    fun dictionaryCatchesWhatTheDatabaseDidNotRecognize() =
        runTest {
            val product =
                productFromFixture(OffFixtures.unknownStatusNoIngredients)
                    .copy(ingredientsText = "Agua, azúcar, grenetina")

            val verdict = pipeline.evaluate(product)

            assertEquals(VeganStatus.NON_VEGAN, verdict.status)
            assertEquals(VerdictSource.RULE_ENGINE, verdict.source)
            assertEquals(listOf(FlaggedIngredient("grenetina", IngredientVeganStatus.NO)), verdict.flaggedIngredients)
        }

    @Test
    fun cleanDictionaryCheckDoesNotHideDatabaseDoubts() =
        runTest {
            // OFF flags sugar and natural flavours as "maybe"; the dictionary's silence must not override that.
            val verdict = pipeline.evaluate(productFromFixture(OffFixtures.maybeVegan))

            assertEquals(VeganStatus.MAYBE_VEGAN, verdict.status)
            assertEquals(
                listOf("azúcar", "saborizantes naturales"),
                verdict.flaggedIngredients.map { it.name },
            )
        }

    @Test
    fun unrecognizedIngredientsWithCleanDictionaryAreLikelyVegan() =
        runTest {
            val product =
                productFromFixture(OffFixtures.unknownStatusNoIngredients).copy(
                    ingredientsText = "Frijol, agua, colorante rojo 40",
                    ingredients =
                        listOf(
                            Ingredient("en:bean", "Frijol", IngredientVeganStatus.YES),
                            Ingredient("en:water", "agua", IngredientVeganStatus.YES),
                            Ingredient(null, "colorante rojo 40", IngredientVeganStatus.UNKNOWN),
                        ),
                )

            val verdict = pipeline.evaluate(product)

            assertEquals(VeganStatus.LIKELY_VEGAN, verdict.status)
            assertEquals(
                listOf(FlaggedIngredient("colorante rojo 40", IngredientVeganStatus.UNKNOWN)),
                verdict.flaggedIngredients,
            )
        }

    @Test
    fun dictionaryAndDatabaseDoubtsAreMerged() =
        runTest {
            val product =
                productFromFixture(OffFixtures.maybeVegan)
                    .copy(ingredientsText = "Harina de avena, azúcar, aceite de girasol, saborizantes naturales, E471")

            val verdict = pipeline.evaluate(product)

            assertEquals(VeganStatus.MAYBE_VEGAN, verdict.status)
            assertEquals(
                listOf("azúcar", "saborizantes naturales", "E471"),
                verdict.flaggedIngredients.map { it.name },
            )
        }

    @Test
    fun conclusiveDatabaseAnswerIsNeverOverridden() =
        runTest {
            val product = productFromFixture(OffFixtures.vegan).copy(ingredientsText = "Arroz, leche")

            assertEquals(VeganStatus.VEGAN, pipeline.evaluate(product).status)
        }
}
