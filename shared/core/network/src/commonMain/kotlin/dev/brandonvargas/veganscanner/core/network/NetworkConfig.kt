package dev.brandonvargas.veganscanner.core.network

import dev.brandonvargas.veganscanner.core.common.AppInfo

data class NetworkConfig(
    val openFoodFactsBaseUrl: String,
    /** Open Food Facts asks every app to identify itself: `AppName/Version (contact)`. */
    val userAgent: String,
    val enableLogging: Boolean,
) {
    companion object {
        const val OPEN_FOOD_FACTS_URL = "https://world.openfoodfacts.org"
        private const val CONTACT = "https://github.com/BrandonVargas/VeganScanner"

        fun from(appInfo: AppInfo) =
            NetworkConfig(
                openFoodFactsBaseUrl = OPEN_FOOD_FACTS_URL,
                userAgent = "VeganScanner/${appInfo.versionName} (${appInfo.platform}; $CONTACT)",
                enableLogging = appInfo.isDebug,
            )
    }
}
