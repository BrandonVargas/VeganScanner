package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus

/** A known term found in the text, reported with the wording printed on the label. */
internal data class RuleMatch(
    val entryId: String,
    val text: String,
    val status: IngredientVeganStatus,
    val position: Int,
)

/** One ingredient of the list (comma/parenthesis separated) and what the engine concluded about it. */
internal data class AnalyzedItem(
    val text: String,
    /** YES only when every meaningful word is a known vegan term; UNKNOWN when some words aren't recognized. */
    val status: IngredientVeganStatus,
    val matches: List<RuleMatch>,
)

internal data class IngredientAnalysis(val items: List<AnalyzedItem>) {
    /** Non-vegan and doubtful terms, once per dictionary entry, in label order. */
    val flagged: List<RuleMatch> =
        items.flatMap { it.matches }
            .filter { it.status == IngredientVeganStatus.NO || it.status == IngredientVeganStatus.MAYBE }
            .sortedBy { it.position }
            .distinctBy { it.entryId }

    val unrecognized: List<AnalyzedItem> = items.filter { it.status == IngredientVeganStatus.UNKNOWN }

    val allVegan: Boolean = items.isNotEmpty() && items.all { it.status == IngredientVeganStatus.YES }
}

/**
 * Checks free text (database ingredient lists or OCR'd labels) against [IngredientKnowledge].
 *
 * 1. Cross-contamination statements ("may contain traces of milk") are ignored: they are not ingredients.
 * 2. The text is split into items at `, ; ( ) [ ] .`; text before a `:` is a heading ("Emulsificantes:") and skipped.
 * 3. Additive codes are matched in any common form: E120, E-120, E 120, INS 120.
 * 4. Within each item, the longest known phrase wins at every position (hash lookups of word n-grams), so
 *    "leche de coco" (plant-based) beats "leche", and "suero de leche" isn't also reported as "leche".
 * 5. A doubtful or unknown compound followed by its composition, like "base de avena (agua, avena)", is judged by
 *    the listed sub-ingredients instead of its generic name. A non-vegan name stays flagged.
 */
internal class IngredientRuleEngine(private val knowledge: IngredientKnowledge) {
    private data class Token(val text: String, val start: Int, val end: Int)

    private data class ItemRange(val range: IntRange, val isHeading: Boolean, val depth: Int, val opensGroup: Boolean)

    fun analyze(text: String): IngredientAnalysis {
        val searchable = TextFolding.fold(text).toCharArray()
        maskCrossContamination(text, searchable)

        // One pass over the whole text: additive codes first (blanked so their digits aren't words), then words.
        // Both are later assigned to items by position, keeping the analysis linear in the text length.
        val codes =
            E_NUMBER.findAll(searchable.concatToString()).mapNotNull { match ->
                val known =
                    knowledge.additiveCodes["e" + match.groupValues[1] + match.groupValues[2]] ?: return@mapNotNull null
                for (i in match.range) searchable[i] = ' '
                match(known, text, match.range.first, match.range.last + 1)
            }.toList()
        val words =
            WORD.findAll(searchable.concatToString())
                .map { Token(it.value, it.range.first, it.range.last + 1) }
                .toList()

        val ranges = itemRanges(text)
        val analyzed =
            ranges.map { (range, isHeading) ->
                if (isHeading) {
                    null
                } else {
                    analyzeItem(
                        original = text,
                        range = range,
                        codes = codes.filter { it.position in range },
                        tokens = words.filter { it.start in range },
                    )
                }
            }
        val items =
            analyzed.filterIndexed { index, item ->
                item != null && !(item.status in DEFERS_TO_COMPOSITION && hasComposition(ranges, analyzed, index))
            }
        return IngredientAnalysis(items.filterNotNull())
    }

    /** Whether the item at [index] is followed by a parenthesized list with at least one ingredient in it. */
    private fun hasComposition(ranges: List<ItemRange>, analyzed: List<AnalyzedItem?>, index: Int): Boolean {
        if (!ranges[index].opensGroup) return false
        val depth = ranges[index].depth
        return (index + 1 until ranges.size)
            .asSequence()
            .takeWhile { ranges[it].depth > depth }
            .any { analyzed[it] != null }
    }

    private fun analyzeItem(
        original: String,
        range: IntRange,
        codes: List<RuleMatch>,
        tokens: List<Token>,
    ): AnalyzedItem? {
        val matches = codes.toMutableList()
        val covered = BooleanArray(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val longest =
                (minOf(knowledge.maxWords, tokens.size - i) downTo 1).firstNotNullOfOrNull { n ->
                    val key = (i until i + n).joinToString(" ") { tokens[it].text }
                    knowledge.terms[key]?.let { n to it }
                }
            if (longest == null) {
                i++
                continue
            }
            val (n, known) = longest
            matches += match(known, original, tokens[i].start, tokens[i + n - 1].end)
            for (k in i until i + n) covered[k] = true
            i += n
        }

        val meaningful = tokens.indices.filterNot { covered[it] || isFiller(tokens[it].text) }
        if (matches.isEmpty() && meaningful.isEmpty()) return null

        val status =
            when {
                matches.any { it.status == IngredientVeganStatus.NO } -> IngredientVeganStatus.NO
                matches.any { it.status == IngredientVeganStatus.MAYBE } -> IngredientVeganStatus.MAYBE
                meaningful.isNotEmpty() -> IngredientVeganStatus.UNKNOWN
                else -> IngredientVeganStatus.YES
            }
        val itemText =
            original.substring(range).trim().trim(*TRIM_CHARS).let {
                if (it.length > MAX_ITEM_LENGTH) it.take(MAX_ITEM_LENGTH).trimEnd() + "…" else it
            }
        return AnalyzedItem(itemText, status, matches.sortedBy { it.position })
    }

