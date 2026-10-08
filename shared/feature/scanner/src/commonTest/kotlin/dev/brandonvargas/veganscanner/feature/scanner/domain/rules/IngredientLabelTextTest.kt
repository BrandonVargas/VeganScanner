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

    /** Real Vision OCR output of a Mexican yeast box (side panel), including marketing text after the list. */
    @Test
    fun stopsBeforeAllergenFreeClaimAndCertifications() {
        val ocr =
            """
            agua hirviendo o tria.
            Mantenga en un lugar
            fresco y seco.
            Para uso en fórmulas
            de panificación.
            Ingredientes:
            Levadura (Saccharomyces
            cerevisiae), monoestearato de
            sorbitán y ácido ascórbico.
            ESTE PRODUCTO ES
            LIBRE DE ALÉRGENOS
            "Este producto no contiene
            sellos ni leyendas"
            NON
            GMO
            Project
            VERIFIED
            nongmoproject.org
            PARVE
            """.trimIndent()

        assertEquals(
            "Levadura (Saccharomyces cerevisiae), monoestearato de sorbitán y ácido ascórbico",
            IngredientLabelText.extract(ocr),
        )
    }

    @Test
    fun ingredientQualifiersDoNotEndTheList() {
        val ocr = "Ingredients: certified organic oats, kosher salt, non-GMO canola oil. Net wt 12 oz"

        assertEquals("certified organic oats, kosher salt, non-GMO canola oil", IngredientLabelText.extract(ocr))
    }

    @Test
    fun withoutHeadingKeepsTheWholeText() {
        assertEquals("agua, sal, vinagre", IngredientLabelText.extract("agua, sal,\nvinagre."))
    }
}
