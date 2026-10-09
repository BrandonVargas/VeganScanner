package dev.brandonvargas.veganscanner.feature.scanner.domain.research

import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.historyEntry
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.LabelLanguage
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.TextFolding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock

/** What web research changed: a new verdict, and/or ingredients it couldn't look up (and why). */
data class ResearchRefinement(
    val verdict: VeganVerdict?,
    val unresearchedCount: Int,
    val issue: ResearchIssue?,
) {
    companion object {
        val Unchanged = ResearchRefinement(verdict = null, unresearchedCount = 0, issue = null)
    }
}

/**
 * Looks up ingredients on the web (Gemini + Wikipedia / Google Search) after the result is shown, because a lookup
 * can take several seconds:
 * - **Unrecognized** ingredients are resolved, refining the verdict. A researched "non_vegan" makes the product
 *   NON_VEGAN.
 * - Ingredients already flagged **doubtful** only get an explanation (why they may not be vegan, with sources). Their
 *   status and the verdict never change, and an answer that disagrees (says "vegan") isn't shown. Non-vegan ones
 *   ("leche") need no explanation.
 */
class RefineWithWebResearchUseCase(
    private val repository: IngredientResearchRepository,
    private val history: ScanHistoryRepository,
    private val clock: Clock,
    private val deviceLanguage: String,
) {
    fun shouldResearch(verdict: VeganVerdict): Boolean = repository.isAvailable && namesToResearch(verdict).isNotEmpty()

    /**
     * Researches the unrecognized ingredients. The result carries the refined verdict (if anything was learned) and,
     * when some ingredients couldn't be researched, how many and why, so the UI can say so instead of failing silently.
     */
    suspend operator fun invoke(product: Product, verdict: VeganVerdict): ResearchRefinement {
        if (!shouldResearch(verdict)) return ResearchRefinement.Unchanged
        val names = namesToResearch(verdict)
        val outcome = researchInBatches(names, LabelLanguage.guess(product.ingredientsText))

        val refined = outcome.results.takeIf { it.isNotEmpty() }?.let { apply(verdict, it) }?.takeIf { it != verdict }
        refined?.let { history.record(product.historyEntry(it, clock.now())) }
        return ResearchRefinement(
            verdict = refined,
            unresearchedCount = outcome.pending.size,
            issue = outcome.issue.takeIf { outcome.pending.isNotEmpty() },
        )
    }

    /** The server takes [BATCH_SIZE] names per request; batches run in parallel and failures become pending names. */
    private suspend fun researchInBatches(names: List<String>, language: String): ResearchOutcome =
        coroutineScope {
            val outcomes =
                names.chunked(BATCH_SIZE).map { batch ->
                    async {
                        when (val result = repository.research(batch, language)) {
                            is AppResult.Success -> result.value
                            is AppResult.Failure -> ResearchOutcome(emptyList(), batch, result.error.toResearchIssue())
                        }
                    }
                }.awaitAll()
            ResearchOutcome(
                results = outcomes.flatMap { it.results },
                pending = outcomes.flatMap { it.pending },
                // The most actionable one: offline first, then busy, then unavailable.
                issue = outcomes.mapNotNull { it.issue }.minOrNull(),
            )
        }

    internal fun apply(verdict: VeganVerdict, results: List<ResearchedIngredient>): VeganVerdict {
        val byName = results.associateBy { TextFolding.foldTerm(it.name) }
        val explained = verdict.flaggedIngredients.map { flagged -> explain(flagged, byName) }
        val researched = mutableListOf<FlaggedIngredient>()
        val remaining =
            explained.mapNotNull { flagged ->
                val result = byName[TextFolding.foldTerm(flagged.name)]
                if (flagged.status != IngredientVeganStatus.UNKNOWN || result == null) return@mapNotNull flagged
                val resolved =
                    FlaggedIngredient(
                        name = flagged.name,
                        status = result.status,
                        note = result.reason(deviceLanguage),
                        sources = result.sources,
                        researchKey = result.key,
                        onDevice = result.onDevice,
                    )
                researched += resolved
                resolved.takeUnless { it.status == IngredientVeganStatus.YES }
            }
        if (researched.isEmpty()) return verdict.copy(flaggedIngredients = explained)

        val status =
            when {
                remaining.any { it.status == IngredientVeganStatus.NO } -> VeganStatus.NON_VEGAN
                remaining.any { it.status == IngredientVeganStatus.MAYBE } -> VeganStatus.MAYBE_VEGAN
                remaining.any { it.status == IngredientVeganStatus.UNKNOWN } -> verdict.status
                else -> VeganStatus.VEGAN
            }
        return VeganVerdict(
            status = status,
            source = if (researched.all { it.onDevice }) VerdictSource.ON_DEVICE_AI else VerdictSource.WEB_RESEARCH,
            flaggedIngredients =
                if (status == VeganStatus.NON_VEGAN) {
                    remaining.filter { it.status == IngredientVeganStatus.NO }
                } else {
                    remaining
                },
            researched = researched,
        )
    }

    /** Adds the researched reason to a doubtful ingredient when the research agrees it isn't clearly vegan. */
    private fun explain(flagged: FlaggedIngredient, byName: Map<String, ResearchedIngredient>): FlaggedIngredient {
        if (!flagged.isExplainable) return flagged
        val result = byName[TextFolding.foldTerm(flagged.name)] ?: return flagged
        if (result.status != IngredientVeganStatus.NO && result.status != IngredientVeganStatus.MAYBE) return flagged
        return flagged.copy(
            note = result.reason(deviceLanguage),
            sources = result.sources,
            researchKey = result.key,
            onDevice = result.onDevice,
        )
    }

    /** Unrecognized ingredients first (they can change the verdict), then the ones that only need an explanation. */
    private fun namesToResearch(verdict: VeganVerdict): List<String> {
        val unrecognized =
            verdict.flaggedIngredients.takeUnless { verdict.isConclusive }.orEmpty()
                .filter { it.status == IngredientVeganStatus.UNKNOWN }
        val explainable = verdict.flaggedIngredients.filter { it.isExplainable }
        return (unrecognized + explainable)
            .map { it.name }
            .distinctBy { TextFolding.foldTerm(it) }
            .take(MAX_NAMES)
    }

    private val FlaggedIngredient.isExplainable: Boolean
        get() = note == null && status == IngredientVeganStatus.MAYBE

    private companion object {
        /** Matches the Edge Function's per-request limit. */
        const val BATCH_SIZE = 5

        /** Bounds one scan's share of the per-user daily research limit. */
        const val MAX_NAMES = 15
    }
}
