package dev.brandonvargas.veganscanner.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.brandonvargas.veganscanner.R
import dev.brandonvargas.veganscanner.android.ui.theme.LocalVerdictColors
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource

data class VerdictStyle(
    val container: Color,
    val content: Color,
    val icon: ImageVector,
    @StringRes val label: Int,
)

@Composable
@ReadOnlyComposable
fun VeganStatus.style(): VerdictStyle {
    val colors = LocalVerdictColors.current
    return when (this) {
        VeganStatus.VEGAN -> {
            VerdictStyle(
                colors.vegan,
                colors.onVegan,
                Icons.Rounded.CheckCircle,
                R.string.verdict_vegan,
            )
        }

        VeganStatus.NON_VEGAN -> {
            VerdictStyle(
                colors.nonVegan,
                colors.onNonVegan,
                Icons.Rounded.Block,
                R.string.verdict_non_vegan,
            )
        }

        VeganStatus.MAYBE_VEGAN -> {
            VerdictStyle(
                colors.maybe,
                colors.onMaybe,
                Icons.Rounded.WarningAmber,
                R.string.verdict_maybe,
            )
        }

        VeganStatus.LIKELY_VEGAN -> {
            VerdictStyle(
                colors.likely,
                colors.onLikely,
                Icons.Rounded.Spa,
                R.string.verdict_likely,
            )
        }

        VeganStatus.UNKNOWN -> {
            VerdictStyle(
                colors.unknown,
                colors.onUnknown,
                Icons.AutoMirrored.Rounded.HelpOutline,
                R.string.verdict_unknown,
            )
        }
    }
}

@StringRes
fun VerdictSource.labelRes(): Int =
    when (this) {
        VerdictSource.OPEN_FOOD_FACTS -> R.string.source_off_analysis
        VerdictSource.OPEN_FOOD_FACTS_INGREDIENTS -> R.string.source_off_ingredients
        VerdictSource.RULE_ENGINE -> R.string.source_rule_engine
        VerdictSource.LABEL_SCAN -> R.string.source_label_scan
        VerdictSource.WEB_RESEARCH -> R.string.source_web_research
        VerdictSource.UNDETERMINED -> R.string.source_none
    }

@StringRes
fun AppError.messageRes(): Int =
    when (this) {
        AppError.Network -> R.string.error_network
        AppError.ServiceUnavailable -> R.string.error_service_unavailable
        AppError.RateLimited -> R.string.error_rate_limited
        is AppError.Unexpected -> R.string.error_unexpected
    }
