package dev.brandonvargas.veganscanner.feature.scanner.di

import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.OffFixtures
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.VerdictPipeline
import dev.brandonvargas.veganscanner.feature.scanner.productFromFixture
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** The pipeline the app actually uses (from Koin) must include the rule engine. */
class ScannerModuleTest {
    @Test
    fun appPipelineRunsTheRuleEngine() =
        runTest {
            val pipeline = koinApplication { modules(scannerModule) }.koin.get<VerdictPipeline>()
            val product =
                productFromFixture(
                    OffFixtures.unknownStatusNoIngredients,
                ).copy(ingredientsText = "agua, grenetina")

            val verdict = pipeline.evaluate(product)

            assertEquals(VeganStatus.NON_VEGAN, verdict.status)
            assertEquals(VerdictSource.RULE_ENGINE, verdict.source)
        }
}
