# Kotlinx Serialization, Ktor, Koin and Room ship their own consumer rules.
# Keep Nav3 route keys serializable for saved state.
-keep @kotlinx.serialization.Serializable class dev.brandonvargas.veganscanner.android.navigation.** { *; }
