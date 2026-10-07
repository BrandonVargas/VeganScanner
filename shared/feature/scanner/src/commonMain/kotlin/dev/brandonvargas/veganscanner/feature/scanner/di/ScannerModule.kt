package dev.brandonvargas.veganscanner.feature.scanner.di

import dev.brandonvargas.veganscanner.core.network.NetworkJson
import dev.brandonvargas.veganscanner.feature.scanner.data.DefaultProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.data.DefaultScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanProductUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.OffAnalysisResolver
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.OffIngredientsResolver
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.VerdictPipeline
import dev.brandonvargas.veganscanner.feature.scanner.presentation.history.HistoryViewModel
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
        single {
            VerdictPipeline(
                resolvers =
                    listOf(
                        OffAnalysisResolver(),
                        OffIngredientsResolver(),
                    ),
            )
        }
        factoryOf(::ScanProductUseCase)

        viewModelOf(::ScannerViewModel)
        viewModelOf(::HistoryViewModel)
        viewModel { (barcode: String) -> ProductResultViewModel(barcode = barcode, scanProduct = get()) }
    }
