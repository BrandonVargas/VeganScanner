package dev.brandonvargas.veganscanner.android.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ScanRoute : NavKey

@Serializable
data object HistoryRoute : NavKey

/** [revision] changes when the same product must be re-evaluated (e.g. after a label scan) so it gets a fresh entry. */
@Serializable
data class ResultRoute(val barcode: String, val revision: Int = 0) : NavKey

@Serializable
data class LabelScanRoute(val barcode: String) : NavKey
