package dev.brandonvargas.veganscanner.feature.scanner.domain.community

import dev.brandonvargas.veganscanner.core.common.AppResult
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.historyEntry
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.TextFolding
import kotlin.time.Clock

/** A product verdict that AI research concluded on someone's phone and shared for everyone. */
data class CommunityVerdict(
    val barcode: String,
    val status: VeganStatus,
    /** How it was concluded: [VerdictSource.WEB_RESEARCH] or [VerdictSource.ON_DEVICE_AI]. */
    val concludedBy: VerdictSource,
    /** The ingredient list it was made from (from Open Food Facts or a user's label scan). */
    val ingredientsText: String,
    val ingredientsHash: String,
    /** The AI-researched ingredients and their reasons. */
    val researched: List<FlaggedIngredient>,
)

interface CommunityVerdictRepository {
    /** False when the build has no backend configured. */
    val isAvailable: Boolean

    /** The active verdict for [barcode], or `null` when there is none (or it was disputed). */
    suspend fun find(barcode: String): AppResult<CommunityVerdict?>

    /** Shares [verdict]; `false` when the server didn't store it (already shared, disputed, daily limit). */
    suspend fun share(verdict: CommunityVerdict): AppResult<Boolean>

    suspend fun report(barcode: String, reason: String?): AppResult<Unit>
}

object DisabledCommunityVerdictRepository : CommunityVerdictRepository {
    override val isAvailable = false

    override suspend fun find(barcode: String) = AppResult.Success(null)

    override suspend fun share(verdict: CommunityVerdict) = AppResult.Success(false)

    override suspend fun report(barcode: String, reason: String?) = AppResult.Success(Unit)
}

/** A community verdict that applies to the product being shown. */
data class CommunityMatch(
    val verdict: VeganVerdict,
    /** The shared ingredient list, when this phone has none for the product (e.g. not in Open Food Facts). */
    val ingredientsText: String?,
)

/**
 * Shared product verdicts (see ADR 0009):
 * - [lookup] before research: an active verdict for the barcode is used when its ingredients are the ones this
 *   phone knows, or when this phone knows none. A conclusive Open Food Facts answer always wins.
 * - [lookupMissing] for barcodes Open Food Facts doesn't have: another user's label scan and verdict.
 * - [shareIfEligible] after research: a product AI research made VEGAN is shared with its ingredients and reasons.
 */
class CommunityVerdictsUseCase(
    private val repository: CommunityVerdictRepository,
    private val history: ScanHistoryRepository,
    private val clock: Clock,
) {
    suspend fun lookup(product: Product, verdict: VeganVerdict): CommunityMatch? {
        if (!repository.isAvailable || verdict.isConclusive) return null
        val shared = (repository.find(product.barcode.value) as? AppResult.Success)?.value ?: return null
        val localText = product.ingredientsText?.takeIf { it.isNotBlank() }
        if (localText != null && IngredientsHash.of(localText) != shared.ingredientsHash) return null

        val match =
            CommunityMatch(
                verdict =
                    VeganVerdict(
                        status = shared.status,
                        source = VerdictSource.COMMUNITY,
                        researched = shared.researched,
                    ),
                ingredientsText = shared.ingredientsText.takeIf { localText == null },
            )
        history.record(product.historyEntry(match.verdict, clock.now()))
        return match
    }

    /** A product Open Food Facts doesn't know, as another user shared it (label-scanned ingredients and verdict). */
    suspend fun lookupMissing(barcode: Barcode): Pair<Product, CommunityMatch>? {
        val product =
            Product(
                barcode = barcode,
                name = null,
                brands = null,
                imageUrl = null,
                ingredientsText = null,
                ingredients = emptyList(),
                sourceAnalysis = VeganStatus.UNKNOWN,
            )
        return lookup(product, VeganVerdict.Unknown)?.let { product to it }
    }

    suspend fun shareIfEligible(product: Product, refined: VeganVerdict) {
        if (!repository.isAvailable || refined.status != VeganStatus.VEGAN) return
        if (refined.source != VerdictSource.WEB_RESEARCH && refined.source != VerdictSource.ON_DEVICE_AI) return
        val text = product.ingredientsText?.trim()?.takeIf { it.length >= MIN_TEXT_LENGTH } ?: return
        repository.share(
            CommunityVerdict(
                barcode = product.barcode.value,
                status = refined.status,
                concludedBy = refined.source,
                ingredientsText = text.take(MAX_TEXT_LENGTH),
                ingredientsHash = IngredientsHash.of(text),
                researched = refined.researched.filter { it.status != IngredientVeganStatus.UNKNOWN },
            ),
        )
    }

    suspend fun report(barcode: String, reason: String?): AppResult<Unit> = repository.report(barcode, reason)

    private companion object {
        const val MIN_TEXT_LENGTH = 3
        const val MAX_TEXT_LENGTH = 3000
    }
}

/**
 * A stable fingerprint of an ingredient list: FNV-1a (64-bit) over the folded words, so case, accents and
 * punctuation don't matter but any changed word does. 16 lowercase hex characters.
 */
object IngredientsHash {
    fun of(ingredientsText: String): String {
        val words = TextFolding.fold(ingredientsText).split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        var hash = 0xcbf29ce484222325uL
        words.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x100000001b3uL
        }
        return hash.toString(16).padStart(16, '0')
    }
}
