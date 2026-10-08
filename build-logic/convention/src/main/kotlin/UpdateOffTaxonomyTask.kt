import groovy.json.JsonGenerator
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI

/**
 * Downloads the Open Food Facts ingredient taxonomy and writes a trimmed copy with what the app needs:
 * the effective vegan status (inherited through `parents`), English/Spanish names and synonyms, and additive codes.
 *
 * Curated `overrides` from the app's dictionary are applied here so whole sub-trees inherit them
 * (e.g. `en:sugar → yes` also covers brown sugar), but an override never relaxes an explicit `no`.
 */
abstract class UpdateOffTaxonomyTask : DefaultTask() {
    @get:Input
    abstract val sourceUrl: Property<String>

    @get:InputFile
    abstract val curatedDictionary: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "vegan scanner"
        description = "Refreshes dictionary/off-taxonomy.json from the Open Food Facts ingredient taxonomy."
        outputs.upToDateWhen { false }
    }

    @Suppress("UNCHECKED_CAST")
    @TaskAction
    fun update() {
        val connection =
            URI(sourceUrl.get()).toURL().openConnection().apply {
                setRequestProperty("User-Agent", "VeganScanner-build (https://github.com/BrandonVargas/VeganScanner)")
            }
        val taxonomy = connection.getInputStream().use { JsonSlurper().parse(it) } as Map<String, Map<String, Any?>>
        val curated = JsonSlurper().parse(curatedDictionary.get().asFile) as Map<String, Any?>
        val overrides =
            (curated["overrides"] as List<Map<String, String>>? ?: emptyList())
                .associate { it.getValue("id") to it.getValue("status") }

        fun ownStatus(id: String): String? = (taxonomy[id]?.get("vegan") as Map<String, String>?)?.get("en")

        fun inherited(id: String, seen: Set<String> = emptySet()): String? {
            ownStatus(id)?.let { return it }
            val parents = taxonomy[id]?.get("parents") as List<String>? ?: return null
            return parents.filterNot { it in seen }.firstNotNullOfOrNull { inherited(it, seen + id) }
        }

        fun overrideFor(id: String, seen: Set<String> = emptySet()): String? {
            overrides[id]?.let { return it }
            val parents = taxonomy[id]?.get("parents") as List<String>? ?: return null
            return parents.filterNot { it in seen }.firstNotNullOfOrNull { overrideFor(it, seen + id) }
        }

        fun terms(entry: Map<String, Any?>, language: String): List<String> {
            val name = (entry["name"] as Map<String, String>?)?.get(language)
            val synonyms = (entry["synonyms"] as Map<String, List<String>>?)?.get(language).orEmpty()
            return (listOfNotNull(name) + synonyms).map {
                it.trim()
            }.filter { it.isNotEmpty() && it.length <= 80 }.distinct()
        }

        val missingOverrides = overrides.keys.filterNot(taxonomy::containsKey)
        require(missingOverrides.isEmpty()) { "Overrides reference unknown taxonomy ids: $missingOverrides" }

        val json = JsonGenerator.Options().disableUnicodeEscaping().build()
        val lines =
            taxonomy.keys.sorted().mapNotNull { id ->
                val entry = taxonomy.getValue(id)
                val base = inherited(id) ?: return@mapNotNull null
                val override = overrideFor(id)?.takeIf { base != "no" && it != base }
                val en = terms(entry, "en") + terms(entry, "xx")
                val es = terms(entry, "es")
                if (en.isEmpty() && es.isEmpty()) return@mapNotNull null
                val eNumber = (entry["e_number"] as Map<String, String>?)?.get("en")
                buildMap {
                    put("id", id)
                    put("status", override ?: base)
                    if (override != null) put("overridden", true)
                    if (en.isNotEmpty()) put("en", en.distinct())
                    if (es.isNotEmpty()) put("es", es)
                    if (eNumber != null) put("e", "E$eNumber".uppercase())
                }.let(json::toJson)
            }

        outputFile.get().asFile.writeText(
            buildString {
                appendLine("{")
                appendLine("  \"source\": \"Open Food Facts ingredients taxonomy (${sourceUrl.get()})\",")
                appendLine("  \"license\": \"Open Database License (ODbL) 1.0 - (c) Open Food Facts contributors\",")
                appendLine(
                    "  \"generatedBy\": \"./gradlew :shared:feature:scanner:updateOffTaxonomy - do not edit by hand\",",
                )
                appendLine("  \"entries\": [")
                appendLine(lines.joinToString(",\n") { "    $it" })
                appendLine("  ]")
                appendLine("}")
            },
        )
        logger.lifecycle("Wrote ${lines.size} taxonomy entries to ${outputFile.get().asFile}")
    }
}
