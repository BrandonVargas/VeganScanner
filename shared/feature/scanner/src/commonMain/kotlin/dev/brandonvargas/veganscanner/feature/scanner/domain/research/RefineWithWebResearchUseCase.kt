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
import kotlin.time.Clock

/**
 * Looks up the ingredients the offline steps couldn't recognize on the web (AI + Google Search) and refines the
 * verdict. Runs after the result is shown, because a lookup can take several seconds.
 *
 * Safety: research can only resolve *unrecognized* ingredients. It never touches an ingredient the dictionaries
 * flagged as non-vegan or doubtful, and a researched "non_vegan" makes the product NON_VEGAN.
 */
class RefineWithWebResearchUseCase(
    private val repository: IngredientResearchRepository,
    private val history: ScanHistoryRepository,
    private val clock: Clock,
    private val deviceLanguage: String,
) {
    fun shouldResearch(verdict: VeganVerdict): Boolean =
        repository.isAvailable && !verdict.isConclusive && unrecognized(verdict).isNotEmpty()

    /** Returns the refined verdict, or `null` when research wasn't possible or didn't change anything. */
    suspend operator fun invoke(product: Product, verdict: VeganVerdict): VeganVerdict? {
        if (!shouldResearch(verdict)) return null
        val names = unrecognized(verdict).map { it.name }.take(MAX_NAMES)
        val outcome =
            (repository.research(names, LabelLanguage.guess(product.ingredientsText)) as? AppResult.Success)
                ?.value
                ?.takeIf { it.results.isNotEmpty() }
                ?: return null

        val refined = apply(verdict, outcome.results)
        history.record(product.historyEntry(refined, clock.now()))
        return refined
    }

    internal fun apply(verdict: VeganVerdict, results: List<ResearchedIngredient>): VeganVerdict {
        val byName = results.associateBy { TextFolding.foldTerm(it.name) }
        val researched = mutableListOf<FlaggedIngredient>()
        val remaining =
            verdict.flaggedIngredients.mapNotNull { flagged ->
                val result = byName[TextFolding.foldTerm(flagged.name)]
                if (flagged.status != IngredientVeganStatus.UNKNOWN || result == null) return@mapNotNull flagged
                val resolved =
                    FlaggedIngredient(
                        name = flagged.name,
                        status = result.status,
                        note = result.reason(deviceLanguage),
                        sources = result.sources,
                        researchKey = result.key,
                    )
                researched += resolved
                resolved.takeUnless { it.status == IngredientVeganStatus.YES }
            }
        if (researched.isEmpty()) return verdict

        val status =
            when {
                remaining.any { it.status == IngredientVeganStatus.NO } -> VeganStatus.NON_VEGAN
                remaining.any { it.status == IngredientVeganStatus.MAYBE } -> VeganStatus.MAYBE_VEGAN
                remaining.any { it.status == IngredientVeganStatus.UNKNOWN } -> verdict.status
                else -> VeganStatus.VEGAN
            }
        return VeganVerdict(
            status = status,
            source = VerdictSource.WEB_RESEARCH,
            flaggedIngredients =
                if (status == VeganStatus.NON_VEGAN) {
                    remaining.filter { it.status == IngredientVeganStatus.NO }
                } else {
                    remaining
                },
            researched = researched,
        )
    }

    private fun unrecognized(verdict: VeganVerdict) =
        verdict.flaggedIngredients
            .filter { it.status == IngredientVeganStatus.UNKNOWN }
            .distinctBy { TextFolding.foldTerm(it.name) }

    private companion object {
        /** Matches the Edge Function's per-request limit. */
        const val MAX_NAMES = 5
    }
}
