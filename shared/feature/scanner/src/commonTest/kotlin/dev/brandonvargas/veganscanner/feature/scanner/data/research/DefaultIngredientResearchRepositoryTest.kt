package dev.brandonvargas.veganscanner.feature.scanner.data.research

import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.network.NetworkJson
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.feature.scanner.FakeIngredientResearchDao
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.ResearchIssue
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.ResearchOutcome
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days

class DefaultIngredientResearchRepositoryTest {
    private val clock = TestClock()
    private val dao = FakeIngredientResearchDao()
    private val remote = FakeRemote()
    private val repository = DefaultIngredientResearchRepository(remote, dao, NetworkJson, clock, cacheTtl = 30.days)

    private class FakeRemote : ResearchRemote {
        var response = ResearchResponseDto()
        var failure: Exception? = null
        val calls = mutableListOf<List<String>>()
        val reports = mutableListOf<String>()

        override suspend fun research(names: List<String>, language: String): ResearchResponseDto {
            calls += names
            failure?.let { throw it }
            return response
        }

        override suspend fun report(key: String, reason: String?) {
            reports += key
        }
    }

    private val goma =
        ResearchedIngredientDto(
            name = "Goma gelana",
            normalizedName = "goma gelana",
            status = "vegan",
            reasonEn = "Made by bacterial fermentation.",
            reasonEs = "Se obtiene por fermentación bacteriana.",
            sources = listOf(SourceDto("wikipedia.org", "https://example.org/gellan")),
        )

    @Test
    fun mapsServerResultsAndCachesThemLocally() =
        runTest {
            remote.response =
                ResearchResponseDto(
                    results = listOf(goma),
                    deferred = listOf("Ruido"),
                    deferredReasons = mapOf("Ruido" to "budget"),
                )

            val outcome =
                assertIs<AppResult.Success<ResearchOutcome>>(
                    repository.research(listOf("Goma gelana", "Ruido"), "es"),
                ).value

            val researched = outcome.results.single()
            assertEquals(IngredientVeganStatus.YES, researched.status)
            assertEquals("goma gelana", researched.key)
            assertEquals("Se obtiene por fermentación bacteriana.", researched.reason("es"))
            assertEquals("wikipedia.org", researched.sources.single().title)
            assertEquals(listOf("Ruido"), outcome.pending)
            assertEquals(ResearchIssue.BUSY, outcome.issue)
            assertEquals("vegan", dao.rows["goma gelana"]?.status)
        }

    @Test
    fun freshLocalCacheAvoidsTheNetwork() =
        runTest {
            remote.response = ResearchResponseDto(results = listOf(goma))
            repository.research(listOf("Goma gelana"), "es")
            clock.advanceBy(29.days)

            val outcome =
                assertIs<AppResult.Success<ResearchOutcome>>(
                    repository.research(listOf("GOMA GELANA"), "es"),
                ).value

            assertEquals(1, remote.calls.size)
            assertEquals("Goma gelana", outcome.results.single().name)
        }

    @Test
    fun expiredCacheAsksTheServerAgain() =
        runTest {
            remote.response = ResearchResponseDto(results = listOf(goma))
            repository.research(listOf("Goma gelana"), "es")
            clock.advanceBy(31.days)

            repository.research(listOf("Goma gelana"), "es")

            assertEquals(2, remote.calls.size)
        }

    @Test
    fun networkFailureIsAFailureUnlessSomethingWasCached() =
        runTest {
            remote.failure = IOException("offline")
            assertEquals(AppResult.Failure(AppError.Network), repository.research(listOf("Goma gelana"), "es"))

            remote.failure = null
            remote.response = ResearchResponseDto(results = listOf(goma))
            repository.research(listOf("Goma gelana"), "es")
            remote.failure = IOException("offline")

            val outcome =
                assertIs<AppResult.Success<ResearchOutcome>>(
                    repository.research(listOf("Goma gelana", "Otra"), "es"),
                ).value
            assertEquals(listOf("Goma gelana"), outcome.results.map { it.name })
            assertEquals(listOf("Otra"), outcome.pending)
            assertEquals(ResearchIssue.OFFLINE, outcome.issue)
        }

    @Test
    fun reportingSendsTheKeyAndForgetsTheLocalCopy() =
        runTest {
            remote.response = ResearchResponseDto(results = listOf(goma))
            repository.research(listOf("Goma gelana"), "es")

            assertEquals(AppResult.Success(Unit), repository.report("goma gelana", reason = null))

            assertEquals(listOf("goma gelana"), remote.reports)
            assertEquals(null, dao.rows["goma gelana"])
        }
}