    private fun match(known: KnownTerm, original: String, start: Int, end: Int) =
        RuleMatch(known.entryId, original.substring(start, end).trim(), known.status, start)

    /**
     * Splits at item separators; a range followed by `:` is a heading such as "Ingredientes:" or "Contiene:".
     * Each range records its bracket nesting depth and whether a bracket opens right after it.
     */
    private fun itemRanges(text: String): List<ItemRange> {
        val ranges = mutableListOf<ItemRange>()
        var start = 0
        var depth = 0
        for (index in 0..text.length) {
            val char = text.getOrNull(index)
            val isSeparator = char == null || char in ITEM_SEPARATORS || char == ':'
            // "7,4 %" / "7.12%": a comma or point between digits is a decimal separator, not an item boundary.
            val isDecimal =
                (char == ',' || char == '.') && text.getOrNull(index - 1)?.isDigit() == true &&
                    text.getOrNull(index + 1)?.isDigit() == true
            if (!isSeparator || isDecimal) continue
            val opensGroup = char in GROUP_OPENERS
            if (index > start && text.substring(start, index).isNotBlank()) {
                ranges += ItemRange(start until index, char == ':', depth, opensGroup)
            }
            if (opensGroup) depth++
            if (char in GROUP_CLOSERS) depth = maxOf(0, depth - 1)
            // A heading ("Contiene 2% o menos de:") ends at the next sentence, not inside brackets.
            if (char == '.' || char == '\n') depth = 0
            start = index + 1
        }
        return ranges
    }

    /** Blanks each "may contain …" clause up to the end of its sentence. */
    private fun maskCrossContamination(original: String, searchable: CharArray) {
        knowledge.crossContaminationMarkers.forEach { marker ->
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
                for (i in match.range.first until end) searchable[i] = ' '
            }
        }
    }

    private fun isFiller(word: String) = word in FILLER_WORDS || word.all { it.isDigit() } || word.length == 1

    private companion object {
        val SENTENCE_ENDS = charArrayOf('.', ';', '\n')
        val ITEM_SEPARATORS = setOf(',', ';', '(', ')', '[', ']', '{', '}', '.', '\n', '*')
        val GROUP_OPENERS = setOf('(', '[', '{')

        /** A non-vegan name stays flagged, and a vegan one ("levadura (Saccharomyces…)") needs no help. */
        val DEFERS_TO_COMPOSITION = setOf(IngredientVeganStatus.MAYBE, IngredientVeganStatus.UNKNOWN)
        val GROUP_CLOSERS = setOf(')', ']', '}')
        val TRIM_CHARS = charArrayOf('.', ',', ';', ':', '-', '*', '"', '\'')
        const val MAX_ITEM_LENGTH = 60
        val WORD = Regex("[a-z0-9]+")

        /** `e120`, `e 120`, `ins 471`, `e472e` after folding (hyphens and parentheses are already spaces). */
        val E_NUMBER = Regex("\\b(?:e|ins)\\s*(\\d{3,4})([a-f])?\\b")

        /** Connectors, quantities and processing words that don't change what an ingredient is (folded). */
        val FILLER_WORDS =
            setOf(
                // Spanish
                "de",
                "del",
                "la",
                "las",
                "el",
                "los",
                "y",
                "e",
                "o",
                "u",
                "en",
                "con",
                "sin",
                "a",
                "al",
                "para",
                "por",
                "un",
                "una",
                "su",
                "sus",
                "que",
                "como",
                "otros",
                "otras",
                "agregado",
                "agregada",
                "adicionado",
                "adicionada",
                "organico",
                "organica",
                "organicos",
                "organicas",
                "natural",
                "naturales",
                "refinado",
                "refinada",
                "deshidratado",
                "deshidratada",
                "deshidratados",
                "deshidratadas",
                "polvo",
                "molido",
                "molida",
                "entero",
                "entera",
                "enteros",
                "integral",
                "fresco",
                "fresca",
                "frescos",
                "concentrado",
                "concentrada",
                "pasteurizado",
                "pasteurizada",
                "yodada",
                "yodado",
                "fortificado",
                "fortificada",
                "enriquecido",
                "enriquecida",
                "tostado",
                "tostada",
                "tostados",
                "cocido",
                "cocida",
                "picado",
                "picada",
                "trozos",
                "hidrogenado",
                "hidrogenada",
                "parcialmente",
                "totalmente",
                "modificado",
                "modificada",
                "extra",
                "virgen",
                "puro",
                "pura",
                "contiene",
                "ingredientes",
                "ingrediente",
                // English
                "of",
                "and",
                "or",
                "the",
                "an",
                "with",
                "in",
                "from",
                "for",
                "added",
                "organic",
                "refined",
                "dried",
                "dehydrated",
                "powder",
                "powdered",
                "ground",
                "whole",
                "fresh",
                "concentrate",
                "concentrated",
                "pasteurized",
                "iodized",
                "enriched",
                "fortified",
                "roasted",
                "toasted",
                "cooked",
                "chopped",
                "sliced",
                "pieces",
                "hydrogenated",
                "partially",
                "fully",
                "modified",
                "virgin",
                "pure",
                "contains",
                "ingredients",
                // Units
                "g",
                "mg",
                "kg",
                "ml",
                "l",
                "oz",
            )
    }
}
