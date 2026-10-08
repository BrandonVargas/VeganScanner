package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict

/**
 * Runs [resolvers] in order and returns the first conclusive verdict.
 * When none decides, returns the most informative partial verdict, or [VeganVerdict.Unknown].
 *
 * Order (see docs/adr/0003-verdict-pipeline.md): OFF analysis → (community) → OFF ingredients →
 * rule engine → (on-device AI). Steps in parentheses are added in later phases.
 */
class VerdictPipeline(private val resolvers: List<VerdictResolver>) {
    suspend fun evaluate(product: Product): VeganVerdict {
        var best: VeganVerdict? = null
        for (resolver in resolvers) {
            when (val result = resolver.resolve(product)) {
                is ResolverResult.Conclusive -> return result.verdict
                is ResolverResult.Inconclusive -> best = moreInformative(best, result.partial)
            }
        }
        return best ?: VeganVerdict.Unknown
    }

    /**
     * Combines partial verdicts, preferring caution: MAYBE_VEGAN > LIKELY_VEGAN > UNKNOWN.
     *
     * Exception: a "maybe" that only lists *unrecognized* ingredients (nothing actually flagged as doubtful) is
     * upgraded by a clean dictionary check to LIKELY_VEGAN, keeping the unrecognized ones so the UI can show them.
     */
    private fun moreInformative(current: VeganVerdict?, candidate: VeganVerdict?): VeganVerdict? {
        if (candidate == null) return current
        if (current == null) return candidate
        val onlyUnrecognized =
            current.status == VeganStatus.MAYBE_VEGAN &&
                current.flaggedIngredients.all { it.status == IngredientVeganStatus.UNKNOWN }
        return when {
            onlyUnrecognized && candidate.status == VeganStatus.LIKELY_VEGAN -> {
                candidate.copy(
                    flaggedIngredients =
                        (current.flaggedIngredients + candidate.flaggedIngredients)
                            .distinctBy { it.name.lowercase() },
                )
            }

            rank(candidate) > rank(current) -> {
                candidate
            }

            rank(candidate) < rank(current) -> {
                current
            }

            else -> {
                val (primary, secondary) =
                    if (candidate.flaggedIngredients.size > current.flaggedIngredients.size) {
                        candidate to current
                    } else {
                        current to candidate
                    }
                primary.copy(
                    flaggedIngredients =
                        (primary.flaggedIngredients + secondary.flaggedIngredients).distinctBy {
                            it.name.lowercase()
                        },
                )
            }
        }
    }

    private fun rank(verdict: VeganVerdict): Int =
        when (verdict.status) {
            VeganStatus.MAYBE_VEGAN -> 3
            VeganStatus.LIKELY_VEGAN -> 2
            VeganStatus.UNKNOWN -> 1
            VeganStatus.VEGAN, VeganStatus.NON_VEGAN -> 0
        }
}
