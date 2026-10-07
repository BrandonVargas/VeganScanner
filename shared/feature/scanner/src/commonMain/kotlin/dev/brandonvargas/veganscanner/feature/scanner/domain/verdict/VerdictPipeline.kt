package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict

/**
 * Runs [resolvers] in order and returns the first conclusive verdict.
 * When none decides, returns the most informative partial verdict, or [VeganVerdict.Unknown].
 *
 * Order (see docs/adr/0003-verdict-pipeline.md): OFF analysis → (community) → OFF ingredients →
 * (rule engine) → (on-device AI). Steps in parentheses are added in later phases.
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

    /** A "maybe" beats an "unknown"; for the same status, the one explaining more ingredients wins. */
    private fun moreInformative(current: VeganVerdict?, candidate: VeganVerdict?): VeganVerdict? {
        if (candidate == null) return current
        if (current == null) return candidate
        val upgradesUnknown = candidate.status == VeganStatus.MAYBE_VEGAN && current.status == VeganStatus.UNKNOWN
        val explainsMore =
            candidate.status == current.status &&
                candidate.flaggedIngredients.size > current.flaggedIngredients.size
        return if (upgradesUnknown || explainsMore) candidate else current
    }
}
