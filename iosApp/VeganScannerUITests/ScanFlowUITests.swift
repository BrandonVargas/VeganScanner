import XCTest

/// End-to-end flows through the real UI. The simulator has no camera, so lookups use manual entry.
final class ScanFlowUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-resetLocalData"]
        app.launch()
    }

    /// Offline: validation happens in shared Kotlin code before any network call.
    func testInvalidBarcodeShowsValidationError() {
        enterBarcode("3017620422004")
        XCTAssertTrue(app.staticTexts["manualEntry.error"].waitForExistence(timeout: 15))
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

    /// Live: the scanner must keep opening results after returning from one (regression test).
    func testConsecutiveLookups() {
        enterBarcode("3017620422003")
        XCTAssertTrue(app.staticTexts["Not vegan"].waitForExistence(timeout: 20))
        app.navigationBars.buttons.element(boundBy: 0).tap()

        enterBarcode("7500327047878")
        XCTAssertTrue(app.staticTexts["Vegan"].waitForExistence(timeout: 20))
    }

    /// Live: a product missing from Open Food Facts is evaluated from its typed/scanned ingredient label.
    func testLabelScanEvaluatesUnknownProduct() {
        enterBarcode("7501000000012")
        let scanLabel = app.buttons["Scan ingredient label"]
        XCTAssertTrue(scanLabel.waitForExistence(timeout: 20))
        attachScreenshot(named: "not-found")
        scanLabel.tap()

        let typeInstead = app.buttons["label.typeInstead"]
        XCTAssertTrue(typeInstead.waitForExistence(timeout: 10))
        typeInstead.tap()

        let review = app.textViews["label.review"]
        XCTAssertTrue(review.waitForExistence(timeout: 10))
        review.tap()
        review.typeText("Agua, azúcar, grenetina, leche de coco")
        attachScreenshot(named: "label-review")
        app.buttons["label.check"].tap()

        XCTAssertTrue(app.staticTexts["Not vegan"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["Based on the ingredient label you scanned"].exists)
        XCTAssertTrue(app.staticTexts["grenetina"].exists)
        attachScreenshot(named: "label-result")
    }

    /// Uses accessibility identifiers (not localized text) and waits on state, so slow CI simulators don't flake.
    private func enterBarcode(_ barcode: String) {
        let field = app.textFields["manualEntry.field"]
        XCTAssertTrue(field.waitForExistence(timeout: 30))
        field.tap()
        field.typeText(barcode)
        XCTAssertEqual(field.value as? String, barcode)

        let submit = app.buttons["manualEntry.submit"]
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: submit)
        wait(for: [enabled], timeout: 10)
        submit.tap()
    }

    private func attachScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
