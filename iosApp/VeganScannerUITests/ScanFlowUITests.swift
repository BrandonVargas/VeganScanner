import XCTest

/// End-to-end flows through the real UI. The simulator has no camera, so lookups use manual entry.
final class ScanFlowUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    /// Offline: validation happens in shared Kotlin code before any network call.
    func testInvalidBarcodeShowsValidationError() {
        enterBarcode("3017620422004")
        XCTAssertTrue(app.staticTexts["That doesn't look like a valid barcode"].waitForExistence(timeout: 5))
        attachScreenshot(named: "invalid-barcode")
    }

    /// Live: hits the Open Food Facts API. Skipped in CI (see .github/workflows/ci.yml).
    func testNonVeganProductShowsReasons() {
        enterBarcode("3017620422003")
        XCTAssertTrue(app.staticTexts["Not vegan"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["Non-vegan ingredients"].exists)
        attachScreenshot(named: "result-non-vegan")

        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["Nutella"].waitForExistence(timeout: 5))
        attachScreenshot(named: "history")
    }

    private func enterBarcode(_ barcode: String) {
        let field = app.textFields["e.g. 7501234567893"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText(barcode)
        app.buttons["Look up"].tap()
    }

    private func attachScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
