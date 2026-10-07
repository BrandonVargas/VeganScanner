package dev.brandonvargas.veganscanner.core.common

import org.koin.dsl.module
import kotlin.time.Clock

val commonModule =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider }
        single<Clock> { Clock.System }
    }
