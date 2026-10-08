package dev.brandonvargas.veganscanner.core.testing

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class TestClock(var current: Instant = Instant.fromEpochMilliseconds(1_760_000_000_000)) : Clock {
    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current += duration
    }
}
