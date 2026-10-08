import SwiftUI
import VeganKit

/// Visual treatment for each verdict. Colors adapt to light/dark mode.
struct VerdictStyle {
    let background: Color
    let foreground: Color
    let systemImage: String
    let label: LocalizedStringKey
}

extension VeganStatus {
    var style: VerdictStyle {
        switch self {
        case .vegan:
            VerdictStyle(
                background: .adaptive(light: 0xB1F2A2, dark: 0x155115),
                foreground: .adaptive(light: 0x002203, dark: 0xB1F2A2),
                systemImage: "checkmark.circle.fill",
                label: "verdict.vegan"
            )
        case .nonVegan:
            VerdictStyle(
                background: .adaptive(light: 0xFFDAD6, dark: 0x93000A),
                foreground: .adaptive(light: 0x410002, dark: 0xFFDAD6),
                systemImage: "nosign",
                label: "verdict.non_vegan"
            )
        case .maybeVegan:
            VerdictStyle(
                background: .adaptive(light: 0xFFDEA6, dark: 0x5D4200),
                foreground: .adaptive(light: 0x271900, dark: 0xFFDEA6),
                systemImage: "exclamationmark.triangle.fill",
                label: "verdict.maybe"
            )
        case .unknown:
            VerdictStyle(
                background: .adaptive(light: 0xDEE5D8, dark: 0x424940),
                foreground: .adaptive(light: 0x181D17, dark: 0xDFE4DA),
                systemImage: "questionmark.circle.fill",
                label: "verdict.unknown"
            )
        }
    }
}

extension VerdictSource {
    var label: LocalizedStringKey {
        switch self {
        case .openFoodFacts: "source.off_analysis"
        case .openFoodFactsIngredients: "source.off_ingredients"
        case .undetermined: "source.undetermined"
        }
    }
}

extension IngredientVeganStatus {
    var reason: LocalizedStringKey {
        switch self {
        case .no: "ingredient_status.no"
        case .maybe: "ingredient_status.maybe"
        case .unknown, .yes: "ingredient_status.unknown"
        }
    }
}

func errorMessage(_ error: AppError) -> LocalizedStringKey {
    switch onEnum(of: error) {
    case .network: "error.network"
    case .serviceUnavailable: "error.service_unavailable"
    case .rateLimited: "error.rate_limited"
    case .unexpected: "error.unexpected"
    }
}

extension Color {
    static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

extension KotlinInstant {
    var date: Date {
        Date(timeIntervalSince1970: TimeInterval(toEpochMilliseconds()) / 1000)
    }
}
