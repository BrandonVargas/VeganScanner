package dev.brandonvargas.veganscanner.feature.scanner.di

import dev.brandonvargas.veganscanner.core.network.NetworkJson
import dev.brandonvargas.veganscanner.feature.scanner.data.DefaultLabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.data.DefaultProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.data.DefaultScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.LabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanProductUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientKnowledge
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.appVerdictPipeline
import dev.brandonvargas.veganscanner.feature.scanner.presentation.history.HistoryViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val scannerModule =
    module {
        single<ProductRepository> {
            DefaultProductRepository(api = get(), cache = get(), json = NetworkJson, clock = get())
        }
        single<ScanHistoryRepository> { DefaultScanHistoryRepository(dao = get()) }
        single<LabelScanRepository> { DefaultLabelScanRepository(dao = get(), clock = get()) }
        single { appVerdictPipeline(IngredientKnowledge.Bundled) }
        factoryOf(::ScanProductUseCase)

        viewModelOf(::ScannerViewModel)
        viewModelOf(::HistoryViewModel)
        viewModel { (barcode: String) -> ProductResultViewModel(barcode = barcode, scanProduct = get()) }
        viewModel { (barcode: String) -> LabelScanViewModel(barcode = barcode, repository = get()) }
    }
