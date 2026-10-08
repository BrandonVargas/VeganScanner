package dev.brandonvargas.veganscanner.feature.scanner.domain.research

import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.SourceLink

/** An ingredient researched on the web by AI, shared by all users through the server cache. */
data class ResearchedIngredient(
    val name: String,
    /** Key of the shared entry; used to report it as wrong. */
    val key: String,
    val status: IngredientVeganStatus,
    val reasonEn: String?,
    val reasonEs: String?,
    val sources: List<SourceLink>,
) {
    fun reason(language: String): String? = if (language == "es") reasonEs ?: reasonEn else reasonEn ?: reasonEs
}

data class ResearchOutcome(
    val results: List<ResearchedIngredient>,
    /** Names the server couldn't research right now (daily budget reached, search failed); retried on a later scan. */
    val pending: List<String>,
)

interface IngredientResearchRepository {
    /** False when the build has no backend configured. */
    val isAvailable: Boolean

    suspend fun research(names: List<String>, language: String): AppResult<ResearchOutcome>

    suspend fun report(key: String, reason: String?): AppResult<Unit>
}

/** Used when no Supabase project is configured (e.g. forks): online research is simply off. */
object DisabledIngredientResearchRepository : IngredientResearchRepository {
    override val isAvailable = false

    override suspend fun research(names: List<String>, language: String) =
        AppResult.Success(ResearchOutcome(emptyList(), names))

    override suspend fun report(key: String, reason: String?) = AppResult.Success(Unit)
}
