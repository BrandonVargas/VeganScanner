plugins {
    alias(libs.plugins.veganscanner.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
