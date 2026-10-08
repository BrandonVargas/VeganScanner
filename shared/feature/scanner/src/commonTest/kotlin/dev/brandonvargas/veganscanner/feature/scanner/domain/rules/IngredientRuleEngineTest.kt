package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.MAYBE
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.NO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IngredientRuleEngineTest {
    private val engine = IngredientRuleEngine(IngredientDictionary.Bundled)

    private fun flagged(text: String) = engine.analyze(text).map { it.text to it.status }

    @Test
    fun findsSpanishAnimalIngredientsKeepingLabelWording() {
        assertEquals(
            listOf("GRENETINA" to NO, "Leche en polvo" to NO),
            flagged("Agua, azúcar, GRENETINA, Leche en polvo, sal"),
        )
    }

    @Test
    fun ignoresAccentsAndCase() {
        assertEquals(listOf("caseína" to NO), flagged("Proteína de soya, caseína"))
        assertEquals(listOf("ATÚN" to NO), flagged("ATÚN, aceite"))
    }

    @Test
    fun plantBasedLookAlikesAreNotFlagged() {
        assertEquals(
            emptyList(),
            flagged(
                "Leche de coco, manteca de cacao, mantequilla de cacahuate, miel de agave, crema de avellanas, manteca vegetal",
            ),
        )
        assertEquals(emptyList(), flagged("coconut milk, cocoa butter, peanut butter, eggplant"))
    }

    @Test
    fun lookAlikeExceptionDoesNotHideTheRealIngredientNextToIt() {
        assertEquals(listOf("leche" to NO), flagged("leche de coco, leche"))
    }

    @Test
    fun longestTermWinsSoWheyIsNotAlsoReportedAsMilk() {
        assertEquals(listOf("suero de leche" to NO), flagged("harina, suero de leche"))
    }

    @Test
    fun wholeWordsOnly() {
        assertEquals(emptyList(), flagged("eggplant, hamburger buns, resinas, codorniz vegetal"))
    }

    @Test
    fun crossContaminationWarningsAreNotIngredients() {
        assertEquals(emptyList(), flagged("Harina de trigo, azúcar. Puede contener trazas de leche, huevo y nuez."))
        assertEquals(emptyList(), flagged("Oats, sugar. May contain milk; made in a facility that processes eggs"))
    }

    @Test
    fun allergenStatementsAreIngredients() {
        assertEquals(listOf("LECHE" to NO), flagged("Harina, azúcar. CONTIENE: LECHE"))
    }

    @Test
    fun additiveCodesInAnyCommonForm() {
        listOf("colorante E120", "colorante (E-120)", "colorante e 120", "colorante INS 120").forEach {
            assertEquals(listOf(NO), engine.analyze(it).map { match -> match.status }, it)
        }
        assertEquals(listOf("E471" to MAYBE), flagged("emulsificante E471"))
    }

    @Test
    fun doubtfulIngredientsAreMaybe() {
        assertEquals(
            listOf("saborizantes naturales" to MAYBE, "mono y diglicéridos de ácidos grasos" to MAYBE),
            flagged("azúcar, saborizantes naturales, mono y diglicéridos de ácidos grasos"),
        )
    }

    @Test
    fun cajetaIsDairy() {
        assertEquals(listOf("cajeta" to NO), flagged("Obleas con cajeta"))
    }

    @Test
    fun reportsEachIngredientOnce() {
        assertEquals(1, engine.analyze("leche, leche, leche entera").size)
    }

    @Test
    fun plainVeganListHasNoMatches() {
        assertTrue(engine.analyze("Agua, frijol, sal, chile, cebolla, ajo, aceite de girasol").isEmpty())
    }
}
