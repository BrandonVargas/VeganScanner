import OSLog
import SwiftUI
import VisionKit

/// Live camera barcode scanner backed by VisionKit's `DataScannerViewController` (on-device).
///
/// `DataScannerViewController` stops scanning when its view disappears (for example, when a result is pushed on
/// top of it) and doesn't resume by itself. `isActive` drives `startScanning()`/`stopScanning()` explicitly so
/// the scanner restarts every time the screen becomes visible again.
struct BarcodeScannerView: UIViewControllerRepresentable {
    let isActive: Bool
    let onBarcodeDetected: (String) -> Void

    /// False on the Simulator, on unsupported hardware, or when camera access was denied.
    static var isAvailable: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controller = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.ean13, .ean8])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isHighlightingEnabled: true
        )
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        context.coordinator.onBarcodeDetected = onBarcodeDetected
        if isActive, !controller.isScanning {
            do {
                try controller.startScanning()
            } catch {
                Logger.scanner.error("Could not start scanning: \(error.localizedDescription)")
            }
        } else if !isActive, controller.isScanning {
            // Stopping also clears recognized items, so the same product can be scanned again later.
            controller.stopScanning()
        }
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onBarcodeDetected: onBarcodeDetected)
    }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var onBarcodeDetected: (String) -> Void

        init(onBarcodeDetected: @escaping (String) -> Void) {
            self.onBarcodeDetected = onBarcodeDetected
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            for item in addedItems {
                if case .barcode(let barcode) = item, let payload = barcode.payloadStringValue {
                    onBarcodeDetected(payload)
                }
            }
        }

        func dataScanner(_ dataScanner: DataScannerViewController, becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable) {
            Logger.scanner.error("Scanner became unavailable: \(String(describing: error))")
        }
    }
}

private extension Logger {
    static let scanner = Logger(subsystem: "dev.brandonvargas.veganscanner", category: "Scanner")
}
