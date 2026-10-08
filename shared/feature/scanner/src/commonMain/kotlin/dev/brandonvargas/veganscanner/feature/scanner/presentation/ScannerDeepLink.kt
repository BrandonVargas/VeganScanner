package dev.brandonvargas.veganscanner.feature.scanner.presentation

import dev.brandonvargas.veganscanner.core.model.Barcode

/**
 * `veganscanner://product/{barcode}` opens a product result directly.
 * Used for sharing and by the Maestro E2E flows (simulators have no camera).
 */
object ScannerDeepLink {
    const val SCHEME = "veganscanner"
    const val HOST = "product"

    /** Returns the validated barcode for a product deep link, or `null` for anything else. */
    fun barcodeFrom(url: String): String? {
        val prefix = "$SCHEME://$HOST/"
        if (!url.startsWith(prefix, ignoreCase = true)) return null
        val raw = url.substring(prefix.length).substringBefore('?').substringBefore('#').trimEnd('/')
        return Barcode.parse(raw)?.value
    }

    fun urlFor(barcode: String): String = "$SCHEME://$HOST/$barcode"
}
