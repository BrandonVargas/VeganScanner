import SwiftUI
import VeganKit

struct ResultView: View {
    let barcode: String
    let onScanAnother: () -> Void
    let onScanLabel: () -> Void

    @State private var model: ResultModel

    init(barcode: String, onScanAnother: @escaping () -> Void, onScanLabel: @escaping () -> Void) {
        self.barcode = barcode
        self.onScanAnother = onScanAnother
        self.onScanLabel = onScanLabel
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
            FoundContent(
                product: found.product,
                verdict: found.verdict,
                barcode: barcode,
                isResearching: found.isResearching,
                reportedKeys: found.reportedKeys,
                researchIssue: found.researchIssue,
                unresearchedCount: Int(found.unresearchedCount),
                onScanLabel: onScanLabel,
                onReport: model.report
            )
        case .notFound:
            ContentUnavailableView {
                Label("not_found.title", systemImage: "magnifyingglass")
            } description: {
                Text("not_found.body \(barcode)")
            } actions: {
                Button("action.scan_label", systemImage: "doc.text.viewfinder", action: onScanLabel)
                    .buttonStyle(.borderedProminent)
                Button("action.scan_another", action: onScanAnother)
                    .buttonStyle(.bordered)
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
                Button("action.scan_label_offline", action: onScanLabel)
                    .buttonStyle(.bordered)
            }
        }
    }
}

private struct FoundContent: View {
    let product: Product
    let verdict: VeganVerdict
    let barcode: String
    let isResearching: Bool
    let reportedKeys: Set<String>
    let researchIssue: ResearchIssue?
    let unresearchedCount: Int
    let onScanLabel: () -> Void
    let onReport: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VerdictBanner(verdict: verdict)
                if isResearching {
                    HStack(spacing: 12) {
                        ProgressView()
                        Text("research.in_progress").font(.callout)
                    }
                }
                if let researchIssue, unresearchedCount > 0 {
                    // Research didn't happen for some ingredients: say so instead of failing silently.
                    Label(researchIssue.message(count: unresearchedCount), systemImage: researchIssue.systemImage)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
                header
                if !verdict.flaggedIngredients.isEmpty {
                    flaggedIngredients
                }
                if !verdict.researched.isEmpty {
                    ResearchedIngredients(
                        ingredients: verdict.researched,
                        onDeviceOnly: verdict.source == .onDeviceAi,
                        reportedKeys: reportedKeys,
                        onReport: onReport
                    )
                }
                if !verdict.isConclusive && !isResearching {
                    inconclusiveNotice
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

    private var isFromLabel: Bool { product.ingredientsSource == .labelScan }

    private var inconclusiveNotice: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(isFromLabel ? "result.inconclusive_notice_label" : "result.inconclusive_notice", systemImage: "info.circle.fill")
                .font(.callout)
            Button(action: onScanLabel) {
                Label(isFromLabel ? "action.rescan_label" : "action.scan_label", systemImage: "doc.text.viewfinder")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("result.scanLabel")
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var flaggedTitle: LocalizedStringKey {
        switch verdict.status {
        case .nonVegan: "result.flagged_non_vegan"
        case .likelyVegan: "result.flagged_unrecognized"
        default: "result.flagged_check"
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
            Text(flaggedTitle)
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            ForEach(Array(verdict.flaggedIngredients.enumerated()), id: \.offset) { _, flagged in
                VStack(alignment: .leading, spacing: 2) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(verbatim: "•")
                        Text(flagged.name).fontWeight(.medium)
                        Text(verbatim: "—")
                        Text(flagged.status.reason).foregroundStyle(.secondary)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        if let note = flagged.note {
                            Text(note).font(.callout).foregroundStyle(.secondary)
                        }
                        if flagged.researchKey != nil {
                            // An AI explanation of a dictionary finding: the status above didn't come from the AI.
                            Text("research.explanation_hint").font(.caption).foregroundStyle(.secondary)
                            ResearchLinks(ingredient: flagged, reportedKeys: reportedKeys, onReport: onReport)
                        }
                    }
                    .padding(.leading, 14)
                }
            }
        }
    }
}

/// Sources and the report option of a web-researched ingredient.
private struct ResearchLinks: View {
    let ingredient: FlaggedIngredient
    let reportedKeys: Set<String>
    let onReport: (String) -> Void

    var body: some View {
        ForEach(Array(ingredient.sources.prefix(3).enumerated()), id: \.offset) { _, source in
            if let url = URL(string: source.url) {
                Link(source.title, destination: url).font(.footnote)
            }
        }
        if let key = ingredient.researchKey {
            if reportedKeys.contains(key) {
                Text("research.reported").font(.footnote).foregroundStyle(.secondary)
            } else {
                Button("research.report") { onReport(key) }.font(.footnote)
            }
        }
    }
}

/// Ingredients resolved by AI web research: always shown with a warning, reasons, sources and a report option.
private struct ResearchedIngredients: View {
    let ingredients: [FlaggedIngredient]
    /// Every answer came from the phone's own model (offline), not from online research.
    let onDeviceOnly: Bool
    let reportedKeys: Set<String>
    let onReport: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(onDeviceOnly ? "research.title_on_device" : "research.title", systemImage: "sparkles")
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            Text(onDeviceOnly ? "research.warning_on_device" : "research.warning").font(.footnote)
            ForEach(Array(ingredients.enumerated()), id: \.offset) { _, ingredient in
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(ingredient.name).fontWeight(.semibold)
                        Text(verbatim: "—")
                        Text(ingredient.status == .yes ? "research.status_vegan" : ingredient.status.reason)
                    }
                    if let note = ingredient.note {
                        Text(note).font(.callout)
                    }
                    if ingredient.onDevice && !onDeviceOnly {
                        Text("research.on_device_item").font(.caption).foregroundStyle(.secondary)
                    }
                    ResearchLinks(ingredient: ingredient, reportedKeys: reportedKeys, onReport: onReport)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.purple.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
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
