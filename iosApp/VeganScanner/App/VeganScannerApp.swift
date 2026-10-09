import SwiftUI
import VeganKit

@main
struct VeganScannerApp: App {
    init() {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        #if DEBUG
        let isDebug = true
        // UI tests start from a clean database so earlier runs can't change the flow under test.
        if ProcessInfo.processInfo.arguments.contains("-resetLocalData") {
            Self.deleteLocalDatabase()
        }
        #else
        let isDebug = false
        #endif
        IosEntryPointKt.startVeganKit(
            versionName: version,
            isDebug: isDebug,
            deviceLanguage: Locale.current.language.languageCode?.identifier ?? "en",
            supabaseHost: Bundle.main.object(forInfoDictionaryKey: "SupabaseHost") as? String,
            supabasePublishableKey: Bundle.main.object(forInfoDictionaryKey: "SupabasePublishableKey") as? String,
            onDeviceModel: AppleFoundationModel()
        )
    }

    #if DEBUG
    private static func deleteLocalDatabase() {
        let fileManager = FileManager.default
        guard let documents = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first,
              let files = try? fileManager.contentsOfDirectory(atPath: documents.path) else { return }
        for file in files where file.hasPrefix("vegan_scanner.db") {
            try? fileManager.removeItem(at: documents.appendingPathComponent(file))
        }
    }
    #endif

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
