import SwiftUI
import VisionKit

/// Lets SwiftUI ask the live camera for a still photo.
@MainActor
final class LabelCamera {
    enum CaptureError: Error { case notReady }

    fileprivate weak var scanner: DataScannerViewController?

    /// False on the Simulator, on unsupported hardware, or when camera access was denied.
    static var isAvailable: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    func capturePhoto() async throws -> UIImage {
        guard let scanner, scanner.isScanning else { throw CaptureError.notReady }
        return try await scanner.capturePhoto()
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
        camera.scanner = controller
        return controller
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        camera.scanner = controller
        if isActive, !controller.isScanning {
            try? controller.startScanning()
        } else if !isActive, controller.isScanning {
            controller.stopScanning()
        }
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: ()) {
        controller.stopScanning()
    }
}
