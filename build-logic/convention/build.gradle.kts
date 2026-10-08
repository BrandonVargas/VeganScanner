plugins {
    `kotlin-dsl`
}

group = "dev.brandonvargas.veganscanner.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = libs.plugins.veganscanner.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = libs.plugins.veganscanner.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
