import OSLog
import UIKit
import Vision

/// On-device OCR with Apple's Vision framework. Nothing leaves the device.
enum TextRecognizer {
    private static let preferredLanguages = ["es-ES", "en-US"]

    static func recognizeText(in image: UIImage) async throws -> String {
        // Photos from the camera aren't always backed by a CGImage and often carry an orientation flag.
        // Redrawing gives an upright, CGImage-backed bitmap that Vision can always read.
        guard let cgImage = uprightCGImage(from: image) else {
            Logger.label.error("Captured photo has no bitmap data")
            return ""
        }
        return try await Task.detached(priority: .userInitiated) {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            let supported = (try? request.supportedRecognitionLanguages()) ?? []
            request.recognitionLanguages = preferredLanguages.filter(supported.contains)
            try VNImageRequestHandler(cgImage: cgImage, orientation: .up).perform([request])
            let text = (request.results ?? [])
                .compactMap { $0.topCandidates(1).first?.string }
                .joined(separator: "\n")
            Logger.label.info("Photo OCR read \(text.count) characters")
            return text
        }.value
    }

    private static func uprightCGImage(from image: UIImage) -> CGImage? {
        if image.imageOrientation == .up, let cgImage = image.cgImage { return cgImage }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = image.scale
        return UIGraphicsImageRenderer(size: image.size, format: format)
            .image { _ in image.draw(in: CGRect(origin: .zero, size: image.size)) }
            .cgImage
    }
}

extension Logger {
    static let label = Logger(subsystem: "dev.brandonvargas.veganscanner", category: "LabelScan")
}
