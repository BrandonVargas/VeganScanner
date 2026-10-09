import OSLog
import SwiftUI
import VeganKit

/// Photograph the ingredient list → review/fix the recognized text → re-evaluate the product.
struct LabelScanView: View {
    let barcode: String
    let onShowResult: (String) -> Void

    @State private var model: LabelScanModel
    @State private var camera = LabelCamera()
    @State private var reviewText = ""
    @State private var isVisible = false
    @Environment(\.scenePhase) private var scenePhase

    init(barcode: String, onShowResult: @escaping (String) -> Void) {
        self.barcode = barcode
        self.onShowResult = onShowResult
        _model = State(initialValue: LabelScanModel(barcode: barcode))
    }

    var body: some View {
        Group {
            switch model.state.step {
            case .capture: capture
            case .review: review
            }
        }
        .navigationTitle("label.title")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { isVisible = true }
        .onDisappear { isVisible = false }
        .task { await model.observeState() }
        .task {
            for await barcode in model.resultsToShow() {
                onShowResult(barcode)
            }
        }
        .onChange(of: model.state.draft?.revision) { _, _ in
            reviewText = model.state.draft?.text ?? ""
        }
    }

    // MARK: Capture

    private var capture: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if LabelCamera.isAvailable {
                LabelCameraView(camera: camera, isActive: isVisible && scenePhase == .active && model.state.step == .capture)
                    .ignoresSafeArea()
            } else {
                Text("label.camera_unavailable")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white)
                    .padding(32)
            }
            VStack(spacing: 16) {
                Spacer()
                Text(model.state.error == .nothingRecognized ? "label.nothing_recognized" : "label.hint")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 16))
                if LabelCamera.isAvailable {
                    Button(action: captureAndRecognize) {
                        ZStack {
                            Circle().fill(.white).frame(width: 76, height: 76)
                            if model.state.isRecognizing {
                                ProgressView().tint(.black)
                            } else {
                                Image(systemName: "camera.fill").font(.title).foregroundStyle(.black)
                            }
                        }
                    }
                    .disabled(model.state.isRecognizing)
                    .accessibilityLabel(Text("label.capture"))
                }
                Button("label.type_instead") { model.send(LabelScanActionTypeManually.shared) }
                    .foregroundStyle(.white)
                    .accessibilityIdentifier("label.typeInstead")
            }
            .padding()
        }
    }

    private func captureAndRecognize() {
        model.send(LabelScanActionCaptureStarted.shared)
        Task {
            var lines: [OcrLine] = []
            do {
                let photo = try await camera.capturePhoto()
                lines = try await TextRecognizer.recognizeLines(in: photo)
            } catch {
                Logger.label.error("Photo capture or OCR failed: \(String(describing: error))")
            }
            if lines.isEmpty {
                // The live preview has been recognizing text all along; use it rather than failing.
                lines = camera.liveLines()
                Logger.label.info("Using live preview text (\(lines.count) lines)")
            }
            if lines.isEmpty {
                model.send(LabelScanActionRecognitionFailed.shared)
            } else {
                model.send(LabelScanActionTextRecognized(lines: lines))
            }
        }
    }

    // MARK: Review

    private var review: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("label.review_title").font(.title2.bold())
                Text("label.review_body").font(.body).foregroundStyle(.secondary)
                TextEditor(text: $reviewText)
                    .frame(minHeight: 160)
                    .padding(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(model.state.error == .textTooShort ? Color.red : Color.secondary.opacity(0.4))
                    )
                    .onChange(of: reviewText) { _, _ in model.send(LabelScanActionTextEdited.shared) }
                    .accessibilityIdentifier("label.review")
                if model.state.error == .textTooShort {
                    Text("label.text_too_short").font(.footnote).foregroundStyle(.red)
                }
                HStack {
                    Button("label.retake") { model.send(LabelScanActionRetake.shared) }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)
                    Button("label.check") { model.send(LabelScanActionSubmit(text: reviewText)) }
                        .buttonStyle(.borderedProminent)
                        .disabled(model.state.isSaving)
                        .frame(maxWidth: .infinity)
                        .accessibilityIdentifier("label.check")
                }
                Label("label.privacy_note", systemImage: "lock.fill")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding()
        }
    }
}
