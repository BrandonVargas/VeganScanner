package dev.brandonvargas.veganscanner.umbrella

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.brandonvargas.veganscanner.feature.scanner.presentation.history.HistoryViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

/**
 * Gives SwiftUI screens the same ViewModel lifecycle Android has: each Swift screen model owns a
 * holder and calls [clear] from `deinit`, which cancels the ViewModel's `viewModelScope`.
 */
class ViewModelStoreHolder : KoinComponent {
    private val store = ViewModelStore()

    fun scannerViewModel(): ScannerViewModel = provide("scanner") { get() }

    fun historyViewModel(): HistoryViewModel = provide("history") { get() }

    fun productResultViewModel(barcode: String): ProductResultViewModel =
        provide("result:$barcode") { get { parametersOf(barcode) } }

    fun labelScanViewModel(barcode: String): LabelScanViewModel =
        provide("label:$barcode") { get { parametersOf(barcode) } }

    fun clear() = store.clear()

    private inline fun <reified VM : ViewModel> provide(key: String, crossinline create: () -> VM): VM =
        ViewModelProvider.create(store, viewModelFactory { initializer { create() } })[key, VM::class]
}
