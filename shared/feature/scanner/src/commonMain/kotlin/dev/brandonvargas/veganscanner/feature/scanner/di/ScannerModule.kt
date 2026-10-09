package dev.brandonvargas.veganscanner.feature.scanner.di

import dev.brandonvargas.veganscanner.core.common.AppInfo
import dev.brandonvargas.veganscanner.core.network.NetworkJson
import dev.brandonvargas.veganscanner.feature.scanner.data.DefaultLabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.data.DefaultProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.data.DefaultScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.data.research.DefaultIngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.data.research.SupabaseResearchRemote
import dev.brandonvargas.veganscanner.feature.scanner.domain.LabelScanRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ProductRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanHistoryRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.ScanProductUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.DisabledIngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.IngredientResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.OnDeviceFallbackResearchRepository
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.OnDeviceIngredientClassifier
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.OnDeviceLanguageModel
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.RefineWithWebResearchUseCase
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.IngredientKnowledge
import dev.brandonvargas.veganscanner.feature.scanner.domain.verdict.appVerdictPipeline
import dev.brandonvargas.veganscanner.feature.scanner.presentation.history.HistoryViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultViewModel
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerViewModel
import io.github.jan.supabase.SupabaseClient
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
        single<IngredientResearchRepository> {
            val supabase = getOrNull<SupabaseClient>()
            val online =
                if (supabase == null) {
                    DisabledIngredientResearchRepository
                } else {
                    DefaultIngredientResearchRepository(
                        remote = SupabaseResearchRemote(supabase, session = get(), json = NetworkJson),
                        dao = get(),
                        json = NetworkJson,
                        clock = get(),
                    )
                }
            // Registered by the app when the platform has an on-device model (see startVeganKit).
            val model = getOrNull<OnDeviceLanguageModel>() ?: return@single online
            OnDeviceFallbackResearchRepository(
                online = online,
                onDevice = OnDeviceIngredientClassifier(model, getOrNull<AppInfo>()?.deviceLanguage ?: "en"),
            )
        }
        factory {
            RefineWithWebResearchUseCase(
                repository = get(),
                history = get(),
                clock = get(),
                deviceLanguage = getOrNull<AppInfo>()?.deviceLanguage ?: "en",
            )
        }
        factoryOf(::ScanProductUseCase)

        viewModelOf(::ScannerViewModel)
        viewModelOf(::HistoryViewModel)
        viewModel { (barcode: String) ->
            ProductResultViewModel(
                barcode = barcode,
                scanProduct = get(),
                refineWithWebResearch = get(),
                research = get(),
            )
        }
        viewModel { (barcode: String) -> LabelScanViewModel(barcode = barcode, repository = get()) }
    }
