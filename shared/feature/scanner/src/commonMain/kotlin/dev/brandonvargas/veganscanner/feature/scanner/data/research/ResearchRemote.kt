package dev.brandonvargas.veganscanner.feature.scanner.data.research

import dev.brandonvargas.veganscanner.core.supabase.AnonymousSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The `research-ingredients` Edge Function and the reports table, behind an interface for tests. */
internal interface ResearchRemote {
    suspend fun research(names: List<String>, language: String): ResearchResponseDto

    suspend fun report(key: String, reason: String?)
}

@Serializable
internal data class ResearchRequestDto(val ingredients: List<String>, val language: String)

@Serializable
internal data class ResearchResponseDto(
    val results: List<ResearchedIngredientDto> = emptyList(),
    val deferred: List<String> = emptyList(),
    /** Non-sensitive reason code per deferred name: `budget`, `timeout`, `upstream_<status>`, `invalid_answer`. */
    val deferredReasons: Map<String, String> = emptyMap(),
    val disputed: List<String> = emptyList(),
    val rejected: List<String> = emptyList(),
)

@Serializable
internal data class ResearchedIngredientDto(
    val name: String,
    val normalizedName: String,
    /** `vegan`, `non_vegan`, `maybe` or `unknown`. */
    val status: String,
    val reasonEn: String? = null,
    val reasonEs: String? = null,
    val sources: List<SourceDto> = emptyList(),
    val cached: Boolean = false,
)

@Serializable
internal data class SourceDto(val title: String, val url: String)

@Serializable
private data class ReportDto(
    @SerialName("normalized_name") val normalizedName: String,
    val reason: String?,
)

internal class SupabaseResearchRemote(
    private val client: SupabaseClient,
    private val session: AnonymousSession,
    private val json: Json,
) : ResearchRemote {
    override suspend fun research(names: List<String>, language: String): ResearchResponseDto {
        session.ensureSignedIn()
        val response =
            client.functions.invoke(
                function = "research-ingredients",
                body = ResearchRequestDto(names, language),
            )
        return json.decodeFromString(ResearchResponseDto.serializer(), response.bodyAsText())
    }

    override suspend fun report(key: String, reason: String?) {
        session.ensureSignedIn()
        client.from("ingredient_reports").insert(ReportDto(key, reason))
    }
}
