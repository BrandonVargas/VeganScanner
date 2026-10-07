package dev.brandonvargas.veganscanner.feature.scanner.presentation

import app.cash.turbine.test
import dev.brandonvargas.veganscanner.core.testing.MainDispatcherOverride
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerAction
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerEffect
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ScannerViewModelTest {
    private val main = MainDispatcherOverride()
    private val clock = TestClock()
    private lateinit var viewModel: ScannerViewModel

    @BeforeTest
    fun setUp() {
        main.install()
        viewModel = ScannerViewModel(clock)
    }

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun validDetectionOpensResult() =
        runTest(main.dispatcher) {
            viewModel.effects.test {
                viewModel.onAction(ScannerAction.BarcodeDetected("3017620422003"))
                assertEquals(ScannerEffect.OpenResult("3017620422003"), awaitItem())
            }
        }

    @Test
    fun repeatedDetectionsOfSameCodeAreDebounced() =
        runTest(main.dispatcher) {
            viewModel.effects.test {
                repeat(5) { viewModel.onAction(ScannerAction.BarcodeDetected("3017620422003")) }
                assertEquals(ScannerEffect.OpenResult("3017620422003"), awaitItem())
                expectNoEvents()

                clock.advanceBy(4.seconds)
                viewModel.onAction(ScannerAction.BarcodeDetected("3017620422003"))
                assertEquals(ScannerEffect.OpenResult("3017620422003"), awaitItem())
            }
        }

    @Test
    fun invalidDetectionsAreIgnored() =
        runTest(main.dispatcher) {
            viewModel.effects.test {
                viewModel.onAction(ScannerAction.BarcodeDetected("3017620422004"))
                expectNoEvents()
            }
        }

    @Test
    fun sanitizeKeepsDigitsUpToGtin14Length() {
        assertEquals("3017620422004", ScannerViewModel.sanitizeManualEntry("3017-6204a22004"))
        assertEquals("12345678901234", ScannerViewModel.sanitizeManualEntry("1234567890123456"))
    }

    @Test
    fun invalidSubmissionIsFlaggedUntilEdited() =
        runTest(main.dispatcher) {
            viewModel.onAction(ScannerAction.ManualEntrySubmitted("3017620422004"))
            assertTrue(viewModel.state.value.isManualEntryInvalid)

            viewModel.onAction(ScannerAction.ManualEntryEdited)
            assertFalse(viewModel.state.value.isManualEntryInvalid)
        }

    @Test
    fun validSubmissionOpensResult() =
        runTest(main.dispatcher) {
            viewModel.effects.test {
                viewModel.onAction(ScannerAction.ManualEntrySubmitted("3017620422003"))
                assertEquals(ScannerEffect.OpenResult("3017620422003"), awaitItem())
                assertFalse(viewModel.state.value.isManualEntryInvalid)
            }
        }
}
