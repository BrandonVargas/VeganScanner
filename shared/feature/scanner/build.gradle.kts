plugins {
    alias(libs.plugins.veganscanner.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.model)
            api(projects.shared.core.common)
            implementation(projects.shared.core.network)
            implementation(projects.shared.core.database)
            api(libs.androidx.lifecycle.viewmodel)
            implementation(libs.koin.core.viewmodel)
        }
        commonTest.dependencies {
            implementation(projects.shared.core.testing)
            implementation(libs.ktor.client.mock)
        }
    }
}
