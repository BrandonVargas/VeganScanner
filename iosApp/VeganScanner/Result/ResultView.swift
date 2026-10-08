import SwiftUI
import VeganKit

struct ResultView: View {
    let barcode: String
    let onScanAnother: () -> Void

    @State private var model: ResultModel

    init(barcode: String, onScanAnother: @escaping () -> Void) {
        self.barcode = barcode
        self.onScanAnother = onScanAnother
        _model = State(initialValue: ResultModel(barcode: barcode))
    }

    var body: some View {
        content
            .navigationTitle("result.title")
            .navigationBarTitleDisplayMode(.inline)
            .task { await model.observeState() }
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: model.state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .found(let found):
            FoundContent(product: found.product, verdict: found.verdict, barcode: barcode)
        case .notFound:
            ContentUnavailableView {
                Label("not_found.title", systemImage: "magnifyingglass")
            } description: {
                Text("not_found.body \(barcode)")
            } actions: {
                Button("action.scan_another", action: onScanAnother)
                    .buttonStyle(.borderedProminent)
                if let url = URL(string: "https://world.openfoodfacts.org/product/\(barcode)") {
                    Link("action.open_in_off", destination: url)
                }
            }
        case .error(let error):
            ContentUnavailableView {
                Label("error.title", systemImage: "wifi.exclamationmark")
            } description: {
                Text(errorMessage(error.error))
            } actions: {
                Button("action.retry", action: model.retry)
                    .buttonStyle(.borderedProminent)
            }
        }
    }
}

private struct FoundContent: View {
    let product: Product
    let verdict: VeganVerdict
    let barcode: String

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VerdictBanner(verdict: verdict)
                header
                if !verdict.flaggedIngredients.isEmpty {
                    flaggedIngredients
                }
                if !verdict.isConclusive {
                    Label("result.inconclusive_notice", systemImage: "info.circle.fill")
                        .font(.callout)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
                }
                if let ingredients = product.ingredientsText {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("result.ingredients").font(.headline).accessibilityAddTraits(.isHeader)
                        Text(ingredients).font(.body)
                    }
                }
                Divider()
                Text("result.disclaimer")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding()
        }
    }

    private var header: some View {
        HStack(spacing: 16) {
            if let imageUrl = product.imageUrl, let url = URL(string: imageUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.secondary.opacity(0.15)
                }
                .frame(width: 72, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(product.name ?? String(localized: "product.unnamed")).font(.title3.weight(.semibold))
                if let brands = product.brands {
                    Text(brands).font(.subheadline)
                }
                Text(barcode).font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private var flaggedIngredients: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(verdict.status == .nonVegan ? "result.flagged_non_vegan" : "result.flagged_check")
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            ForEach(Array(verdict.flaggedIngredients.enumerated()), id: \.offset) { _, flagged in
                HStack(alignment: .firstTextBaseline) {
                    Text("•")
                    Text(flagged.name).fontWeight(.medium)
                    Text("—")
                    Text(flagged.status.reason).foregroundStyle(.secondary)
                }
            }
        }
    }
}

private struct VerdictBanner: View {
    let verdict: VeganVerdict

    var body: some View {
        let style = verdict.status.style
        HStack(spacing: 16) {
            Image(systemName: style.systemImage)
                .font(.system(size: 44))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(style.label)
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                Text(verdict.source.label).font(.subheadline)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .foregroundStyle(style.foreground)
        .background(style.background, in: RoundedRectangle(cornerRadius: 20))
        .accessibilityElement(children: .combine)
    }
}
