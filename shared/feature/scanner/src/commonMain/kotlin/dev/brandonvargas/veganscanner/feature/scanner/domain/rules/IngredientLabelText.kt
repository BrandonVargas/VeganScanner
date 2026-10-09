package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

/**
 * Cleans OCR output from a product label down to the ingredient list:
 * joins lines, starts after the "Ingredients:/Ingredientes:" heading when present, and stops at
 * the next section (nutrition table, directions, manufacturer…).
 */
object IngredientLabelText {
    private val HEADING = Regex("\\bingredientes?\\b|\\bingredients?\\b")
    private val NEXT_SECTIONS =
        listOf(
            "informacion nutrimental",
            "informacion nutricional",
            "declaracion nutrimental",
            "tabla nutrimental",
            "nutrition facts",
            "nutrition information",
            "nutritional information",
            "modo de empleo",
            "modo de uso",
            "modo de preparacion",
            "instrucciones",
            "directions",
            "preparation",
            "conservese",
            "consumase",
            "consumir preferentemente",
            "best before",
            "store in",
            "keep refrigerated",
            "hecho en",
            "elaborado por",
            "fabricado por",
            "distribuido por",
            "importado por",
            "producto de",
            "made in",
            "manufactured by",
            "distributed by",
            "contenido neto",
            "cont net",
            "net wt",
            "net weight",
            // Storage notes and claims often printed right after the list. Words that can also be part of an
            // ingredient ("kosher salt", "non-GMO oil", "certified organic oats") are deliberately not here.
            "mantenga en",
            "mantengase",
            "este producto es libre",
            "este producto no contiene sellos",
            "libre de alergenos",
        ).map { TextFolding.termRegex(it) }

    /**
     * The ingredient list from positioned OCR lines: the heading's column is followed on the photo first
     * ([IngredientLabelLayout]), then the text is trimmed to the list and common misreads are fixed ([OcrCorrection]).
     */
    fun extract(lines: List<OcrLine>): String =
        OcrCorrection.Bundled.correct(extract(IngredientLabelLayout.select(lines).joinToString("\n") { it.text }))

    fun extract(rawText: String): String {
        val joined =
            rawText
                .replace(Regex("-\\s*\\n\\s*"), "")
                .replace(Regex("\\s*\\n\\s*"), " ")
        val folded = TextFolding.fold(joined)

        val start = HEADING.find(folded)?.range?.last?.plus(1) ?: 0
        val end =
            NEXT_SECTIONS
                .mapNotNull { it.find(folded, start)?.range?.first }
                .minOrNull() ?: joined.length

        return joined.substring(start, end)
            .trimStart(' ', ':', '.', '-', ';', ',')
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trimEnd('.', ',', ';', ':')
    }
}
