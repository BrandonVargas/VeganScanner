package dev.brandonvargas.veganscanner.feature.scanner.domain.research

import dev.brandonvargas.veganscanner.core.common.AppError
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

            val refined = useCase(product, likely("Xantolina", "Gelana")).verdict!!

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

            val refined = useCase(product, likely("Xantolina", "Isinglás")).verdict!!

            assertEquals(VeganStatus.NON_VEGAN, refined.status)
            assertEquals(listOf("Isinglás"), refined.flaggedIngredients.map { it.name })
        }

    @Test
    fun researchedMaybeOrStillUnknownKeepsTheProductInconclusive() =
        runTest {
            repository.results = listOf(researched("Xantolina", MAYBE))

            val refined = useCase(product, likely("Xantolina", "Ruido OCR")).verdict!!

            assertEquals(VeganStatus.MAYBE_VEGAN, refined.status)
            assertEquals(listOf(MAYBE, UNKNOWN), refined.flaggedIngredients.map { it.status })
        }

    @Test
    fun judgedIngredientsAreExplainedButNeverChanged() =
        runTest {
            repository.results =
                listOf(
                    researched("saborizantes naturales", MAYBE),
                    researched("grenetina", NO),
                    researched("Xantolina", YES),
                )
            val verdict =
                VeganVerdict(
                    VeganStatus.MAYBE_VEGAN,
                    VerdictSource.RULE_ENGINE,
                    listOf(
                        FlaggedIngredient("saborizantes naturales", MAYBE),
                        FlaggedIngredient("grenetina", NO),
                        FlaggedIngredient("Xantolina", UNKNOWN),
                    ),
                )

            val refined = useCase(product, verdict).verdict!!

            assertEquals(listOf(listOf("Xantolina", "saborizantes naturales", "grenetina")), repository.requested)
            assertEquals(VeganStatus.NON_VEGAN, refined.status)
            val gelatin = refined.flaggedIngredients.single()
            assertEquals(NO, gelatin.status)
            assertEquals("Razón en español", gelatin.note)
            assertEquals("grenetina", gelatin.researchKey)
            assertEquals(listOf("Xantolina"), refined.researched.map { it.name })
        }

    @Test
    fun explanationsKeepTheVerdictAndItsSource() =
        runTest {
            repository.results = listOf(researched("Vitamina A", MAYBE))
            val verdict =
                VeganVerdict(
                    VeganStatus.MAYBE_VEGAN,
                    VerdictSource.RULE_ENGINE,
                    listOf(FlaggedIngredient("Vitamina A", MAYBE)),
                )

            val refined = useCase(product, verdict).verdict!!

            assertEquals(verdict.copy(flaggedIngredients = listOf(refined.flaggedIngredients.single())), refined)
            assertEquals(MAYBE, refined.flaggedIngredients.single().status)
            assertEquals("Razón en español", refined.flaggedIngredients.single().note)
            assertTrue(refined.researched.isEmpty())
        }

    @Test
    fun explanationsThatDisagreeAreNotShown() =
        runTest {
            repository.results = listOf(researched("grenetina", YES))
            val verdict =
                VeganVerdict(
                    VeganStatus.NON_VEGAN,
                    VerdictSource.RULE_ENGINE,
                    listOf(FlaggedIngredient("grenetina", NO)),
                )

            assertNull(useCase(product, verdict).verdict)
            assertTrue(history.entries.value.isEmpty())
        }

    @Test
    fun conclusiveNonVeganVerdictsAreExplainedToo() =
        runTest {
            val verdict =
                VeganVerdict(
                    VeganStatus.NON_VEGAN,
                    VerdictSource.OPEN_FOOD_FACTS,
                    listOf(FlaggedIngredient("miel", NO)),
                )

            assertTrue(useCase.shouldResearch(verdict))
            assertFalse(
                useCase.shouldResearch(
                    verdict.copy(flaggedIngredients = listOf(FlaggedIngredient("miel", NO, note = "x"))),
                ),
            )
        }

    @Test
    fun conclusiveVerdictsAndUnavailableResearchAreLeftAlone() =
        runTest {
            assertFalse(useCase.shouldResearch(VeganVerdict(VeganStatus.VEGAN, VerdictSource.RULE_ENGINE)))
            assertEquals(
                ResearchRefinement.Unchanged,
                useCase(product, VeganVerdict(VeganStatus.NON_VEGAN, VerdictSource.RULE_ENGINE)),
            )

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
            assertNull(useCase(product, likely("Xantolina")).verdict)
            assertTrue(history.entries.value.isEmpty())
        }

    @Test
    fun researchesEveryUnrecognizedIngredientInBatchesOfFive() =
        runTest {
            repository.results = listOf(researched("f6", YES))

            val refinement = useCase(product, likely("a1", "b2", "c3", "d4", "e5", "f6", "g7"))

            assertEquals(listOf(5, 2), repository.requested.map { it.size })
            assertEquals(listOf("f6"), refinement.verdict?.researched?.map { it.name })
        }

    @Test
    fun researchesAtMostFifteenIngredientsPerScan() =
        runTest {
            useCase(product, likely(*Array(20) { "item$it" }))

            assertEquals(15, repository.requested.flatten().size)
        }

    @Test
    fun offlineIsReportedWithTheNumberOfIngredientsNotLookedUp() =
        runTest {
            repository.failure = AppError.Network

            val refinement = useCase(product, likely("Xantolina", "Gelana"))

            assertEquals(
                ResearchRefinement(verdict = null, unresearchedCount = 2, issue = ResearchIssue.OFFLINE),
                refinement,
            )
        }

    @Test
    fun partialResearchRefinesWhatItCanAndReportsTheRest() =
        runTest {
            repository.results = listOf(researched("Xantolina", YES))
            repository.pendingIssue = ResearchIssue.BUSY

            val refinement = useCase(product, likely("Xantolina", "Gelana"))

            assertEquals(VeganStatus.LIKELY_VEGAN, refinement.verdict?.status)
            assertEquals(listOf("Xantolina"), refinement.verdict?.researched?.map { it.name })
            assertEquals(1, refinement.unresearchedCount)
            assertEquals(ResearchIssue.BUSY, refinement.issue)
        }

    @Test
    fun serverReasonCodesMapToActionableIssues() {
        assertEquals(null, ResearchIssue.fromServerReasons(emptyList()))
        assertEquals(ResearchIssue.BUSY, ResearchIssue.fromServerReasons(listOf("timeout", "budget")))
        assertEquals(ResearchIssue.BUSY, ResearchIssue.fromServerReasons(listOf("upstream_429")))
        assertEquals(
            ResearchIssue.UNAVAILABLE,
            ResearchIssue.fromServerReasons(listOf("upstream_404", "invalid_answer")),
        )
    }
}
