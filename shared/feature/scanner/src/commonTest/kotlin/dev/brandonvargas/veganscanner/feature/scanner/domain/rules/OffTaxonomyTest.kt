package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** Smoke tests on the bundled Open Food Facts taxonomy, limited to facts that won't change between refreshes. */
class OffTaxonomyTest {
    private val knowledge = IngredientKnowledge.Bundled
    private val engine = IngredientRuleEngine(knowledge)

    private fun status(term: String) = knowledge.terms[TextFolding.foldTerm(term)]?.status

    @Test
    fun bundledTaxonomyIsLargeAndHasSpanish() {
        assertTrue(OffTaxonomy.Bundled.entries.size > 4_000)
        assertTrue(OffTaxonomy.Bundled.entries.count { it.es.isNotEmpty() } > 2_000)
    }

    @Test
    fun stableFacts() {
        assertEquals(IngredientVeganStatus.YES, status("agua"))
        assertEquals(IngredientVeganStatus.YES, status("harina de trigo"))
        assertEquals(IngredientVeganStatus.NO, status("suero de leche"))
        assertEquals(IngredientVeganStatus.NO, knowledge.additiveCodes["e120"]?.status)
    }

    @Test
    fun everyCuratedOverrideIsAppliedToTheTaxonomy() {
        IngredientDictionary.Bundled.overrides.forEach { override ->
            val entry = OffTaxonomy.Bundled.entries.firstOrNull { it.id == override.id }
            assertTrue(entry?.overridden == true, "Override ${override.id} missing; run ./gradlew updateOffTaxonomy")
        }
        assertEquals(IngredientVeganStatus.YES, status("azúcar"))
        assertEquals(IngredientVeganStatus.YES, knowledge.overrideFor("en:brown-sugar"))
    }

    @Test
    fun translatedSpanishNamesMatchWithTheirEntryStatus() {
        val taxonomy =
            OffTaxonomy(
                listOf(
                    OffTaxonomy.Entry("en:water", OffTaxonomy.Status.YES, en = listOf("water"), es = listOf("agua")),
                    OffTaxonomy.Entry("en:isinglass", OffTaxonomy.Status.NO, en = listOf("isinglass")),
                ),
            )
        val translations =
            OffTaxonomyTranslations(
                mapOf(
                    "en:isinglass" to OffTaxonomyTranslations.Translation(listOf("cola de pescado", "agua")),
                ),
            )
        val translated = IngredientKnowledge(IngredientDictionary.Bundled, taxonomy, translations)

        assertEquals(IngredientVeganStatus.NO, translated.terms[TextFolding.foldTerm("cola de pescado")]?.status)
        assertEquals(
            IngredientVeganStatus.YES,
            translated.terms[TextFolding.foldTerm("agua")]?.status,
            "a translation never replaces an existing name",
        )
    }

    @Test
    fun singleWordTranslationsOfNonVeganEntriesAreIgnored() {
        val taxonomy =
            OffTaxonomy(
                listOf(
                    OffTaxonomy.Entry("en:donkey", OffTaxonomy.Status.NO, en = listOf("donkey")),
                    OffTaxonomy.Entry("en:pork-skin", OffTaxonomy.Status.NO, en = listOf("pork skin")),
                    OffTaxonomy.Entry("en:black-kidney-bean", OffTaxonomy.Status.YES, en = listOf("black kidney bean")),
                ),
            )
        val translations =
            OffTaxonomyTranslations(
                mapOf(
                    "en:donkey" to OffTaxonomyTranslations.Translation(listOf("burro", "carne de burro")),
                    "en:pork-skin" to OffTaxonomyTranslations.Translation(listOf("cuerito de cerdo")),
                    "en:black-kidney-bean" to OffTaxonomyTranslations.Translation(listOf("poroto")),
                ),
            )
        val terms = IngredientKnowledge(IngredientDictionary.Bundled, taxonomy, translations).terms

        assertEquals(null, terms["burro"])
        assertEquals(IngredientVeganStatus.NO, terms["carne de burro"]?.status)
        assertEquals(IngredientVeganStatus.NO, terms["cuerito de cerdo"]?.status)
        assertEquals(IngredientVeganStatus.YES, terms["poroto"]?.status)
    }

    @Test
    fun bundledKnowledgeKeepsMexicanStaplesVegan() {
        listOf("achiote", "elote", "choclo", "pico de gallo").forEach {
            val status = status(it)
            assertTrue(status == null || status == IngredientVeganStatus.YES, "$it is $status")
        }
        assertEquals(IngredientVeganStatus.NO, status("blanquillo"))
    }

    @Test
    fun bundledTranslationsOnlyCoverTaxonomyIngredients() {
        val ids = OffTaxonomy.Bundled.entries.mapTo(HashSet()) { it.id }
        val unknown = OffTaxonomyTranslations.Bundled.entries.keys.filterNot(ids::contains)
        assertTrue(unknown.isEmpty(), "Stale translations $unknown; run ./gradlew translateOffTaxonomy")
    }

    @Test
    fun analysingALongLabelIsFast() {
        val label =
            List(40) {
                "harina de trigo, agua, sal, azúcar, aceite de girasol, xantolina $it"
            }.joinToString(", ")
        engine.analyze(label) // warm up

        val elapsed = measureTime { engine.analyze(label) }

        assertTrue(elapsed.inWholeMilliseconds < 250, "Took $elapsed for ${label.length} chars")
    }
}
