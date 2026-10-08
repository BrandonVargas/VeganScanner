plugins {
    alias(libs.plugins.veganscanner.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.common)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
