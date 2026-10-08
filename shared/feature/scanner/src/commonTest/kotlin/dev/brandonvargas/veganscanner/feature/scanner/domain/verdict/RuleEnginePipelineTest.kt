package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.Ingredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientKnowledge
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The pipeline exactly as the app wires it: OFF analysis → OFF ingredients (with overrides) → rule engine. */
class RuleEnginePipelineTest {
    private val pipeline = appVerdictPipeline(IngredientKnowledge.Bundled)

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
    fun everyIngredientRecognizedAsVeganIsConclusive() =
        runTest {
            val product =
                productFromFixture(OffFixtures.unknownStatusNoIngredients)
                    .copy(ingredientsText = "Harina de trigo, agua, sal, aceite de girasol, azúcar")

            assertEquals(VeganVerdict(VeganStatus.VEGAN, VerdictSource.RULE_ENGINE), pipeline.evaluate(product))
        }

    @Test
    fun sugarOverrideResolvesDatabaseMaybe() =
        runTest {
            // OFF flags sugar "maybe"; the curated override makes it vegan, so only the flavouring stays doubtful.
            val verdict = pipeline.evaluate(productFromFixture(OffFixtures.maybeVegan))

            assertEquals(VeganStatus.MAYBE_VEGAN, verdict.status)
            assertEquals(listOf("saborizantes naturales"), verdict.flaggedIngredients.map { it.name })
        }

    @Test
    fun sugarOnlyDoubtIsVegan() =
        runTest {
            val product =
                productFromFixture(OffFixtures.maybeVegan).let { product ->
                    product.copy(
                        ingredients = product.ingredients.filterNot { it.id == "en:natural-flavouring" },
                        ingredientsText = "Harina de avena, azúcar, aceite de girasol",
                    )
                }

            val verdict = pipeline.evaluate(product)

            assertEquals(VeganVerdict(VeganStatus.VEGAN, VerdictSource.OPEN_FOOD_FACTS_INGREDIENTS), verdict)
        }

    @Test
    fun unrecognizedIngredientsWithCleanDictionaryAreLikelyVegan() =
        runTest {
            val product =
                productFromFixture(OffFixtures.unknownStatusNoIngredients).copy(
                    ingredientsText = "Frijol, agua, xantolina",
                    ingredients =
                        listOf(
                            Ingredient("en:bean", "Frijol", IngredientVeganStatus.YES),
                            Ingredient("en:water", "agua", IngredientVeganStatus.YES),
                            Ingredient(null, "xantolina", IngredientVeganStatus.UNKNOWN),
                        ),
                )

            val verdict = pipeline.evaluate(product)

            assertEquals(VeganStatus.LIKELY_VEGAN, verdict.status)
            assertEquals(
                listOf(FlaggedIngredient("xantolina", IngredientVeganStatus.UNKNOWN)),
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
            assertEquals(listOf("saborizantes naturales", "E471"), verdict.flaggedIngredients.map { it.name })
        }

    @Test
    fun conclusiveDatabaseAnswerIsNeverOverridden() =
        runTest {
            val product = productFromFixture(OffFixtures.vegan).copy(ingredientsText = "Arroz, leche")

            assertEquals(VeganStatus.VEGAN, pipeline.evaluate(product).status)
        }
}
