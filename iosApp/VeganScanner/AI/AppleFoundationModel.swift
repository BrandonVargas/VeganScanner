import Foundation
import FoundationModels
import OSLog
import VeganKit

/// Apple Foundation Models (the on-device model behind Apple Intelligence, iOS 26+) for shared code's
/// on-device ingredient fallback. Unavailable on older iOS versions, unsupported devices, or when Apple
/// Intelligence is turned off or still downloading; shared code then simply skips it.
final class AppleFoundationModel: NSObject, OnDeviceLanguageModel {
    // SKIE exposes the Kotlin suspend functions to Swift as these async requirements.
    func __isAvailable() async throws -> KotlinBoolean {
        guard #available(iOS 26.0, *) else { return KotlinBoolean(bool: false) }
        let availability = SystemLanguageModel.default.availability
        if case .unavailable(let reason) = availability {
            Logger.onDeviceAI.debug("Foundation Models unavailable: \(String(describing: reason))")
        }
        return KotlinBoolean(bool: availability == .available)
    }

    func __generate(instructions: String, prompt: String) async throws -> String? {
        guard #available(iOS 26.0, *) else { return nil }
        do {
            // A fresh session per ingredient, so one label item can't influence the next answer.
            let session = LanguageModelSession(instructions: instructions)
            let response = try await session.respond(
                to: prompt,
                options: GenerationOptions(sampling: .greedy, maximumResponseTokens: 160)
            )
            return response.content
        } catch {
            // Guardrail refusals, unsupported language, context limits: no answer rather than an error.
            Logger.onDeviceAI.error("Foundation Models failed: \(String(describing: error))")
            return nil
        }
    }
}

extension Logger {
    static let onDeviceAI = Logger(subsystem: "dev.brandonvargas.veganscanner", category: "OnDeviceAI")
}
