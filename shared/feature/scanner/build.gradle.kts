plugins {
    alias(libs.plugins.veganscanner.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Refreshes `dictionary/off-taxonomy.json`. Run manually or by the weekly `update-taxonomy` workflow.
tasks.register<UpdateOffTaxonomyTask>("updateOffTaxonomy") {
    sourceUrl.set("https://static.openfoodfacts.org/data/taxonomies/ingredients.json")
    curatedDictionary.set(layout.projectDirectory.file("dictionary/ingredients.json"))
    outputFile.set(layout.projectDirectory.file("dictionary/off-taxonomy.json"))
}

// Adds Spanish names to English-only taxonomy entries (`dictionary/off-taxonomy-es.json`): Wikidata, then Gemini
// when GEMINI_API_KEY is set. Incremental; run after updateOffTaxonomy.
tasks.register<TranslateOffTaxonomyTask>("translateOffTaxonomy") {
    sourceUrl.set("https://static.openfoodfacts.org/data/taxonomies/ingredients.json")
    taxonomyFile.set(layout.projectDirectory.file("dictionary/off-taxonomy.json"))
    translationsFile.set(layout.projectDirectory.file("dictionary/off-taxonomy-es.json"))
    geminiModel.set("gemini-3.5-flash-lite")
}

// Embeds the dictionary JSON files into commonMain (see GenerateDictionarySourceTask in build-logic).
val generateIngredientDictionary =
    tasks.register<GenerateDictionarySourceTask>("generateIngredientDictionary") {
        packageName.set("dev.brandonvargas.veganscanner.feature.scanner.domain.rules")
        outputDir.set(layout.buildDirectory.dir("generated/dictionary/kotlin"))
        embedded.add(
            objects.newInstance<GenerateDictionarySourceTask.Embedded>().apply {
                propertyName.set("INGREDIENT_DICTIONARY_JSON")
                file.set(layout.projectDirectory.file("dictionary/ingredients.json"))
            },
        )
        embedded.add(
            objects.newInstance<GenerateDictionarySourceTask.Embedded>().apply {
                propertyName.set("OFF_TAXONOMY_JSON")
                file.set(layout.projectDirectory.file("dictionary/off-taxonomy.json"))
            },
        )
        embedded.add(
            objects.newInstance<GenerateDictionarySourceTask.Embedded>().apply {
                propertyName.set("OFF_TAXONOMY_ES_JSON")
                file.set(layout.projectDirectory.file("dictionary/off-taxonomy-es.json"))
            },
        )
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
                implementation(projects.shared.core.supabase)
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
