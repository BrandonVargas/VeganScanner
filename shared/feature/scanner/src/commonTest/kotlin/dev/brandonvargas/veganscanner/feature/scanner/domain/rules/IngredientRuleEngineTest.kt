package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.MAYBE
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.NO
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.UNKNOWN
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.YES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Curated-dictionary behaviour; an empty taxonomy keeps these independent of Open Food Facts data updates. */
class IngredientRuleEngineTest {
    private val engine = IngredientRuleEngine(IngredientKnowledge(IngredientDictionary.Bundled, OffTaxonomy.Empty))

    private fun flagged(text: String) = engine.analyze(text).flagged.map { it.text to it.status }

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
    fun lookAlikeDoesNotHideTheRealIngredientNextToIt() {
        assertEquals(listOf("leche" to NO), flagged("leche de coco, leche"))
    }

    @Test
    fun longestTermWinsSoWheyIsNotAlsoReportedAsMilk() {
        assertEquals(listOf("suero de leche" to NO), flagged("harina, suero de leche"))
    }

    @Test
    fun wholeWordsOnly() {
        assertEquals(emptyList(), flagged("eggplant, hamburger buns, resinas"))
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
            assertEquals(listOf(NO), engine.analyze(it).flagged.map { match -> match.status }, it)
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
    fun sorbitanStearatesAreDoubtful() {
        assertEquals(
            listOf("monoestearato de sorbitán" to MAYBE),
            flagged("Levadura (Saccharomyces cerevisiae), monoestearato de sorbitán y ácido ascórbico"),
        )
    }

    @Test
    fun cajetaIsDairy() {
        assertEquals(listOf("cajeta" to NO), flagged("Obleas con cajeta"))
    }

    @Test
    fun reportsEachIngredientOnce() {
        assertEquals(1, engine.analyze("leche, leche, leche entera").flagged.size)
    }

    @Test
    fun curatedVeganStaplesAreRecognized() {
        val analysis = engine.analyze("Levadura (Saccharomyces cerevisiae), ácido ascórbico, chile guajillo")

        assertTrue(analysis.allVegan, analysis.items.toString())
    }
}

/** Item recognition with a small inline taxonomy, independent of the bundled data. */
class IngredientRecognitionTest {
    private val taxonomy =
        OffTaxonomy.parse(
            """
            {"entries":[
              {"id":"en:water","status":"yes","en":["water"],"es":["agua"]},
              {"id":"en:salt","status":"yes","en":["salt"],"es":["sal"]},
              {"id":"en:wheat-flour","status":"yes","en":["wheat flour"],"es":["harina de trigo"]},
              {"id":"en:sugar","status":"yes","overridden":true,"en":["sugar"],"es":["azúcar"]},
              {"id":"en:cocoa","status":"yes","en":["cocoa"],"es":["cacao"]},
              {"id":"en:egg-white","status":"no","en":["egg white"],"es":["clara"]},
              {"id":"en:e330","status":"yes","en":["citric acid"],"e":"E330"}
            ]}
            """.trimIndent(),
        )
    private val knowledge = IngredientKnowledge(IngredientDictionary.Bundled, taxonomy)
    private val engine = IngredientRuleEngine(knowledge)

    @Test
    fun everyItemRecognizedAsVegan() {
        val analysis = engine.analyze("Harina de trigo integral, agua, sal yodada, azúcar, cacao 7,4 %, E330")

        assertTrue(analysis.allVegan, analysis.items.toString())
        assertEquals(6, analysis.items.size)
    }

    @Test
    fun unrecognizedItemsAreReportedWithLabelWording() {
        val analysis = engine.analyze("Agua, Xantolina roja, sal")

        assertFalse(analysis.allVegan)
        assertEquals(listOf("Xantolina roja"), analysis.unrecognized.map { it.text })
        assertEquals(UNKNOWN, analysis.unrecognized.single().status)
    }

    @Test
    fun headingsBeforeColonAreNotIngredients() {
        val analysis = engine.analyze("Ingredientes: agua, sal. Emulsificantes: E330")

        assertTrue(analysis.allVegan, analysis.items.toString())
    }

    @Test
    fun curatedPlantBasedLookAlikesCountAsRecognizedVegan() {
        assertEquals(listOf(NO), engine.analyze("clara de huevo").flagged.map { it.status })
        assertEquals(YES, engine.analyze("leche de coco").items.single().status)
    }

    @Test
    fun overridesAreExposedForDatabaseFlags() {
        assertEquals(YES, knowledge.overrideFor("en:sugar"))
        assertEquals(null, knowledge.overrideFor("en:water"))
    }
}
