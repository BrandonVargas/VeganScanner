package dev.brandonvargas.veganscanner.core.testing

import dev.brandonvargas.veganscanner.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Runs "background" work inline so tests stay deterministic. */
class TestDispatcherProvider(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}
