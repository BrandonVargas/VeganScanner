import SwiftUI
import VeganKit

struct ScannerView: View {
    let onOpenResult: (String) -> Void

    @State private var model = ScannerModel()
    @State private var manualEntry = ""
    @FocusState private var isManualEntryFocused: Bool
    @Environment(\.openURL) private var openURL

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if BarcodeScannerView.isAvailable {
                BarcodeScannerView(onBarcodeDetected: model.barcodeDetected)
                    .ignoresSafeArea()
            } else {
                cameraUnavailable
            }

            VStack {
                Text("scanner.hint")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(.black.opacity(0.55), in: Capsule())
                    .padding(.top, 8)
                Spacer()
                manualEntryCard
            }
            .padding()
        }
        .toolbar(.hidden, for: .navigationBar)
        .task { await model.observeState() }
        .task {
            for await barcode in model.resultsToOpen() {
                isManualEntryFocused = false
                manualEntry = ""
                onOpenResult(barcode)
            }
        }
    }

    private var cameraUnavailable: some View {
        VStack(spacing: 12) {
            Image(systemName: "camera.fill")
                .font(.system(size: 44))
                .foregroundStyle(.white)
            Text("camera.unavailable")
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
            Button("camera.open_settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(32)
    }

    private var manualEntryCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("manual_entry.title").font(.subheadline.weight(.semibold))
            HStack {
                TextField("manual_entry.placeholder", text: $manualEntry)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
                    .focused($isManualEntryFocused)
                    .onChange(of: manualEntry) { _, newValue in
                        let sanitized = model.sanitize(newValue)
                        if sanitized != newValue { manualEntry = sanitized }
                        model.manualEntryEdited()
                    }
                    .onSubmit { model.submitManualEntry(manualEntry) }
                Button {
                    model.submitManualEntry(manualEntry)
                } label: {
                    Image(systemName: "magnifyingglass")
                        .accessibilityLabel(Text("manual_entry.submit"))
                }
                .buttonStyle(.borderedProminent)
                .disabled(manualEntry.isEmpty)
            }
            if model.state.isManualEntryInvalid {
                Text("manual_entry.invalid")
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
}
