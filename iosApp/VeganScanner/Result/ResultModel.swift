import Foundation
import Observation
import VeganKit

@MainActor
@Observable
final class ResultModel {
    private let holder = ViewModelStoreHolder()
    private let viewModel: ProductResultViewModel
    private(set) var state: ProductResultUiState

    init(barcode: String) {
        viewModel = holder.productResultViewModel(barcode: barcode)
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

    func retry() {
        viewModel.onAction(action: ProductResultActionRetry.shared)
    }
}
