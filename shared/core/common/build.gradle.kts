plugins {
    alias(libs.plugins.veganscanner.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kermit)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
        }
    }
}
