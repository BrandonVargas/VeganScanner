package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientLabelTextTest {
    @Test
    fun startsAfterSpanishHeadingAndStopsAtNutritionTable() {
        val ocr =
            """
            GALLETAS DE AVENA
            Contenido neto 300 g
            INGREDIENTES: Harina de avena, azúcar,
            aceite de girasol, gre-
            netina.
            INFORMACIÓN NUTRIMENTAL
            Porción 30 g
            """.trimIndent()

        assertEquals("Harina de avena, azúcar, aceite de girasol, grenetina", IngredientLabelText.extract(ocr))
    }

    @Test
    fun startsAfterEnglishHeadingAndStopsAtManufacturer() {
        val ocr = "Ingredients: oats, sugar, honey. Manufactured by ACME Foods"

        assertEquals("oats, sugar, honey", IngredientLabelText.extract(ocr))
    }

    @Test
    fun keepsAllergenStatement() {
        val ocr = "Ingredientes: harina, azúcar. Contiene: leche. Hecho en México"

        assertEquals("harina, azúcar. Contiene: leche", IngredientLabelText.extract(ocr))
    }

    @Test
    fun withoutHeadingKeepsTheWholeText() {
        assertEquals("agua, sal, vinagre", IngredientLabelText.extract("agua, sal,\nvinagre."))
    }
}
