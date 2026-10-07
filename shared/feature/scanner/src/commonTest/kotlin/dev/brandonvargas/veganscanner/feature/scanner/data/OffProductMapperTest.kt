package dev.brandonvargas.veganscanner.feature.scanner.data

import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OffProductMapperTest {
    @Test
    fun mapsAnalysisTags() {
        assertEquals(VeganStatus.VEGAN, veganStatusFromTags(listOf("en:palm-oil-free", "en:vegan")))
        assertEquals(VeganStatus.NON_VEGAN, veganStatusFromTags(listOf("en:non-vegan", "en:vegetarian")))
        assertEquals(VeganStatus.MAYBE_VEGAN, veganStatusFromTags(listOf("en:maybe-vegan")))
        assertEquals(VeganStatus.UNKNOWN, veganStatusFromTags(listOf("en:vegan-status-unknown")))
        assertEquals(VeganStatus.UNKNOWN, veganStatusFromTags(emptyList()))
    }

    @Test
    fun mapsProductFieldsAndNestedIngredients() {
        val product = productFromFixture(OffFixtures.nonVegan)

        assertEquals("Nutella", product.name)
        assertEquals("Nutella, Ferrero", product.brands)
        assertEquals(8, product.ingredients.size)
        val lecithin = product.ingredients.first { it.id == "en:e322" }
        assertEquals(IngredientVeganStatus.MAYBE, lecithin.vegan)
        assertEquals(IngredientVeganStatus.YES, lecithin.subIngredients.single().vegan)
    }

    @Test
    fun missingFlagMapsToUnknown() {
        val product = productFromFixture(OffFixtures.veganWithNestedUnknown)
        val preservative = product.ingredients.last().subIngredients.single()

        assertEquals("Preservative", preservative.text)
        assertEquals(IngredientVeganStatus.UNKNOWN, preservative.vegan)
    }

    @Test
    fun missingFieldsAreNull() {
        val product = productFromFixture(OffFixtures.unknownStatusNoIngredients)

        assertNull(product.brands)
        assertNull(product.imageUrl)
        assertNull(product.ingredientsText)
        assertEquals(emptyList(), product.ingredients)
    }
}
