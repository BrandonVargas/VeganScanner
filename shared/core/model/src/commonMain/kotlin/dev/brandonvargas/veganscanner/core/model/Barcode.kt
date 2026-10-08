package dev.brandonvargas.veganscanner.core.model

import kotlin.jvm.JvmInline

/**
 * A validated GTIN barcode (EAN-8, UPC-A, EAN-13 or GTIN-14).
 * Use [Barcode.parse] to build one from untrusted input such as manual entry or a camera frame.
 */
@JvmInline
value class Barcode private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        private val VALID_LENGTHS = setOf(8, 12, 13, 14)

        /** Returns a [Barcode] when [raw] is a GTIN with a valid check digit, `null` otherwise. */
        fun parse(raw: String): Barcode? {
            val digits = raw.filterNot { it.isWhitespace() || it == '-' }
            if (digits.length !in VALID_LENGTHS || !digits.all { it in '0'..'9' }) return null
            return if (hasValidCheckDigit(digits)) Barcode(digits) else null
        }

        /** GS1 mod-10: weights alternate 3,1,3… starting from the digit left of the check digit. */
        private fun hasValidCheckDigit(digits: String): Boolean {
            val body = digits.dropLast(1)
            val sum =
                body.reversed().foldIndexed(0) { index, acc, char ->
                    acc + (char - '0') * if (index % 2 == 0) 3 else 1
                }
            val expected = (10 - sum % 10) % 10
            return expected == digits.last() - '0'
        }
    }
}
