package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus

/** A dictionary hit, reported with the wording found in the analysed text. */
internal data class RuleMatch(
    val entryId: String,
    val text: String,
    val status: IngredientVeganStatus,
    val position: Int,
)

/**
 * Finds animal-derived ingredients in free text (database ingredient lists or OCR'd labels).
 *
 * 1. Cross-contamination statements ("may contain traces of milk") are ignored: they are not ingredients.
 * 2. Plant-based look-alikes ("coconut milk", "manteca vegetal") are masked out.
 * 3. Terms are matched as whole words, longest first, so "suero de leche" isn't also reported as "leche".
 * 4. Additive codes are matched in any common form: E120, E-120, E 120, INS 120.
 */
internal class IngredientRuleEngine(dictionary: IngredientDictionary) {
    private class Term(val entry: IngredientDictionary.Entry, val regex: Regex, val length: Int)

    private val terms: List<Term> =
        dictionary.entries
            .flatMap { entry ->
                entry.terms.values.flatten().map(TextFolding::foldTerm).distinct().map { folded ->
                    Term(entry, TextFolding.termRegex(folded), folded.length)
                }
            }
            .sortedByDescending { it.length }

    private val exceptions: List<Regex> =
        dictionary.plantBasedExceptions.values.flatten()
            .map(TextFolding::foldTerm)
            .distinct()
            .sortedByDescending { it.length }
            .map(TextFolding::termRegex)

    private val crossContamination: List<Regex> =
        dictionary.crossContaminationMarkers.values.flatten()
            .map { TextFolding.termRegex(TextFolding.foldTerm(it)) }

    private val eNumbers: Map<String, IngredientDictionary.Entry> =
        dictionary.entries
            .flatMap { entry -> entry.eNumbers.map { it.lowercase() to entry } }
            .toMap()

    fun analyze(text: String): List<RuleMatch> {
        val searchable = TextFolding.fold(text).toCharArray()
        maskCrossContamination(text, searchable)
        exceptions.forEach { regex ->
            regex.findAll(searchable.concatToString()).forEach { searchable.blank(it.range) }
        }

        val matches = mutableListOf<RuleMatch>()
        for (term in terms) {
            for (match in term.regex.findAll(searchable.concatToString())) {
                matches +=
                    RuleMatch(
                        term.entry.id,
                        text.substring(match.range).trim(),
                        term.entry.status.ingredientStatus,
                        match.range.first,
                    )
                searchable.blank(match.range)
            }
        }
        for (match in E_NUMBER.findAll(searchable.concatToString())) {
            val code = "e" + match.groupValues[1] + match.groupValues[2]
            val entry = eNumbers[code] ?: continue
            matches +=
                RuleMatch(
                    entry.id,
                    text.substring(match.range).trim(),
                    entry.status.ingredientStatus,
                    match.range.first,
                )
        }

        return matches
            .sortedBy { it.position }
            .distinctBy { it.entryId }
    }

    /** Blanks each "may contain …" clause up to the end of its sentence. */
    private fun maskCrossContamination(original: String, searchable: CharArray) {
        crossContamination.forEach { marker ->
            marker.findAll(searchable.concatToString()).forEach { match ->
                val end =
                    original.indexOfAny(SENTENCE_ENDS, startIndex = match.range.last).let {
                        if (it <
                            0
                        ) {
                            original.length
                        } else {
                            it
                        }
                    }
                searchable.blank(match.range.first until end)
            }
        }
    }

    private fun CharArray.blank(range: IntRange) {
        for (i in range) this[i] = ' '
    }

    private companion object {
        val SENTENCE_ENDS = charArrayOf('.', ';', '\n')

        /** `e120`, `e 120`, `ins 471`, `e472e` after folding (hyphens and parentheses are already spaces). */
        val E_NUMBER = Regex("\\b(?:e|ins)\\s*(\\d{3,4})([a-f])?\\b")
    }
}
