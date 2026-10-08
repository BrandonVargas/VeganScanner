package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganVerdict

/**
 * One link of the verdict chain (Chain of Responsibility).
 * Each resolver either decides, or passes with the partial insight it gathered.
 */
fun interface VerdictResolver {
    suspend fun resolve(product: Product): ResolverResult
}

sealed interface ResolverResult {
    data class Conclusive(val verdict: VeganVerdict) : ResolverResult

    /** [partial] carries doubtful ingredients so later steps (and the UI) can explain the result. */
    data class Inconclusive(val partial: VeganVerdict? = null) : ResolverResult
}
