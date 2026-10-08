package dev.brandonvargas.veganscanner.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarcodeTest {
    @Test
    fun acceptsValidEan13() {
        assertEquals("3017620422003", Barcode.parse("3017620422003")?.value)
    }

    @Test
    fun acceptsValidUpcA() {
        assertEquals("036000291452", Barcode.parse("036000291452")?.value)
    }

    @Test
    fun acceptsValidEan8() {
        assertEquals("96385074", Barcode.parse("96385074")?.value)
    }

    @Test
    fun stripsWhitespaceAndDashes() {
        assertEquals("3017620422003", Barcode.parse(" 3017-620422003 ")?.value)
    }

    @Test
    fun rejectsWrongCheckDigit() {
        assertNull(Barcode.parse("3017620422004"))
    }

    @Test
    fun rejectsUnsupportedLength() {
        assertNull(Barcode.parse("12345"))
    }

    @Test
    fun rejectsNonDigits() {
        assertNull(Barcode.parse("30176204220A3"))
    }
}
