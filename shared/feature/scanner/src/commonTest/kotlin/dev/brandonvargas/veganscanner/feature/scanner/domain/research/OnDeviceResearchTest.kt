package dev.brandonvargas.veganscanner.feature.scanner.domain.research

import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnDeviceResearchTest {
    private class FakeModel(
        var available: Boolean = true,
        val answers: Map<String, String?> = emptyMap(),
    ) : OnDeviceLanguageModel {
        val prompts = mutableListOf<String>()

        override suspend fun isAvailable() = available

        override suspend fun generate(instructions: String, prompt: String): String? {
            prompts += prompt
            return answers.entries.firstOrNull { prompt.contains(": ${it.key}\n") }?.value
        }
    }

    private val classifier =
        OnDeviceIngredientClassifier(
            FakeModel(
                answers =
                    mapOf(
                        "Xantolina" to "```json\n{\"status\": \"vegan\", \"reason\": \"Una planta.\",}\n```",
                        "Isinglás" to "{\"status\":\"non_vegan\",\"reason\":}",
                        "Ruido" to "I think it's vegan",
                    ),
            ),
            deviceLanguage = "es",
        )

    @Test
    fun parsesLenientAnswersAndSkipsUnusableOnes() =
        runTest {
            val answers = classifier.classify(listOf("Xantolina", "Isinglás", "Ruido", "Sin respuesta"), "es")

            assertEquals(listOf("Xantolina" to YES, "Isinglás" to NO), answers.map { it.name to it.status })
            assertEquals("Una planta.", answers.first().reason("es"))
            assertTrue(answers.all { it.onDevice && it.key == null && it.sources.isEmpty() })
        }

    @Test
    fun onlyNamesOnlineResearchCouldNotHandleGoToTheDevice() =
        runTest {
            val online =
                FakeIngredientResearchRepository(
                    results = listOf(researchedOnline("Gelana")),
                    pendingIssue = ResearchIssue.BUSY,
                )
            val model = FakeModel(answers = mapOf("Xantolina" to "{\"status\":\"vegan\",\"reason\":\"x\"}"))
            val repository = OnDeviceFallbackResearchRepository(online, OnDeviceIngredientClassifier(model, "en"))

            val outcome = (repository.research(listOf("Gelana", "Xantolina", "Ruido"), "es") as AppResult.Success).value

            assertEquals(listOf("Gelana", "Xantolina"), outcome.results.map { it.name })
            assertEquals(listOf(false, true), outcome.results.map { it.onDevice })
            assertEquals(listOf("Ruido"), outcome.pending)
            assertEquals(ResearchIssue.BUSY, outcome.issue)
            assertEquals(2, model.prompts.size, "only pending names are sent to the device")
        }

    @Test
    fun offlineWithAModelReportsNoIssueWhenEverythingWasEstimated() =
        runTest {
            val online = FakeIngredientResearchRepository(failure = AppError.Network)
            val model = FakeModel(answers = mapOf("Xantolina" to "{\"status\":\"vegan\",\"reason\":\"x\"}"))
            val repository = OnDeviceFallbackResearchRepository(online, OnDeviceIngredientClassifier(model, "en"))

            val outcome = (repository.research(listOf("Xantolina"), "es") as AppResult.Success).value

            assertEquals(listOf("Xantolina"), outcome.results.map { it.name })
            assertNull(outcome.issue)
        }

    @Test
    fun unavailableModelLeavesTheOnlineOutcomeAlone() =
        runTest {
            val online = FakeIngredientResearchRepository(failure = AppError.Network)
            val repository =
                OnDeviceFallbackResearchRepository(
                    online,
                    OnDeviceIngredientClassifier(FakeModel(available = false), "en"),
                )

            val outcome = (repository.research(listOf("Xantolina"), "es") as AppResult.Success).value

            assertEquals(listOf("Xantolina"), outcome.pending)
            assertEquals(ResearchIssue.OFFLINE, outcome.issue)
        }

    @Test
    fun verdictsResolvedOnlyOnTheDeviceSaySo() =
        runTest {
            val repository = FakeIngredientResearchRepository(results = listOf(estimated("Xantolina", YES)))
            val useCase = RefineWithWebResearchUseCase(repository, FakeScanHistoryRepository(), TestClock(), "es")
            val verdict =
                VeganVerdict(
                    VeganStatus.LIKELY_VEGAN,
                    VerdictSource.RULE_ENGINE,
                    listOf(FlaggedIngredient("Xantolina", UNKNOWN)),
                )

            val refined = useCase(productFromFixture(OffFixtures.unknownStatusNoIngredients), verdict).verdict!!

            assertEquals(VeganStatus.VEGAN, refined.status)
            assertEquals(VerdictSource.ON_DEVICE_AI, refined.source)
            assertTrue(refined.researched.single().onDevice)
            assertNull(refined.researched.single().researchKey)
        }

    private fun researchedOnline(name: String) =
        ResearchedIngredient(
            name,
            name.lowercase(),
            YES,
            "r",
            "r",
            listOf(SourceLink("example.org", "https://example.org")),
        )

    private fun estimated(name: String, status: dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus) =
        ResearchedIngredient(name, null, status, "r", "r", emptyList(), onDevice = true)
}
