import OSLog
import UIKit
import VeganKit
import Vision

/// On-device OCR with Apple's Vision framework. Nothing leaves the device.
enum TextRecognizer {
    private static let preferredLanguages = ["es-ES", "en-US"]

    /// Recognized lines positioned on the upright photo (top-left origin, 0…1), so shared code can follow the
    /// ingredient column and skip neighboring paragraphs.
    static func recognizeLines(in image: UIImage) async throws -> [OcrLine] {
        // Photos from the camera aren't always backed by a CGImage and often carry an orientation flag.
        // Redrawing gives an upright, CGImage-backed bitmap that Vision can always read.
        guard let cgImage = uprightCGImage(from: image) else {
            Logger.label.error("Captured photo has no bitmap data")
            return []
        }
        return try await Task.detached(priority: .userInitiated) {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            // The default skips text under 1/32 of the photo's height, which drops small ingredient print.
            request.minimumTextHeight = 0.01
            let supported = (try? request.supportedRecognitionLanguages()) ?? []
            request.recognitionLanguages = preferredLanguages.filter(supported.contains)
            try VNImageRequestHandler(cgImage: cgImage, orientation: .up).perform([request])
            let lines = (request.results ?? []).compactMap { observation -> OcrLine? in
                guard let text = observation.topCandidates(1).first?.string else { return nil }
                let box = observation.boundingBox // normalized, bottom-left origin
                return OcrLine(
                    text: text,
                    left: Float(box.minX),
                    top: Float(1 - box.maxY),
                    right: Float(box.maxX),
                    bottom: Float(1 - box.minY)
                )
            }
            Logger.label.info("Photo OCR read \(lines.count) lines")
            return lines
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
