package dev.brandonvargas.veganscanner.feature.scanner.data.community

import co.touchlab.kermit.Logger
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.supabase.AnonymousSession
import dev.brandonvargas.veganscanner.feature.scanner.domain.community.CommunityVerdict
import dev.brandonvargas.veganscanner.feature.scanner.domain.community.CommunityVerdictRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A row of `public.community_verdicts`. */
@Serializable
internal data class CommunityVerdictDto(
    val barcode: String,
    val status: String,
    val source: String,
    @SerialName("ingredients_text") val ingredientsText: String,
    @SerialName("ingredients_hash") val ingredientsHash: String,
    val researched: List<ResearchedDto> = emptyList(),
)

/** One AI-researched ingredient as shared: `status` is `vegan`, `non_vegan`, `maybe` or `unknown`. */
@Serializable
internal data class ResearchedDto(val name: String, val status: String, val reason: String? = null)

@Serializable
private data class VerdictReportDto(val barcode: String, val reason: String?)

/** Reads go through RLS (active verdicts only); writes through `submit_community_verdict()` and the reports table. */
internal class SupabaseCommunityVerdictRepository(
    private val client: SupabaseClient,
    private val session: AnonymousSession,
    private val json: Json,
) : CommunityVerdictRepository {
    private val log = Logger.withTag("CommunityVerdicts")

    override val isAvailable = true

    override suspend fun find(barcode: String): AppResult<CommunityVerdict?> =
        attempt("find $barcode") {
            client.from("community_verdicts")
                .select { filter { eq("barcode", barcode) } }
                .decodeList<CommunityVerdictDto>()
                .firstOrNull()
                ?.toDomain()
        }

    override suspend fun share(verdict: CommunityVerdict): AppResult<Boolean> =
        attempt("share ${verdict.barcode}") {
            session.ensureSignedIn()
            val params =
                buildJsonObject {
                    put("p_barcode", verdict.barcode)
                    put("p_status", verdict.status.wireName())
                    put(
                        "p_source",
                        if (verdict.concludedBy ==
                            VerdictSource.ON_DEVICE_AI
                        ) {
                            "on_device_ai"
                        } else {
                            "web_research"
                        },
                    )
                    put("p_ingredients_text", verdict.ingredientsText)
                    put("p_ingredients_hash", verdict.ingredientsHash)
                    put(
                        "p_researched",
                        json.encodeToJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(ResearchedDto.serializer()),
                            verdict.researched.map { ResearchedDto(it.name, it.status.wireName(), it.note) },
                        ),
                    )
                }
            client.postgrest.rpc("submit_community_verdict", params).decodeAs<JsonPrimitive>().content == "true"
        }

    override suspend fun report(barcode: String, reason: String?): AppResult<Unit> =
        attempt("report $barcode") {
            session.ensureSignedIn()
            client.from("verdict_reports").insert(VerdictReportDto(barcode, reason))
            Unit
        }

    private suspend fun <T> attempt(what: String, block: suspend () -> T): AppResult<T> =
        try {
            AppResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w(e) { "Community verdict request failed: $what" }
            AppResult.Failure(if (e is RestException) AppError.ServiceUnavailable else AppError.Network)
        }

    private fun CommunityVerdictDto.toDomain(): CommunityVerdict? {
        val status =
            when (status) {
                "vegan" -> VeganStatus.VEGAN
                "non_vegan" -> VeganStatus.NON_VEGAN
                "maybe" -> VeganStatus.MAYBE_VEGAN
                else -> return null
            }
        return CommunityVerdict(
            barcode = barcode,
            status = status,
            concludedBy = if (source == "on_device_ai") VerdictSource.ON_DEVICE_AI else VerdictSource.WEB_RESEARCH,
            ingredientsText = ingredientsText,
            ingredientsHash = ingredientsHash,
            researched =
                researched.map {
                    FlaggedIngredient(name = it.name, status = it.status.toIngredientStatus(), note = it.reason)
                },
        )
    }

    private fun VeganStatus.wireName() =
        when (this) {
            VeganStatus.VEGAN -> "vegan"
            VeganStatus.NON_VEGAN -> "non_vegan"
            else -> "maybe"
        }

    private fun IngredientVeganStatus.wireName() =
        when (this) {
            IngredientVeganStatus.YES -> "vegan"
            IngredientVeganStatus.NO -> "non_vegan"
            IngredientVeganStatus.MAYBE -> "maybe"
            IngredientVeganStatus.UNKNOWN -> "unknown"
        }

    private fun String.toIngredientStatus() =
        when (this) {
            "vegan" -> IngredientVeganStatus.YES
            "non_vegan" -> IngredientVeganStatus.NO
            "maybe" -> IngredientVeganStatus.MAYBE
            else -> IngredientVeganStatus.UNKNOWN
        }
}
