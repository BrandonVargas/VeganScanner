package dev.brandonvargas.veganscanner.feature.scanner.domain.research

import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.MAYBE
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.NO
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.UNKNOWN
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.YES
import dev.brandonvargas.veganscanner.core.model.SourceLink
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.feature.scanner.FakeIngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.FakeScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefineWithWebResearchUseCaseTest {
    private val repository = FakeIngredientResearchRepository()
    private val history = FakeScanHistoryRepository()
    private val useCase = RefineWithWebResearchUseCase(repository, history, TestClock(), deviceLanguage = "es")
    private val product = productFromFixture(OffFixtures.unknownStatusNoIngredients)

    private fun researched(name: String, status: IngredientVeganStatus) =
        ResearchedIngredient(
            name = name,
            key = name.lowercase(),
            status = status,
            reasonEn = "Reason in English",
            reasonEs = "Razón en español",
            sources = listOf(SourceLink("example.org", "https://example.org")),
        )

    private fun likely(vararg unknown: String) =
        VeganVerdict(
            VeganStatus.LIKELY_VEGAN,
            VerdictSource.RULE_ENGINE,
            unknown.map { FlaggedIngredient(it, UNKNOWN) },
        )

    @Test
    fun allUnrecognizedResearchedVeganMakesTheProductVegan() =
        runTest {
            repository.results = listOf(researched("Xantolina", YES), researched("Gelana", YES))

            val refined = useCase(product, likely("Xantolina", "Gelana"))!!

            assertEquals(VeganStatus.VEGAN, refined.status)
            assertEquals(VerdictSource.WEB_RESEARCH, refined.source)
            assertTrue(refined.flaggedIngredients.isEmpty())
            assertEquals(listOf("Xantolina", "Gelana"), refined.researched.map { it.name })
            assertEquals("Razón en español", refined.researched.first().note)
            assertEquals("xantolina", refined.researched.first().researchKey)
            assertEquals(VeganStatus.VEGAN, history.entries.value.single().status)
        }

    @Test
    fun researchedNonVeganMakesTheProductNonVegan() =
        runTest {
            repository.results = listOf(researched("Xantolina", YES), researched("Isinglás", NO))

            val refined = useCase(product, likely("Xantolina", "Isinglás"))!!

            assertEquals(VeganStatus.NON_VEGAN, refined.status)
            assertEquals(listOf("Isinglás"), refined.flaggedIngredients.map { it.name })
        }

    @Test
    fun researchedMaybeOrStillUnknownKeepsTheProductInconclusive() =
        runTest {
            repository.results = listOf(researched("Xantolina", MAYBE))

            val refined = useCase(product, likely("Xantolina", "Ruido OCR"))!!

            assertEquals(VeganStatus.MAYBE_VEGAN, refined.status)
            assertEquals(listOf(MAYBE, UNKNOWN), refined.flaggedIngredients.map { it.status })
        }

    @Test
    fun neverTouchesIngredientsTheDictionariesAlreadyJudged() =
        runTest {
            repository.results = listOf(researched("saborizantes naturales", YES), researched("Xantolina", YES))
            val verdict =
                VeganVerdict(
                    VeganStatus.MAYBE_VEGAN,
                    VerdictSource.RULE_ENGINE,
                    listOf(FlaggedIngredient("saborizantes naturales", MAYBE), FlaggedIngredient("Xantolina", UNKNOWN)),
                )

            val refined = useCase(product, verdict)!!

            assertEquals(listOf(listOf("Xantolina")), repository.requested)
            assertEquals(VeganStatus.MAYBE_VEGAN, refined.status)
            assertEquals(listOf("saborizantes naturales"), refined.flaggedIngredients.map { it.name })
        }

    @Test
    fun conclusiveVerdictsAndUnavailableResearchAreLeftAlone() =
        runTest {
            assertFalse(useCase.shouldResearch(VeganVerdict(VeganStatus.VEGAN, VerdictSource.RULE_ENGINE)))
            assertNull(useCase(product, VeganVerdict(VeganStatus.NON_VEGAN, VerdictSource.RULE_ENGINE)))

            val offline =
                RefineWithWebResearchUseCase(
                    FakeIngredientResearchRepository(isAvailable = false),
                    history,
                    TestClock(),
                    deviceLanguage = "en",
                )
            assertFalse(offline.shouldResearch(likely("Xantolina")))
        }

    @Test
    fun noResultsMeansNoChange() =
        runTest {
            assertNull(useCase(product, likely("Xantolina")))
            assertTrue(history.entries.value.isEmpty())
        }

    @Test
    fun researchesAtMostFiveIngredients() =
        runTest {
            useCase(product, likely("a1", "b2", "c3", "d4", "e5", "f6", "g7"))

            assertEquals(5, repository.requested.single().size)
        }
}
