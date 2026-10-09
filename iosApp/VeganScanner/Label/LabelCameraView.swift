import OSLog
import SwiftUI
import VeganKit
import VisionKit

/// Lets SwiftUI ask the live camera for a still photo, or for the text it is already tracking.
@MainActor
final class LabelCamera {
    enum CaptureError: Error { case notReady }

    fileprivate weak var scanner: DataScannerViewController?
    fileprivate var liveItems: [RecognizedItem] = []

    /// False on the Simulator, on unsupported hardware, or when camera access was denied.
    static var isAvailable: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    func capturePhoto() async throws -> UIImage {
        guard let scanner else { throw CaptureError.notReady }
        if !scanner.isScanning {
            // The session can fail to start if it was requested before the view was on screen; retry now.
            try scanner.startScanning()
            try await Task.sleep(for: .milliseconds(600))
        }
        return try await scanner.capturePhoto()
    }

    /// Lines VisionKit is highlighting in the live preview, positioned on the preview. Fallback when the photo
    /// can't be read.
    func liveLines() -> [OcrLine] {
        guard let size = scanner?.view.bounds.size, size.width > 0, size.height > 0 else { return [] }
        return liveItems.compactMap { item -> OcrLine? in
            guard case .text(let text) = item else { return nil }
            let corners = [text.bounds.topLeft, text.bounds.topRight, text.bounds.bottomLeft, text.bounds.bottomRight]
            return OcrLine(
                text: text.transcript,
                left: Float((corners.map(\.x).min() ?? 0) / size.width),
                top: Float((corners.map(\.y).min() ?? 0) / size.height),
                right: Float((corners.map(\.x).max() ?? 0) / size.width),
                bottom: Float((corners.map(\.y).max() ?? 0) / size.height)
            )
        }
    }
}

/// Live camera preview that highlights text while framing the ingredient list. Like `BarcodeScannerView`,
/// scanning is started and stopped explicitly from `isActive`.
struct LabelCameraView: UIViewControllerRepresentable {
    let camera: LabelCamera
    let isActive: Bool

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controller = DataScannerViewController(
            recognizedDataTypes: [.text()],
            qualityLevel: .accurate,
            recognizesMultipleItems: true,
            isHighFrameRateTrackingEnabled: false,
            isHighlightingEnabled: true
        )
        controller.delegate = context.coordinator
        camera.scanner = controller
        return controller
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        camera.scanner = controller
        if isActive, !controller.isScanning {
            do {
                try controller.startScanning()
            } catch {
                Logger.label.error("Could not start the label camera: \(error.localizedDescription)")
            }
        } else if !isActive, controller.isScanning {
            controller.stopScanning()
        }
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(camera: camera)
    }

    @MainActor
    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        private let camera: LabelCamera

        init(camera: LabelCamera) {
            self.camera = camera
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            camera.liveItems = allItems
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didUpdate updatedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            camera.liveItems = allItems
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didRemove removedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            camera.liveItems = allItems
        }
    }
}
