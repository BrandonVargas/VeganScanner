package dev.brandonvargas.veganscanner.feature.scanner.domain.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IngredientsHashTest {
    @Test
    fun ignoresCaseAccentsAndPunctuationButNotWords() {
        assertEquals(IngredientsHash.of("Azúcar, AGUA. Sal"), IngredientsHash.of("azucar agua sal"))
        assertNotEquals(IngredientsHash.of("azucar, agua, sal"), IngredientsHash.of("azucar, agua, sal, leche"))
        assertEquals(16, IngredientsHash.of("agua").length)
    }

    @Test
    fun isStableAcrossVersions() {
        // Shared verdicts store this value: changing the algorithm would orphan them.
        assertEquals("cbf29ce484222325", IngredientsHash.of(""))
        assertEquals(IngredientsHash.of("agua"), IngredientsHash.of(" Agua "))
    }
}
