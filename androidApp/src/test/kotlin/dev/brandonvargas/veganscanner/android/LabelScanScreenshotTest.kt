package dev.brandonvargas.veganscanner.android

import android.app.Application
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.brandonvargas.veganscanner.android.feature.label.LabelScanContent
import dev.brandonvargas.veganscanner.android.ui.theme.VeganScannerTheme
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelDraft
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanError
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanStep
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class LabelScanScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun review() =
        capture(
            LabelScanUiState(step = LabelScanStep.REVIEW, draft = LabelDraft("", 1)),
            text = "Harina de trigo, azúcar, manteca vegetal, grenetina, sal",
        )

    @Test
    fun reviewTooShort() =
        capture(
            LabelScanUiState(
                step = LabelScanStep.REVIEW,
                draft = LabelDraft("", 1),
                error = LabelScanError.TEXT_TOO_SHORT,
            ),
            text = "",
        )

    private fun capture(state: LabelScanUiState, text: String) {
        composeRule.setContent {
            VeganScannerTheme {
                LabelScanContent(state = state, reviewText = TextFieldState(text), onAction = {}, onBack = {})
            }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
