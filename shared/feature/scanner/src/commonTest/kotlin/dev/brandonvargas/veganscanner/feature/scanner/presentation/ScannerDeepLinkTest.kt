package dev.brandonvargas.veganscanner.feature.scanner.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScannerDeepLinkTest {
    @Test
    fun parsesProductLink() {
        assertEquals("3017620422003", ScannerDeepLink.barcodeFrom("veganscanner://product/3017620422003"))
        assertEquals("3017620422003", ScannerDeepLink.barcodeFrom("VeganScanner://product/3017620422003/?ref=share"))
    }

    @Test
    fun rejectsOtherLinksAndInvalidBarcodes() {
        assertNull(ScannerDeepLink.barcodeFrom("https://example.com/product/3017620422003"))
        assertNull(ScannerDeepLink.barcodeFrom("veganscanner://listing/3017620422003"))
        assertNull(ScannerDeepLink.barcodeFrom("veganscanner://product/3017620422004"))
    }

    @Test
    fun roundTrips() {
        assertEquals("7500327047878", ScannerDeepLink.barcodeFrom(ScannerDeepLink.urlFor("7500327047878")))
    }
}
