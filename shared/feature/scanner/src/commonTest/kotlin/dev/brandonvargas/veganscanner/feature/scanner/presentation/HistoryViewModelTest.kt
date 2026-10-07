package dev.brandonvargas.veganscanner.feature.scanner.presentation

import app.cash.turbine.test
import dev.brandonvargas.veganscanner.core.model.ScanHistoryEntry
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VerdictSource
import dev.brandonvargas.veganscanner.core.testing.MainDispatcherOverride
import dev.brandonvargas.veganscanner.core.testing.TestClock
import dev.brandonvargas.veganscanner.feature.scanner.FakeScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.presentation.history.HistoryAction
import dev.brandonvargas.veganscanner.feature.scanner.presentation.history.HistoryUiState
import dev.brandonvargas.veganscanner.feature.scanner.presentation.history.HistoryViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryViewModelTest {
    private val main = MainDispatcherOverride()
    private val repository = FakeScanHistoryRepository()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private fun entry(barcode: String) =
        ScanHistoryEntry(
            barcode = barcode,
            productName = "P$barcode",
            brands = null,
            imageUrl = null,
            status = VeganStatus.VEGAN,
            source = VerdictSource.OPEN_FOOD_FACTS,
            scannedAt = TestClock().now(),
        )

    @Test
    fun emitsEntriesAndHandlesDeleteAndClear() =
        runTest(main.dispatcher) {
            repository.entries.value = listOf(entry("1"), entry("2"))
            val viewModel = HistoryViewModel(repository)

            viewModel.state.test {
                assertEquals(HistoryUiState(isLoading = true), awaitItem())
                assertEquals(listOf("1", "2"), awaitItem().entries.map { it.barcode })

                viewModel.onAction(HistoryAction.Delete("1"))
                assertEquals(listOf("2"), awaitItem().entries.map { it.barcode })

                viewModel.onAction(HistoryAction.ClearAll)
                assertEquals(HistoryUiState(isLoading = false, entries = emptyList()), awaitItem())
            }
        }
}
