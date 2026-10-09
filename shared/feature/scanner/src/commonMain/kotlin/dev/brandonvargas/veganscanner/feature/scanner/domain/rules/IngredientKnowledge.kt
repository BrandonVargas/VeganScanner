package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus

/** What the app knows about one ingredient term. */
internal data class KnownTerm(
    val entryId: String,
    val status: IngredientVeganStatus,
    val curated: Boolean,
)

/**
 * Everything the rule engine can look up, merged from the curated dictionary and the Open Food Facts taxonomy.
 *
 * Precedence: curated non-vegan/doubtful terms → curated plant-based look-alikes and vegan staples → taxonomy →
 * translated Spanish names of English-only taxonomy entries, which never replace a name from the sources before them.
 * Translated names of non-vegan or doubtful entries are only used when they have several words: single words are
 * often ambiguous on labels ("burro" is a donkey and a tortilla, "gallo" is in "pico de gallo") or mistranslated.
 * All terms are stored folded ([TextFolding.foldTerm]) for O(1) lookup of word n-grams.
 */
internal class IngredientKnowledge(
    dictionary: IngredientDictionary,
    taxonomy: OffTaxonomy,
    translations: OffTaxonomyTranslations = OffTaxonomyTranslations.Empty,
) {
    val terms: Map<String, KnownTerm>
    val additiveCodes: Map<String, KnownTerm>
    val crossContaminationMarkers: List<Regex>

    /** Longest term, in words; bounds the n-gram search. */
    val maxWords: Int

    /** Statuses the app deliberately changed from Open Food Facts, by taxonomy id (e.g. `en:sugar` → YES). */
    private val overriddenIds: Map<String, IngredientVeganStatus>

    init {
        val terms = LinkedHashMap<String, KnownTerm>()

        fun add(term: String, known: KnownTerm) {
            val folded = TextFolding.foldTerm(term)
            if (folded.isNotEmpty() && folded !in terms) terms[folded] = known
        }
        val (vegan, flagged) = dictionary.entries.partition { it.status == IngredientDictionary.Status.YES }
        flagged.forEach { entry ->
            entry.terms.values.flatten().forEach {
                add(it, KnownTerm(entry.id, entry.status.ingredientStatus, curated = true))
            }
        }
        dictionary.plantBasedExceptions.values.flatten().forEach {
            add(it, KnownTerm("plant-based-exception", IngredientVeganStatus.YES, curated = true))
        }
        vegan.forEach { entry ->
            entry.terms.values.flatten().forEach {
                add(it, KnownTerm(entry.id, IngredientVeganStatus.YES, curated = true))
            }
        }
        taxonomy.entries.forEach { entry ->
            (entry.en + entry.es).forEach {
                add(it, KnownTerm(entry.id, entry.status.ingredientStatus, curated = false))
            }
        }
        taxonomy.entries.forEach { entry ->
            val status = entry.status.ingredientStatus
            translations.spanish(entry.id)
                .filter { status == IngredientVeganStatus.YES || TextFolding.foldTerm(it).contains(' ') }
                .forEach { add(it, KnownTerm(entry.id, status, curated = false)) }
        }
        this.terms = terms

        val codes = LinkedHashMap<String, KnownTerm>()
        dictionary.entries.forEach { entry ->
            entry.eNumbers.forEach {
                codes.getOrPut(it.lowercase()) { KnownTerm(entry.id, entry.status.ingredientStatus, true) }
            }
        }
        taxonomy.entries.forEach { entry ->
            entry.e?.let {
                codes.getOrPut(it.lowercase()) { KnownTerm(entry.id, entry.status.ingredientStatus, false) }
            }
        }
        additiveCodes = codes

        crossContaminationMarkers =
            dictionary.crossContaminationMarkers.values.flatten()
                .map { TextFolding.termRegex(TextFolding.foldTerm(it)) }
        maxWords = terms.keys.maxOfOrNull { key -> key.count { it == ' ' } + 1 } ?: 1
        overriddenIds = taxonomy.entries.filter { it.overridden }.associate { it.id to it.status.ingredientStatus }
    }

    /** The curated correction for an Open Food Facts ingredient id, if any. */
    fun overrideFor(taxonomyId: String?): IngredientVeganStatus? = taxonomyId?.let(overriddenIds::get)

    companion object {
        val Bundled: IngredientKnowledge by lazy {
            IngredientKnowledge(IngredientDictionary.Bundled, OffTaxonomy.Bundled, OffTaxonomyTranslations.Bundled)
        }
    }
}
