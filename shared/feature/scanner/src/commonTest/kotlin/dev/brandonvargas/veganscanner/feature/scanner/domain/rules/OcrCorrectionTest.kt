package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrCorrectionTest {
    private val correction = OcrCorrection.Bundled

    @Test
    fun fixesOneLetterMisreadsOfKnownWords() {
        assertEquals("harina, leche, azucar", correction.correct("harlna, Ieche, azucr").lowercase())
        assertEquals("GELATINA", correction.correct("GELATlNA"))
    }

    @Test
    fun leavesKnownShortAndUnrecognizableWordsAlone() {
        assertEquals("Agua, sal, xantolina roja", correction.correct("Agua, sal, xantolina roja"))
        assertEquals("puede contener trazas", correction.correct("puede contener trazas"))
        assertEquals("E330 7.12%", correction.correct("E330 7.12%"))
    }
}
