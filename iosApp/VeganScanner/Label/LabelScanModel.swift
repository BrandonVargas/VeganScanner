import Foundation
import Observation
import VeganKit

@MainActor
@Observable
final class LabelScanModel {
    private let holder = ViewModelStoreHolder()
    private let viewModel: LabelScanViewModel
    private(set) var state: LabelScanUiState

    init(barcode: String) {
        viewModel = holder.labelScanViewModel(barcode: barcode)
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

    /// Emits the barcode once the label is saved and the result should be shown.
    func resultsToShow() -> AsyncStream<String> {
        let effects = viewModel.effects
        return AsyncStream { continuation in
            let task = Task {
                for await effect in effects {
                    switch onEnum(of: effect) {
                    case .showResult(let showResult):
                        continuation.yield(showResult.barcode)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func send(_ action: LabelScanAction) {
        viewModel.onAction(action: action)
        state = viewModel.state.value
    }
}
