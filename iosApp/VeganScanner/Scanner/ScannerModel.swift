import Foundation
import Observation
import VeganKit

/// Bridges the shared `ScannerViewModel` into SwiftUI's observation system.
@MainActor
@Observable
final class ScannerModel {
    private let holder = ViewModelStoreHolder()
    private let viewModel: ScannerViewModel
    private(set) var state: ScannerUiState

    init() {
        viewModel = holder.scannerViewModel()
        state = viewModel.state.value
    }

    deinit {
        holder.clear()
    }

    func observeState() async {
        for await newState in viewModel.state {
            state = newState
        }
    }

    /// Emits a barcode each time the shared ViewModel decides a result screen should open.
    func resultsToOpen() -> AsyncStream<String> {
        let effects = viewModel.effects
        return AsyncStream { continuation in
            let task = Task {
                for await effect in effects {
                    switch onEnum(of: effect) {
                    case .openResult(let openResult):
                        continuation.yield(openResult.barcode)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func barcodeDetected(_ rawValue: String) {
        viewModel.onAction(action: ScannerActionBarcodeDetected(rawValue: rawValue))
    }

    /// Digits only, at most GTIN-14 length. Same rule as Android, defined once in shared code.
    func sanitize(_ input: String) -> String {
        ScannerViewModel.companion.sanitizeManualEntry(input: input)
    }

    func manualEntryEdited() {
        viewModel.onAction(action: ScannerActionManualEntryEdited.shared)
        state = viewModel.state.value
    }

    func submitManualEntry(_ value: String) {
        viewModel.onAction(action: ScannerActionManualEntrySubmitted(value: value))
        state = viewModel.state.value
    }
}
