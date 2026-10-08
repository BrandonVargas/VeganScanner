import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal const val BASE_NAMESPACE = "dev.brandonvargas.veganscanner"

/** `:shared:core:network` -> `dev.brandonvargas.veganscanner.core.network` */
internal val Project.sharedNamespace: String
    get() = path.removePrefix(":shared:").replace(':', '.').let { "$BASE_NAMESPACE.$it" }
