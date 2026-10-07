package dev.brandonvargas.veganscanner.core.common

/** Build information provided by each platform at startup. */
data class AppInfo(
    val versionName: String,
    val isDebug: Boolean,
    val platform: String,
)
