import SwiftUI
import VeganKit

@main
struct VeganScannerApp: App {
    init() {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        IosEntryPointKt.startVeganKit(versionName: version, isDebug: isDebug)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
