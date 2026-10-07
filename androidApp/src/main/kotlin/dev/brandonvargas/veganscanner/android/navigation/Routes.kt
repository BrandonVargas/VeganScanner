package dev.brandonvargas.veganscanner.android.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ScanRoute : NavKey

@Serializable
data object HistoryRoute : NavKey

@Serializable
data class ResultRoute(val barcode: String) : NavKey
