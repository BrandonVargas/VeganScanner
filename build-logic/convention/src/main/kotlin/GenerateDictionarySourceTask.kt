import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Embeds JSON data files as Kotlin string properties so the same data ships on Android and iOS
 * without platform resource loading. Large files are split into chunks because a single JVM
 * string constant is limited to 64 KB.
 */
abstract class GenerateDictionarySourceTask : DefaultTask() {
    abstract class Embedded {
        @get:Input
        abstract val propertyName: Property<String>

        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val file: RegularFileProperty
    }

    @get:Input
    abstract val packageName: Property<String>

    @get:Nested
    abstract val embedded: org.gradle.api.provider.ListProperty<Embedded>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = packageName.get()
        val file = outputDir.get().file("${packageName.replace('.', '/')}/IngredientDictionaryJson.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("// Generated from shared/feature/scanner/dictionary/*.json. Do not edit.")
                appendLine("package $packageName")
                embedded.get().forEach { item ->
                    val name = item.propertyName.get()
                    val json = item.file.get().asFile.readText()
                    require("\"\"\"" !in json) { "${item.file.get().asFile.name} must not contain triple quotes" }
                    val chunks = chunked(json, maxChars = 16_000)
                    appendLine()
                    appendLine("internal val $name: String")
                    appendLine("    get() = buildString(${json.length}) {")
                    chunks.indices.forEach { appendLine("        append(${name}_$it)") }
                    appendLine("    }")
                    chunks.forEachIndexed { index, chunk ->
                        appendLine()
                        append("private val ${name}_$index: String = \"\"\"")
                        append(chunk.replace("$", "\${'$'}"))
                        appendLine("\"\"\"")
                    }
                }
            },
        )
    }

    /** Splits text into pieces of at most [maxChars] without breaking a surrogate pair. */
    private fun chunked(text: String, maxChars: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }
}
