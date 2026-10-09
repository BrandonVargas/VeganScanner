package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientLabelLayoutTest {
    /** A line of [text] at row [row] (0.04 tall, 0.05 apart) spanning [left]..[right]. */
    private fun line(text: String, row: Int, left: Float = 0.1f, right: Float = 0.5f) =
        OcrLine(text, left, 0.1f + row * 0.05f, right, 0.14f + row * 0.05f)

    private fun select(vararg lines: OcrLine) = IngredientLabelLayout.select(lines.toList()).map { it.text }

    @Test
    fun followsTheIngredientColumnAndSkipsSideAndAboveText() {
        val selected =
            select(
                line("Galletas de avena con chocolate", row = 0, right = 0.9f),
                line("Ingredientes: harina de trigo,", row = 1),
                line("Información nutrimental", row = 1, left = 0.6f, right = 0.9f),
                line("azúcar, aceite vegetal,", row = 2),
                line("Energía 450 kcal", row = 2, left = 0.6f, right = 0.9f),
                line("sal y canela.", row = 3),
                line("Proteínas 6 g", row = 3, left = 0.6f, right = 0.9f),
            )

        assertEquals(listOf("Ingredientes: harina de trigo,", "azúcar, aceite vegetal,", "sal y canela."), selected)
    }

    @Test
    fun stopsAtTheFirstParagraphGap() {
        val selected =
            select(
                line("Ingredientes: agua, sal,", row = 0),
                line("vinagre.", row = 1),
                line("Consérvese en un lugar fresco", row = 4),
            )

        assertEquals(listOf("Ingredientes: agua, sal,", "vinagre."), selected)
    }

    @Test
    fun narrowHeadingWithTheListBelowAndToItsRight() {
        val selected =
            select(
                line("INGREDIENTES:", row = 0, right = 0.3f),
                line("AGUA, AVENA,", row = 0, left = 0.32f, right = 0.8f),
                line("ACEITE DE GIRASOL, SAL DE MAR.", row = 1, right = 0.8f),
            )

        assertEquals(listOf("INGREDIENTES:", "AGUA, AVENA,", "ACEITE DE GIRASOL, SAL DE MAR."), selected)
    }

    @Test
    fun dropsNoiseLines() {
        assertEquals(
            listOf("Ingredientes: agua", "y sal."),
            select(line("Ingredientes: agua", row = 0), line("||| ·:·", row = 1), line("y sal.", row = 2)),
        )
    }

    @Test
    fun withoutHeadingKeepsEveryLineInReadingOrder() {
        assertEquals(
            listOf("agua, sal", "vinagre"),
            select(line("vinagre", row = 1), line("agua, sal", row = 0)),
        )
    }

    @Test
    fun extractFromLinesTrimsTheListAndFixesMisreads() {
        val text =
            IngredientLabelText.extract(
                listOf(
                    line("Marca Ejemplo", row = 0),
                    line("Ingredientes: harlna de trigo, Ieche", row = 1),
                    line("en polvo, sal. Hecho en México", row = 2),
                ),
            )

        assertEquals("harina de trigo, Leche en polvo, sal", text)
    }
}
