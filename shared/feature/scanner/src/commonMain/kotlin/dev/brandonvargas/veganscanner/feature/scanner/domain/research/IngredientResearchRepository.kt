package dev.brandonvargas.veganscanner.feature.scanner.domain.research

import dev.brandonvargas.veganscanner.core.common.AppError
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
    /** Why [pending] names weren't researched; `null` when nothing is pending. */
    val issue: ResearchIssue? = null,
)

/** Why some ingredients couldn't be researched online. Each maps to a different, actionable message. */
enum class ResearchIssue {
    /** No connection: the user can fix it. */
    OFFLINE,

    /** Daily limit reached (per user or for the whole app): try again tomorrow. */
    BUSY,

    /** The research service failed: nothing to do but retry later. */
    UNAVAILABLE,
    ;

    companion object {
        /** Maps the Edge Function's reason codes (`budget`, `upstream_429`, `timeout`, …). Worst first. */
        fun fromServerReasons(reasons: Collection<String>): ResearchIssue? =
            when {
                reasons.isEmpty() -> null
                reasons.any { it == "budget" || it == "upstream_429" } -> BUSY
                else -> UNAVAILABLE
            }
    }
}

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

/** Connection problems are the user's to fix; anything else is the service's. */
internal fun AppError.toResearchIssue(): ResearchIssue =
    when (this) {
        AppError.Network -> ResearchIssue.OFFLINE
        AppError.RateLimited -> ResearchIssue.BUSY
        AppError.ServiceUnavailable, is AppError.Unexpected -> ResearchIssue.UNAVAILABLE
    }
