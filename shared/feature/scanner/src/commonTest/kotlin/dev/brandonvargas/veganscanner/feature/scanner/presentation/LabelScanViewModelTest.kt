package dev.brandonvargas.veganscanner.feature.scanner.presentation

import app.cash.turbine.test
import dev.brandonvargas.veganscanner.core.testing.MainDispatcherOverride
import dev.brandonvargas.veganscanner.feature.scanner.FakeLabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.OcrLine
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelDraft
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanAction
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanEffect
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanError
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanStep
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelScanViewModelTest {
    private val main = MainDispatcherOverride()
    private val repository = FakeLabelScanRepository()
    private lateinit var viewModel: LabelScanViewModel

    @BeforeTest
    fun setUp() {
        main.install()
        viewModel = LabelScanViewModel("7501000000012", repository)
    }

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun recognizedTextIsCleanedAndShownForReview() {
        viewModel.onAction(LabelScanAction.CaptureStarted)
        assertTrue(viewModel.state.value.isRecognizing)

        viewModel.onAction(LabelScanAction.TextRecognized(stackedLines("Ingredientes: agua, sal.", "Hecho en México")))

        val state = viewModel.state.value
        assertEquals(LabelScanStep.REVIEW, state.step)
        assertEquals(LabelDraft("agua, sal", revision = 1), state.draft)
        assertFalse(state.isRecognizing)
    }

    @Test
    fun unreadablePhotoStaysOnCaptureWithError() {
        viewModel.onAction(LabelScanAction.TextRecognized(stackedLines("  12 g  ")))

        assertEquals(LabelScanStep.CAPTURE, viewModel.state.value.step)
        assertEquals(LabelScanError.NOTHING_RECOGNIZED, viewModel.state.value.error)
    }

    @Test
    fun typingManuallyOpensAnEmptyReviewAndRetakeGoesBack() {
        viewModel.onAction(LabelScanAction.TypeManually)
        assertEquals(LabelDraft("", revision = 1), viewModel.state.value.draft)

        viewModel.onAction(LabelScanAction.Retake)
        assertEquals(LabelScanStep.CAPTURE, viewModel.state.value.step)
    }

    @Test
    fun tooShortTextIsRejectedUntilEdited() {
        viewModel.onAction(LabelScanAction.Submit(" a "))
        assertEquals(LabelScanError.TEXT_TOO_SHORT, viewModel.state.value.error)

        viewModel.onAction(LabelScanAction.TextEdited)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun submitSavesTheLabelAndShowsTheResult() =
        runTest(main.dispatcher) {
            viewModel.effects.test {
                viewModel.onAction(LabelScanAction.Submit("  agua, azúcar, grenetina  "))
                assertEquals(LabelScanEffect.ShowResult("7501000000012"), awaitItem())
            }
            assertEquals("agua, azúcar, grenetina", repository.labels["7501000000012"])
        }

    /** Lines printed one under another in a single column. */
    private fun stackedLines(vararg text: String) =
        text.mapIndexed { index, line -> OcrLine(line, 0.1f, 0.1f + index * 0.05f, 0.9f, 0.14f + index * 0.05f) }
}
