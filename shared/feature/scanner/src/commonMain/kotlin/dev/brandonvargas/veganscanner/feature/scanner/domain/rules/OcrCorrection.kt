package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

/**
 * Fixes typical OCR misreads in small label print ("harlna" → "harina", "Ieche" → "leche") using the words of the
 * known ingredient names as a vocabulary.
 *
 * Conservative on purpose, because the user reviews the text but may not notice a wrong fix:
 * only unknown words of [MIN_LENGTH]+ letters are changed, only by one edit (a wrong, missing, extra or swapped
 * letter), and only when exactly one known word is that close.
 */
internal class OcrCorrection(knowledge: IngredientKnowledge) {
    /** Every known word, and every known word with one letter deleted, → the known words they come from. */
    private val vocabulary: Set<String>
    private val byDeletion: Map<String, List<String>>

    init {
        val words =
            (knowledge.terms.keys.flatMap { it.split(' ') } + IngredientRuleEngine.FILLER_WORDS + LABEL_WORDS)
                .filter { it.length >= MIN_LENGTH - 1 && it.all(Char::isLetter) }
                .toSet()
        vocabulary = words
        val index = HashMap<String, MutableList<String>>()
        words.forEach { word ->
            deletions(word).forEach { index.getOrPut(it) { mutableListOf() } += word }
        }
        byDeletion = index
    }

    fun correct(text: String): String =
        WORD.replace(text) { match ->
            val word = match.value
            val folded = TextFolding.foldTerm(word)
            if (folded.length < MIN_LENGTH || folded in vocabulary || !folded.all(Char::isLetter)) {
                word
            } else {
                candidate(folded)?.let { matchCase(it, word) } ?: word
            }
        }

    private fun candidate(word: String): String? {
        val candidates = HashSet<String>()
        byDeletion[word]?.let(candidates::addAll) // a letter missing from the OCR'd word
        deletions(word).forEach { variant ->
            if (variant in vocabulary) candidates += variant // an extra letter
            byDeletion[variant]?.let(candidates::addAll) // a wrong or swapped letter
        }
        return candidates.filter { isOneEditAway(word, it) }.singleOrNull()
    }

    private fun deletions(word: String): List<String> = word.indices.map { word.removeRange(it, it + 1) }

    /** Substitution, insertion, deletion or adjacent transposition. */
    private fun isOneEditAway(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.length == b.length) {
            val diffs = a.indices.filter { a[it] != b[it] }
            if (diffs.size == 1) return true
            val (i, j) = diffs.takeIf { it.size == 2 } ?: return false
            return j == i + 1 && a[i] == b[j] && a[j] == b[i]
        }
        val (short, long) = if (a.length < b.length) a to b else b to a
        if (long.length - short.length != 1) return false
        val firstDiff = short.indices.firstOrNull { short[it] != long[it] } ?: return true
        return short.substring(firstDiff) == long.substring(firstDiff + 1)
    }

    private fun matchCase(replacement: String, original: String): String =
        when {
            // "GELATlNA": the misread letter itself is often the only lowercase one.
            original.count(Char::isUpperCase) >= original.length - 1 -> replacement.uppercase()

            original.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercase() }

            else -> replacement
        }

    companion object {
        private const val MIN_LENGTH = 5
        private val WORD = Regex("\\p{L}+")

        /** Common non-ingredient words on labels, so they are recognized as correct rather than "fixed". */
        private val LABEL_WORDS =
            """
            puede contener trazas menos elaborado equipo procesa tambien producto productos alergenos alergenico
            cantidad minima maxima porcion porciones envase empaque lote fecha caducidad consumo preferente
            informacion nutrimental nutricional energia contenido neto hecho mexico conservese lugar fresco seco
            despues abrir refrigeracion libre gluten lactosa vegano vegana vegetal fuente origen proceso
            may contain traces less than made facility processes also product allergens information nutrition
            energy content net weight store place after opening refrigerate free from vegan source
            """.trimIndent().split(Regex("\\s+")).filter {
                it.isNotEmpty()
            }

        val Bundled: OcrCorrection by lazy { OcrCorrection(IngredientKnowledge.Bundled) }
    }
}
