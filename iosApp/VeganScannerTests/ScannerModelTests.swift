import Testing
import VeganKit
@testable import VeganScanner

/// Runs inside the app host, so Koin is already started by `VeganScannerApp`.
@MainActor
struct ScannerModelTests {
    @Test func sanitizeKeepsDigitsOnly() {
        #expect(ScannerModel().sanitize("3017-62a0422003") == "3017620422003")
    }

    @Test func invalidManualEntryIsFlaggedUntilEdited() {
        let model = ScannerModel()
        model.submitManualEntry("3017620422004")
        #expect(model.state.isManualEntryInvalid)
        model.manualEntryEdited()
        #expect(!model.state.isManualEntryInvalid)
    }

    @Test func validManualEntryOpensResult() async {
        let model = ScannerModel()
        model.submitManualEntry("3017620422003")

        var iterator = model.resultsToOpen().makeAsyncIterator()
        let barcode = await iterator.next()
        #expect(barcode == "3017620422003")
    }
}

struct VerdictPresentationTests {
    @Test(arguments: [VeganStatus.vegan, .nonVegan, .maybeVegan, .unknown])
    func everyStatusHasASymbol(status: VeganStatus) {
        #expect(!status.style.systemImage.isEmpty)
    }
}
