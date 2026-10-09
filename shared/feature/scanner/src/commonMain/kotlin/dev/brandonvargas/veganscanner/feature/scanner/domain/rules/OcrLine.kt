package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

/**
 * One line of text recognized on a label photo, with its bounding box in normalized image coordinates:
 * 0…1, origin at the top-left of the upright photo. Platforms convert from ML Kit (pixels) or Vision
 * (bottom-left origin) before handing lines to shared code.
 */
data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    internal val height: Float get() = bottom - top
    internal val width: Float get() = right - left
}

/**
 * Picks the lines that belong to the ingredient list, using where they are on the photo:
 * starting at the "Ingredientes/Ingredients" heading, it follows that column downward and skips lines above it,
 * beside it (other columns, side panels), and after the first paragraph gap. Without a heading, every line is
 * kept in reading order.
 */
internal object IngredientLabelLayout {
    private val HEADING = Regex("\\bingredientes?\\b|\\bingredients?\\b")

    /** A vertical gap larger than this many line heights ends the paragraph. */
    private const val PARAGRAPH_GAP = 1.6f

    /** Share of the narrower of line and column that must overlap horizontally for a line to be in the column. */
    private const val COLUMN_OVERLAP = 0.5f

    fun select(lines: List<OcrLine>): List<OcrLine> {
        val readable = lines.filter { it.height > 0f && it.width > 0f && isReadable(it.text) }
        if (readable.isEmpty()) return emptyList()
        val lineHeight = readable.map { it.height }.sorted()[readable.size / 2]
        val heading =
            readable
                .filter { HEADING.containsMatchIn(TextFolding.fold(it.text)) }
                .minByOrNull { it.top }
                ?: return readingOrder(readable, lineHeight)

        var columnLeft = heading.left
        var columnRight = heading.right
        var bottom = heading.bottom
        val selected = mutableListOf(heading)
        readable
            .filter { it !== heading && it.top >= heading.top - lineHeight / 2 }
            .sortedBy { it.top }
            .forEach { line ->
                val sameRowAsHeading = line.top < heading.bottom - lineHeight / 2
                val inColumn =
                    if (sameRowAsHeading) {
                        // "Ingredientes:" printed on its own, with the list continuing to its right.
                        line.left >= heading.left && line.left - heading.right < lineHeight * 2
                    } else {
                        val overlap = minOf(line.right, columnRight) - maxOf(line.left, columnLeft)
                        overlap >= COLUMN_OVERLAP * minOf(line.width, columnRight - columnLeft)
                    }
                if (!inColumn) return@forEach
                if (line.top - bottom > lineHeight * PARAGRAPH_GAP) return@forEach
                selected += line
                columnLeft = minOf(columnLeft, line.left)
                columnRight = maxOf(columnRight, line.right)
                bottom = maxOf(bottom, line.bottom)
            }
        return readingOrder(selected, lineHeight)
    }

    /** Top to bottom; lines on the same row left to right. */
    private fun readingOrder(lines: List<OcrLine>, lineHeight: Float): List<OcrLine> =
        lines.sortedWith(compareBy<OcrLine> { (it.top / (lineHeight / 2)).toInt() }.thenBy { it.left })

    /** Drops OCR noise such as `|||`, `·:·` or barcode digits: at least two letters, mostly letters. */
    private fun isReadable(text: String): Boolean {
        val visible = text.filterNot(Char::isWhitespace)
        val letters = visible.count(Char::isLetter)
        return letters >= 2 && letters >= visible.length * 0.4
    }
}
