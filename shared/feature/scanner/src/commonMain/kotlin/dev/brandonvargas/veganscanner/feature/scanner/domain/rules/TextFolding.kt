package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

/**
 * Folds text for matching: lowercase, no accents, and everything except `[a-z0-9]` becomes a space.
 *
 * Folding is strictly 1:1 per character, so an index in the folded text is the same index in the original.
 * That lets matches be reported with the exact wording printed on the label.
 */
internal object TextFolding {
    private val ACCENTS =
        mapOf(
            'á' to 'a',
            'à' to 'a',
            'ä' to 'a',
            'â' to 'a',
            'ã' to 'a',
            'å' to 'a',
            'é' to 'e',
            'è' to 'e',
            'ë' to 'e',
            'ê' to 'e',
            'í' to 'i',
            'ì' to 'i',
            'ï' to 'i',
            'î' to 'i',
            'ó' to 'o',
            'ò' to 'o',
            'ö' to 'o',
            'ô' to 'o',
            'õ' to 'o',
            'ú' to 'u',
            'ù' to 'u',
            'ü' to 'u',
            'û' to 'u',
            'ñ' to 'n',
            'ç' to 'c',
        )

    fun fold(text: String): String =
        buildString(text.length) {
            for (char in text) {
                val lower = char.lowercaseChar()
                val plain = ACCENTS[lower] ?: lower
                append(if (plain in 'a'..'z' || plain in '0'..'9') plain else ' ')
            }
        }

    /** Folds a dictionary term and collapses whitespace, e.g. `"Mono- y diglicéridos"` → `"mono y digliceridos"`. */
    fun foldTerm(term: String): String = fold(term).split(' ').filter { it.isNotEmpty() }.joinToString(" ")

    /** Whole-word regex for a folded term that tolerates any whitespace between words. */
    fun termRegex(foldedTerm: String): Regex =
        Regex("\\b" + foldedTerm.split(' ').joinToString("\\s+") { Regex.escape(it) } + "\\b")
}
