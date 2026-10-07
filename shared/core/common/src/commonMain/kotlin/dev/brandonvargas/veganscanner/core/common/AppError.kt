package dev.brandonvargas.veganscanner.core.common

/** Failures surfaced to the presentation layer. UI maps each case to a localized message. */
sealed interface AppError {
    /** No connectivity, DNS failure or timeout. */
    data object Network : AppError

    /** The remote service answered with a 5xx or a non-JSON maintenance page. */
    data object ServiceUnavailable : AppError

    /** The remote service rejected the request because of rate limiting (HTTP 429). */
    data object RateLimited : AppError

    data class Unexpected(val message: String?) : AppError
}
