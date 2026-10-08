package dev.brandonvargas.veganscanner.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6A2A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB1F2A2),
    onPrimaryContainer = Color(0xFF002203),
    secondary = Color(0xFF53634E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E8CD),
    onSecondaryContainer = Color(0xFF111F0F),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D17),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D17),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    surfaceDim = Color(0xFFD7DBD3),
    surfaceBright = Color(0xFFF6FBF3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EC),
    surfaceContainer = Color(0xFFEAEFE6),
    surfaceContainerHigh = Color(0xFFE4EAE1),
    surfaceContainerHighest = Color(0xFFDFE4DA),
    inverseSurface = Color(0xFF2D322B),
    inverseOnSurface = Color(0xFFEDF2E9),
    inversePrimary = Color(0xFF96D589),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF96D589),
    onPrimary = Color(0xFF003A03),
    primaryContainer = Color(0xFF155115),
    onPrimaryContainer = Color(0xFFB1F2A2),
    secondary = Color(0xFFBACCB2),
    onSecondary = Color(0xFF263422),
    secondaryContainer = Color(0xFF3C4B37),
    onSecondaryContainer = Color(0xFFD6E8CD),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    background = Color(0xFF101510),
    onBackground = Color(0xFFDFE4DA),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFDFE4DA),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    surfaceDim = Color(0xFF101510),
    surfaceBright = Color(0xFF353A34),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D17),
    surfaceContainer = Color(0xFF1C211B),
    surfaceContainerHigh = Color(0xFF262B25),
    surfaceContainerHighest = Color(0xFF31362F),
    inverseSurface = Color(0xFFDFE4DA),
    inverseOnSurface = Color(0xFF2D322B),
    inversePrimary = Color(0xFF2F6A2A),
)

/** Semantic colors for each verdict, tuned for contrast in light and dark themes. */
@Immutable
data class VerdictColors(
    val vegan: Color,
    val onVegan: Color,
    val nonVegan: Color,
    val onNonVegan: Color,
    val maybe: Color,
    val onMaybe: Color,
    val likely: Color,
    val onLikely: Color,
    val unknown: Color,
    val onUnknown: Color,
)

private val LightVerdictColors = VerdictColors(
    vegan = Color(0xFFB1F2A2),
    onVegan = Color(0xFF002203),
    nonVegan = Color(0xFFFFDAD6),
    onNonVegan = Color(0xFF410002),
    maybe = Color(0xFFFFDEA6),
    onMaybe = Color(0xFF271900),
    likely = Color(0xFFCDEBD9),
    onLikely = Color(0xFF00210F),
    unknown = Color(0xFFDEE5D8),
    onUnknown = Color(0xFF181D17),
)

private val DarkVerdictColors = VerdictColors(
    vegan = Color(0xFF155115),
    onVegan = Color(0xFFB1F2A2),
    nonVegan = Color(0xFF93000A),
    onNonVegan = Color(0xFFFFDAD6),
    maybe = Color(0xFF5D4200),
    onMaybe = Color(0xFFFFDEA6),
    likely = Color(0xFF1F4D33),
    onLikely = Color(0xFFCDEBD9),
    unknown = Color(0xFF424940),
    onUnknown = Color(0xFFDFE4DA),
)

// Theme-level design tokens are the intended use of a CompositionLocal (like MaterialTheme.colorScheme).
// Allow-listed for the compose:compositionlocal-allowlist lint in the root build.gradle.kts.
val LocalVerdictColors = staticCompositionLocalOf { LightVerdictColors }

@Composable
fun VeganScannerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVerdictColors provides if (darkTheme) DarkVerdictColors else LightVerdictColors) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
