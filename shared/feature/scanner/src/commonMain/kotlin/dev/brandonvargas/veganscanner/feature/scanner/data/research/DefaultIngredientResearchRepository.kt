package dev.brandonvargas.veganscanner.feature.scanner.data.research

import co.touchlab.kermit.Logger
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.database.dao.IngredientResearchDao
import dev.brandonvargas.veganscanner.core.database.entity.IngredientResearchEntity
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.SourceLink
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.IngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.ResearchOutcome
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.ResearchedIngredient
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.TextFolding
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Local cache first (results rarely change), then the shared server cache / web research. */
internal class DefaultIngredientResearchRepository(
    private val remote: ResearchRemote,
    private val dao: IngredientResearchDao,
    private val json: Json,
    private val clock: Clock,
    private val cacheTtl: Duration = 30.days,
) : IngredientResearchRepository {
    private val log = Logger.withTag("IngredientResearch")
    private val sourcesSerializer = ListSerializer(SourceDto.serializer())

    override val isAvailable = true

    override suspend fun research(names: List<String>, language: String): AppResult<ResearchOutcome> {
        val keys = names.associateBy { TextFolding.foldTerm(it) }
        val now = clock.now()
        val cached =
            dao.get(keys.keys.toList())
                .filter { now - Instant.fromEpochMilliseconds(it.fetchedAtEpochMs) < cacheTtl }
                .associateBy { it.normalizedName }
        val misses = keys.filterKeys { it !in cached }.values.toList()
        val fromCache = cached.values.map { it.toDomain() }
        if (misses.isEmpty()) return AppResult.Success(ResearchOutcome(fromCache, pending = emptyList()))

        return try {
            val response = remote.research(misses, language)
            dao.upsert(response.results.map { it.toEntity(now) })
            AppResult.Success(
                ResearchOutcome(
                    results = fromCache + response.results.map { it.toDomain() },
                    pending = response.deferred,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w(e) { "Research failed for $misses" }
            if (fromCache.isEmpty()) {
                AppResult.Failure(
                    AppError.Network,
                )
            } else {
                AppResult.Success(ResearchOutcome(fromCache, misses))
            }
        }
    }

    override suspend fun report(key: String, reason: String?): AppResult<Unit> =
        try {
            remote.report(key, reason)
            dao.delete(key)
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w(e) { "Report failed for $key" }
            AppResult.Failure(AppError.Network)
        }

    private fun ResearchedIngredientDto.toDomain() =
        ResearchedIngredient(
            name = name,
            key = normalizedName,
            status = status.toIngredientStatus(),
            reasonEn = reasonEn,
            reasonEs = reasonEs,
            sources = sources.map { SourceLink(it.title, it.url) },
        )

    private fun ResearchedIngredientDto.toEntity(now: Instant) =
        IngredientResearchEntity(
            normalizedName = normalizedName,
            name = name,
            status = status,
            reasonEn = reasonEn,
            reasonEs = reasonEs,
            sourcesJson = json.encodeToString(sourcesSerializer, sources),
            fetchedAtEpochMs = now.toEpochMilliseconds(),
        )

    private fun IngredientResearchEntity.toDomain() =
        ResearchedIngredient(
            name = name,
            key = normalizedName,
            status = status.toIngredientStatus(),
            reasonEn = reasonEn,
            reasonEs = reasonEs,
            sources = json.decodeFromString(sourcesSerializer, sourcesJson).map { SourceLink(it.title, it.url) },
        )

    private fun String.toIngredientStatus() =
        when (this) {
            "vegan" -> IngredientVeganStatus.YES
            "non_vegan" -> IngredientVeganStatus.NO
            "maybe" -> IngredientVeganStatus.MAYBE
            else -> IngredientVeganStatus.UNKNOWN
        }
}
