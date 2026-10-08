import SwiftUI
import VeganKit

struct HistoryView: View {
    @State private var model = HistoryModel()
    @State private var isConfirmingClear = false

    var body: some View {
        content
            .navigationTitle("tab.history")
            .toolbar {
                if !model.state.entries.isEmpty {
                    Button("history.clear", systemImage: "trash", role: .destructive) {
                        isConfirmingClear = true
                    }
                }
            }
            .confirmationDialog("history.clear_title", isPresented: $isConfirmingClear, titleVisibility: .visible) {
                Button("history.clear", role: .destructive, action: model.clearAll)
            } message: {
                Text("history.clear_body")
            }
            .task { await model.observeState() }
    }

    @ViewBuilder
    private var content: some View {
        if model.state.isLoading {
            ProgressView()
        } else if model.state.entries.isEmpty {
            ContentUnavailableView("history.empty_title", systemImage: "clock", description: Text("history.empty_body"))
        } else {
            List {
                ForEach(model.state.entries, id: \.barcode) { entry in
                    NavigationLink(value: AppRoute.result(barcode: entry.barcode)) {
                        HistoryRow(entry: entry)
                    }
                }
                .onDelete { offsets in
                    offsets.map { model.state.entries[$0].barcode }.forEach(model.delete)
                }
            }
        }
    }
}

private struct HistoryRow: View {
    let entry: ScanHistoryEntry

    var body: some View {
        let style = entry.status.style
        HStack(spacing: 12) {
            Image(systemName: style.systemImage)
                .foregroundStyle(style.foreground)
                .frame(width: 40, height: 40)
                .background(style.background, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.productName ?? String(localized: "product.unnamed")).font(.body)
                HStack(spacing: 4) {
                    Text(style.label)
                    if let brands = entry.brands {
                        Text(verbatim: "· \(brands)")
                    }
                    Text(verbatim: "· ") + Text(entry.scannedAt.date, format: .relative(presentation: .named))
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            }
        }
    }
}
