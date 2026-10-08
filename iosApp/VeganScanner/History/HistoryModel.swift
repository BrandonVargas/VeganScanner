import Foundation
import Observation
import VeganKit

@MainActor
@Observable
final class HistoryModel {
    private let holder = ViewModelStoreHolder()
    private let viewModel: HistoryViewModel
    private(set) var state: HistoryUiState

    init() {
        viewModel = holder.historyViewModel()
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

    func delete(barcode: String) {
        viewModel.onAction(action: HistoryActionDelete(barcode: barcode))
    }

    func clearAll() {
        viewModel.onAction(action: HistoryActionClearAll.shared)
    }
}
