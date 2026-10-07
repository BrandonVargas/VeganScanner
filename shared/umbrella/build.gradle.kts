plugins {
    alias(libs.plugins.veganscanner.kmp.library)
    alias(libs.plugins.skie)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "VeganKit"
            isStatic = true
            export(projects.shared.core.common)
            export(projects.shared.core.model)
            export(projects.shared.feature.scanner)
            export(libs.androidx.lifecycle.viewmodel)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.common)
            api(projects.shared.core.model)
            api(projects.shared.feature.scanner)
            implementation(projects.shared.core.network)
            implementation(projects.shared.core.database)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        iosTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
