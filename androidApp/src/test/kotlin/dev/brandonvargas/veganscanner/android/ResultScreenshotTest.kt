package dev.brandonvargas.veganscanner.android

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.brandonvargas.veganscanner.android.feature.result.ResultContent
import dev.brandonvargas.veganscanner.android.ui.theme.VeganScannerTheme
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the result screen. Record baselines with `./gradlew recordRoborazziDebug`
 * and review changes with `./gradlew compareRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the screens under test are stateless, so Koin is not started.
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ResultScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val product =
        Product(
            barcode = Barcode.parse("7501234567893")!!,
            name = "Galletas de avena",
            brands = "Marca Ejemplo",
            imageUrl = null,
            ingredientsText = "Harina de avena, azúcar, aceite de girasol, saborizantes naturales.",
            ingredients = emptyList(),
            sourceAnalysis = VeganStatus.MAYBE_VEGAN,
        )

    @Test
    fun vegan() =
        capture(
            ProductResultUiState.Found(product, VeganVerdict(VeganStatus.VEGAN, VerdictSource.OPEN_FOOD_FACTS)),
        )

    @Test
    fun nonVegan() =
        capture(
            ProductResultUiState.Found(
                product,
                VeganVerdict(
                    VeganStatus.NON_VEGAN,
                    VerdictSource.OPEN_FOOD_FACTS,
                    listOf(FlaggedIngredient("Leche en polvo", IngredientVeganStatus.NO)),
                ),
            ),
        )

    @Test
    fun maybeVegan() =
        capture(
            ProductResultUiState.Found(
                product,
                VeganVerdict(
                    VeganStatus.MAYBE_VEGAN,
                    VerdictSource.OPEN_FOOD_FACTS_INGREDIENTS,
                    listOf(
                        FlaggedIngredient("azúcar", IngredientVeganStatus.MAYBE),
                        FlaggedIngredient("saborizantes naturales", IngredientVeganStatus.MAYBE),
                    ),
                ),
            ),
        )

    @Test
    fun notFound() = capture(ProductResultUiState.NotFound("7501000000012"))

    @Test
    fun networkError() = capture(ProductResultUiState.Error(AppError.Network))

    private fun capture(state: ProductResultUiState) {
        composeRule.setContent {
            VeganScannerTheme {
                ResultContent(state = state, onBack = {}, onAction = {})
            }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
