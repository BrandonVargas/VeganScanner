package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Guards the contributor-edited `dictionary/ingredients.json`. */
class IngredientDictionaryTest {
    private val dictionary = IngredientDictionary.Bundled

    @Test
    fun bundledDictionaryParsesAndHasEntries() {
        assertTrue(dictionary.entries.size > 20)
        assertTrue(dictionary.plantBasedExceptions.values.flatten().isNotEmpty())
    }

    @Test
    fun entryIdsAreUnique() {
        val duplicates = dictionary.entries.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) fail("Duplicate entry ids: $duplicates")
    }

    @Test
    fun everyEntryHasEnglishAndSpanishTerms() {
        dictionary.entries.forEach { entry ->
            assertTrue(!entry.terms["en"].isNullOrEmpty(), "${entry.id} has no English terms")
            assertTrue(!entry.terms["es"].isNullOrEmpty(), "${entry.id} has no Spanish terms")
        }
    }

    @Test
    fun noTermBelongsToTwoEntries() {
        val owners =
            dictionary.entries
                .flatMap { entry -> entry.terms.values.flatten().map { TextFolding.foldTerm(it) to entry.id } }
                .distinct()
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }
        if (owners.isNotEmpty()) fail("Terms claimed by several entries: $owners")
    }

    @Test
    fun eNumbersAreWellFormedAndUnique() {
        val codes = dictionary.entries.flatMap { it.eNumbers }
        codes.forEach { assertTrue(Regex("E\\d{3,4}[A-F]?").matches(it), "Malformed additive code: $it") }
        assertTrue(codes.size == codes.toSet().size, "Duplicate additive codes")
    }

    @Test
    fun exceptionsDoNotHideTheirOwnEntries() {
        // An exception must be longer than any term it masks, otherwise it would hide real ingredients.
        val terms = dictionary.entries.flatMap { it.terms.values.flatten() }.map(TextFolding::foldTerm).toSet()
        val exceptions = dictionary.plantBasedExceptions.values.flatten().map(TextFolding::foldTerm)
        exceptions.forEach { assertTrue(it !in terms, "'$it' is both a flagged term and an exception") }
    }
}
