package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerdictPipelineTest {
    private val pipeline = VerdictPipeline(listOf(OffAnalysisResolver(), OffIngredientsResolver()))

    @Test
    fun offVeganTagIsConclusive() =
        runTest {
            val verdict = pipeline.evaluate(productFromFixture(OffFixtures.vegan))

            assertEquals(VeganVerdict(VeganStatus.VEGAN, VerdictSource.OPEN_FOOD_FACTS), verdict)
        }

    @Test
    fun offNonVeganTagExplainsWhichIngredients() =
        runTest {
            val verdict = pipeline.evaluate(productFromFixture(OffFixtures.nonVegan))

            assertEquals(VeganStatus.NON_VEGAN, verdict.status)
            assertEquals(VerdictSource.OPEN_FOOD_FACTS, verdict.source)
            assertEquals(
                listOf("LAIT écrémé en poudre", "LACTOSERUM en poudre"),
                verdict.flaggedIngredients.map(FlaggedIngredient::name),
            )
        }

    @Test
    fun maybeVeganStaysInconclusiveAndListsDoubtfulIngredients() =
        runTest {
            val verdict = pipeline.evaluate(productFromFixture(OffFixtures.maybeVegan))

            assertEquals(VeganStatus.MAYBE_VEGAN, verdict.status)
            assertEquals(VerdictSource.OPEN_FOOD_FACTS_INGREDIENTS, verdict.source)
            assertEquals(
                listOf(
                    FlaggedIngredient("azúcar", IngredientVeganStatus.MAYBE),
                    FlaggedIngredient("saborizantes naturales", IngredientVeganStatus.MAYBE),
                ),
                verdict.flaggedIngredients,
            )
            assertTrue(!verdict.isConclusive)
        }

    @Test
    fun missingAnalysisFallsBackToIngredientFlags() =
        runTest {
            val verdict = pipeline.evaluate(productFromFixture(OffFixtures.noAnalysisAllIngredientsVegan))

            assertEquals(VeganVerdict(VeganStatus.VEGAN, VerdictSource.OPEN_FOOD_FACTS_INGREDIENTS), verdict)
        }

    @Test
    fun noIngredientsAndUnknownStatusIsUnknown() =
        runTest {
            val verdict = pipeline.evaluate(productFromFixture(OffFixtures.unknownStatusNoIngredients))

            assertEquals(VeganStatus.UNKNOWN, verdict.status)
            assertTrue(verdict.flaggedIngredients.isEmpty())
        }

    @Test
    fun emptyPipelineReturnsUnknown() =
        runTest {
            val verdict = VerdictPipeline(emptyList()).evaluate(productFromFixture(OffFixtures.vegan))

            assertEquals(VeganVerdict.Unknown, verdict)
        }

    @Test
    fun stopsAtFirstConclusiveResolver() =
        runTest {
            var secondCalled = false
            val pipeline =
                VerdictPipeline(
                    listOf(
                        VerdictResolver {
                            ResolverResult.Conclusive(VeganVerdict(VeganStatus.VEGAN, VerdictSource.UNDETERMINED))
                        },
                        VerdictResolver {
                            secondCalled = true
                            ResolverResult.Inconclusive()
                        },
                    ),
                )

            pipeline.evaluate(productFromFixture(OffFixtures.vegan))

            assertTrue(!secondCalled)
        }
}
