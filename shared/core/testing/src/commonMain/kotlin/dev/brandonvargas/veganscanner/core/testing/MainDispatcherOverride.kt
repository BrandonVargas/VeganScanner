package dev.brandonvargas.veganscanner.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * `viewModelScope` uses `Dispatchers.Main.immediate`, which doesn't exist in unit tests.
 * Call [install] in `@BeforeTest` and [reset] in `@AfterTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherOverride(val dispatcher: TestDispatcher = StandardTestDispatcher()) {
    fun install() = Dispatchers.setMain(dispatcher)

    fun reset() = Dispatchers.resetMain()
}
