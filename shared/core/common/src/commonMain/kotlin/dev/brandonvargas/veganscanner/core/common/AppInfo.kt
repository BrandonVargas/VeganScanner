package dev.brandonvargas.veganscanner.core.common

/** Build information provided by each platform at startup. */
data class AppInfo(
    val versionName: String,
    val isDebug: Boolean,
    val platform: String,
    /** ISO 639 code of the device language, e.g. `es`; used to pick localized research explanations. */
    val deviceLanguage: String = "en",
    /** Online services; `null` (e.g. in forks without a Supabase project) turns online features off. */
    val backend: BackendConfig? = null,
)

/** Supabase project used for online features. The publishable key is meant to ship in apps (RLS protects data). */
data class BackendConfig(
    val host: String,
    val publishableKey: String,
) {
    val url: String get() = "https://$host"

    companion object {
        /** Builds a config from platform build settings, or `null` when they're missing. */
        fun fromBuildSettings(host: String?, publishableKey: String?): BackendConfig? {
            val cleanHost = host?.trim()?.removePrefix("https://")?.trimEnd('/')
            val cleanKey = publishableKey?.trim()
            return if (cleanHost.isNullOrEmpty() ||
                cleanKey.isNullOrEmpty()
            ) {
                null
            } else {
                BackendConfig(cleanHost, cleanKey)
            }
        }
    }
}
