import SwiftUI
import VeganKit

/// Screens pushed on a tab's navigation stack.
enum AppRoute: Hashable {
    /// `revision` changes when the same product must be re-evaluated (e.g. after a label scan) so SwiftUI
    /// creates a fresh result screen and ViewModel.
    case result(barcode: String, revision: Int = 0)
    case labelScan(barcode: String)
}

struct RootView: View {
    private enum Tab: Hashable { case scan, history }

    @State private var selectedTab = Tab.scan
    @State private var scanPath: [AppRoute] = []
    @State private var historyPath: [AppRoute] = []

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack(path: $scanPath) {
                ScannerView(onOpenResult: { scanPath.append(.result(barcode: $0)) })
                    .navigationDestination(for: AppRoute.self) { destination(for: $0, path: $scanPath) }
            }
            .tabItem { Label("tab.scan", systemImage: "barcode.viewfinder") }
            .tag(Tab.scan)

            NavigationStack(path: $historyPath) {
                HistoryView()
                    .navigationDestination(for: AppRoute.self) { destination(for: $0, path: $historyPath) }
            }
            .tabItem { Label("tab.history", systemImage: "clock.arrow.circlepath") }
            .tag(Tab.history)
        }
        // veganscanner://product/{barcode}
        .onOpenURL { url in
            guard let barcode = ScannerDeepLink.shared.barcodeFrom(url: url.absoluteString) else { return }
            selectedTab = .scan
            scanPath = [.result(barcode: barcode)]
        }
    }

    @ViewBuilder
    private func destination(for route: AppRoute, path: Binding<[AppRoute]>) -> some View {
        switch route {
        case .result(let barcode, _):
            ResultView(
                barcode: barcode,
                onScanAnother: {
                    path.wrappedValue.removeAll()
                    selectedTab = .scan
                },
                onScanLabel: { path.wrappedValue.append(.labelScan(barcode: barcode)) }
            )
            .id(route)
        case .labelScan(let barcode):
            LabelScanView(barcode: barcode, onShowResult: { showResultAfterLabelScan(barcode: $0, path: path) })
        }
    }

    /// Replaces "result → label scan" with a fresh result so the product is re-evaluated with the label.
    private func showResultAfterLabelScan(barcode: String, path: Binding<[AppRoute]>) {
        var routes = path.wrappedValue
        if case .labelScan = routes.last { routes.removeLast() }
        var nextRevision = 1
        if case .result(let previous, let previousRevision) = routes.last, previous == barcode {
            nextRevision = previousRevision + 1
            routes.removeLast()
        }
        routes.append(.result(barcode: barcode, revision: nextRevision))
        path.wrappedValue = routes
    }
}
