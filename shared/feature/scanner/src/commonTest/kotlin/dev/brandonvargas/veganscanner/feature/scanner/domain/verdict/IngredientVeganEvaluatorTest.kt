package dev.brandonvargas.veganscanner.feature.scanner.domain.verdict

import dev.brandonvargas.veganscanner.core.model.Ingredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.MAYBE
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.NO
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.UNKNOWN
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus.YES
import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientVeganEvaluatorTest {
    private fun ing(status: IngredientVeganStatus, vararg children: Ingredient) =
        Ingredient(id = null, text = "x", vegan = status, subIngredients = children.toList())

    private fun eval(ingredient: Ingredient) = IngredientVeganEvaluator.effectiveStatus(ingredient)

    @Test
    fun leafKeepsItsOwnFlag() {
        IngredientVeganStatus.entries.forEach { assertEquals(it, eval(ing(it))) }
    }

    @Test
    fun maybeParentWithAllVeganChildrenIsVegan() {
        // "lécithines [maybe] (soja [yes])"
        assertEquals(YES, eval(ing(MAYBE, ing(YES))))
    }

    @Test
    fun anyNonVeganChildMakesParentNonVegan() {
        assertEquals(NO, eval(ing(YES, ing(YES), ing(NO))))
    }

    @Test
    fun maybeChildDowngradesVeganParent() {
        assertEquals(MAYBE, eval(ing(YES, ing(YES), ing(MAYBE))))
    }

    @Test
    fun unflaggedChildDoesNotDowngradeVeganParent() {
        // "Sodium Benzoate [yes] (Preservative [no flag])"
        assertEquals(YES, eval(ing(YES, ing(UNKNOWN))))
    }

    @Test
    fun unknownParentWithMixedChildrenStaysUnknown() {
        assertEquals(UNKNOWN, eval(ing(UNKNOWN, ing(YES), ing(UNKNOWN))))
    }

    @Test
    fun nestedNonVeganPropagatesUp() {
        assertEquals(NO, eval(ing(MAYBE, ing(YES, ing(NO)))))
    }
}
