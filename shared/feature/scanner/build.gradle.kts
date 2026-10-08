plugins {
    alias(libs.plugins.veganscanner.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Embeds `dictionary/ingredients.json` as a Kotlin string so the same data ships on Android and iOS
 * without platform resource loading. Contributors only ever edit the JSON.
 */
val generateIngredientDictionary by tasks.registering {
    val input = layout.projectDirectory.file("dictionary/ingredients.json")
    val outputDir = layout.buildDirectory.dir("generated/dictionary/kotlin")
    inputs.file(input)
    outputs.dir(outputDir)
    doLast {
        val json = input.asFile.readText()
        require("\"\"\"" !in json) { "ingredients.json must not contain triple quotes" }
        val packageDir = "dev/brandonvargas/veganscanner/feature/scanner/domain/rules"
        val file = outputDir.get().file("$packageDir/IngredientDictionaryJson.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("// Generated from shared/feature/scanner/dictionary/ingredients.json. Do not edit.")
                appendLine("package dev.brandonvargas.veganscanner.feature.scanner.domain.rules")
                appendLine()
                append("internal val INGREDIENT_DICTIONARY_JSON: String = \"\"\"")
                append(json.replace("$", "\${'$'}"))
                appendLine("\"\"\"")
            },
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateIngredientDictionary)
            dependencies {
                api(projects.shared.core.model)
                api(projects.shared.core.common)
                implementation(projects.shared.core.network)
                implementation(projects.shared.core.database)
                implementation(libs.kotlinx.serialization.json)
                api(libs.androidx.lifecycle.viewmodel)
                implementation(libs.koin.core.viewmodel)
            }
        }
        commonTest.dependencies {
            implementation(projects.shared.core.testing)
            implementation(libs.ktor.client.mock)
        }
    }
}
