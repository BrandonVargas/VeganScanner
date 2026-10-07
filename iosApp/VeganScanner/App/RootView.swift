import SwiftUI
import VeganKit

/// Hashable route pushed when a barcode should be looked up.
struct ResultRoute: Hashable {
    let barcode: String
}

struct RootView: View {
    private enum Tab: Hashable { case scan, history }

    @State private var selectedTab = Tab.scan
    @State private var scanPath: [ResultRoute] = []
    @State private var historyPath: [ResultRoute] = []

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack(path: $scanPath) {
                ScannerView(onOpenResult: { scanPath.append(ResultRoute(barcode: $0)) })
                    .navigationDestination(for: ResultRoute.self) { route in
                        ResultView(barcode: route.barcode, onScanAnother: { scanPath.removeAll() })
                    }
            }
            .tabItem { Label("tab.scan", systemImage: "barcode.viewfinder") }
            .tag(Tab.scan)

            NavigationStack(path: $historyPath) {
                HistoryView()
                    .navigationDestination(for: ResultRoute.self) { route in
                        ResultView(barcode: route.barcode, onScanAnother: {
                            historyPath.removeAll()
                            selectedTab = .scan
                        })
                    }
            }
            .tabItem { Label("tab.history", systemImage: "clock.arrow.circlepath") }
            .tag(Tab.history)
        }
        // veganscanner://product/{barcode}
        .onOpenURL { url in
            guard let barcode = ScannerDeepLink.shared.barcodeFrom(url: url.absoluteString) else { return }
            selectedTab = .scan
            scanPath = [ResultRoute(barcode: barcode)]
        }
    }
}
