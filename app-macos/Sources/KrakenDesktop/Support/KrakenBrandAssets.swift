import AppKit
import SwiftUI

enum KrakenBrandAsset: String {
    case appIcon = "kraken_favicon_512"
    case startBackground = "kraken_start_background"
    case splash = "kraken_splash_dark"
}

enum KrakenBrandAssets {
    static func image(_ asset: KrakenBrandAsset) -> NSImage {
        if let image = NSImage(named: asset.rawValue) {
            return image
        }
        if let url = Bundle.module.url(forResource: asset.rawValue, withExtension: "png"),
           let image = NSImage(contentsOf: url) {
            return image
        }
        return NSImage(size: NSSize(width: 1, height: 1))
    }
}

enum KrakenColors {
    static let accent = KrakenThemePreset.deep.palette.accent
    static let accentDeep = KrakenThemePreset.deep.palette.accentDeep
    static let activeGreen = Color(red: 0.10, green: 0.50, blue: 0.28)
    static let windowBackground = KrakenThemePreset.deep.palette.windowBackground
    static let sidebarBackground = KrakenThemePreset.deep.palette.sidebarBackground
    static let detailBackground = KrakenThemePreset.deep.palette.detailBackground
    static let panelBackground = KrakenThemePreset.deep.palette.panelBackground
    static let elevatedPanel = KrakenThemePreset.deep.palette.elevatedPanel
    static let surface = panelBackground

    static var selectionGradient: LinearGradient {
        KrakenThemePreset.deep.palette.selectionGradient
    }
}

struct KrakenThemePalette {
    let accent: Color
    let accentDeep: Color
    let windowBackground: Color
    let sidebarBackground: Color
    let detailBackground: Color
    let messageBackground: Color
    let panelBackground: Color
    let elevatedPanel: Color
    let inputBackground: Color
    let separator: Color
    let selectedText: Color

    var selectionGradient: LinearGradient {
        LinearGradient(
            colors: [accent.krakenDarkened(by: 0.64), accentDeep.krakenDarkened(by: 0.72)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    func applyingCustomAccent(_ accent: Color?) -> KrakenThemePalette {
        guard let accent else { return self }
        return KrakenThemePalette(
            accent: accent,
            accentDeep: accent.krakenDarkened(by: 0.72),
            windowBackground: windowBackground,
            sidebarBackground: sidebarBackground,
            detailBackground: detailBackground,
            messageBackground: messageBackground,
            panelBackground: panelBackground,
            elevatedPanel: elevatedPanel,
            inputBackground: inputBackground,
            separator: separator,
            selectedText: selectedText
        )
    }
}

enum KrakenThemePreset: String, CaseIterable, Identifiable {
    case abyss
    case deep
    case graphite
    case dusk
    case ice
    case pearl

    var id: String { rawValue }

    var title: String {
        switch self {
        case .abyss: "Бездна"
        case .deep: "Глубина"
        case .graphite: "Графит"
        case .dusk: "Сумерки"
        case .ice: "Лёд"
        case .pearl: "Жемчуг"
        }
    }

    var subtitle: String {
        switch self {
        case .abyss: "самая тёмная"
        case .deep: "тёмная фирменная"
        case .graphite: "контрастная"
        case .dusk: "мягкая"
        case .ice: "светлая холодная"
        case .pearl: "самая светлая"
        }
    }

    var preferredColorScheme: ColorScheme {
        switch self {
        case .ice, .pearl: .light
        case .abyss, .deep, .graphite, .dusk: .dark
        }
    }

    var palette: KrakenThemePalette {
        switch self {
        case .abyss:
            KrakenThemePalette(
                accent: Color(red: 0.00, green: 0.34, blue: 0.40),
                accentDeep: Color(red: 0.00, green: 0.15, blue: 0.20),
                windowBackground: Color(red: 0.004, green: 0.010, blue: 0.014),
                sidebarBackground: Color(red: 0.014, green: 0.024, blue: 0.028),
                detailBackground: Color(red: 0.018, green: 0.027, blue: 0.031),
                messageBackground: Color(red: 0.016, green: 0.024, blue: 0.028),
                panelBackground: Color(red: 0.026, green: 0.036, blue: 0.040),
                elevatedPanel: Color(red: 0.048, green: 0.060, blue: 0.066),
                inputBackground: Color(red: 0.064, green: 0.078, blue: 0.084),
                separator: Color.white.opacity(0.10),
                selectedText: .white
            )
        case .deep:
            KrakenThemePalette(
                accent: Color(red: 0.05, green: 0.72, blue: 0.80),
                accentDeep: Color(red: 0.00, green: 0.42, blue: 0.46),
                windowBackground: Color(red: 0.104, green: 0.122, blue: 0.128),
                sidebarBackground: Color(red: 0.122, green: 0.142, blue: 0.148),
                detailBackground: Color(red: 0.148, green: 0.162, blue: 0.168),
                messageBackground: Color(red: 0.172, green: 0.184, blue: 0.190),
                panelBackground: Color(red: 0.190, green: 0.206, blue: 0.212),
                elevatedPanel: Color(red: 0.238, green: 0.256, blue: 0.264),
                inputBackground: Color(red: 0.292, green: 0.310, blue: 0.318),
                separator: Color.white.opacity(0.16),
                selectedText: .white
            )
        case .graphite:
            KrakenThemePalette(
                accent: Color(red: 0.00, green: 0.74, blue: 0.78),
                accentDeep: Color(red: 0.05, green: 0.42, blue: 0.46),
                windowBackground: Color(red: 0.138, green: 0.146, blue: 0.150),
                sidebarBackground: Color(red: 0.158, green: 0.168, blue: 0.174),
                detailBackground: Color(red: 0.190, green: 0.198, blue: 0.202),
                messageBackground: Color(red: 0.216, green: 0.224, blue: 0.228),
                panelBackground: Color(red: 0.236, green: 0.246, blue: 0.252),
                elevatedPanel: Color(red: 0.284, green: 0.296, blue: 0.302),
                inputBackground: Color(red: 0.342, green: 0.354, blue: 0.360),
                separator: Color.white.opacity(0.15),
                selectedText: .white
            )
        case .dusk:
            KrakenThemePalette(
                accent: Color(red: 0.00, green: 0.48, blue: 0.56),
                accentDeep: Color(red: 0.02, green: 0.24, blue: 0.31),
                windowBackground: Color(red: 0.025, green: 0.038, blue: 0.044),
                sidebarBackground: Color(red: 0.042, green: 0.056, blue: 0.062),
                detailBackground: Color(red: 0.052, green: 0.062, blue: 0.068),
                messageBackground: Color(red: 0.062, green: 0.073, blue: 0.078),
                panelBackground: Color(red: 0.082, green: 0.094, blue: 0.100),
                elevatedPanel: Color(red: 0.128, green: 0.142, blue: 0.148),
                inputBackground: Color(red: 0.176, green: 0.190, blue: 0.196),
                separator: Color.white.opacity(0.13),
                selectedText: .white
            )
        case .ice:
            KrakenThemePalette(
                accent: Color(red: 0.00, green: 0.58, blue: 0.66),
                accentDeep: Color(red: 0.55, green: 0.82, blue: 0.88),
                windowBackground: Color(red: 0.830, green: 0.880, blue: 0.890),
                sidebarBackground: Color(red: 0.730, green: 0.805, blue: 0.820),
                detailBackground: Color(red: 0.900, green: 0.930, blue: 0.936),
                messageBackground: Color(red: 0.930, green: 0.952, blue: 0.956),
                panelBackground: Color(red: 0.790, green: 0.850, blue: 0.862),
                elevatedPanel: Color(red: 0.965, green: 0.982, blue: 0.984),
                inputBackground: Color(red: 0.985, green: 0.995, blue: 0.997),
                separator: Color.black.opacity(0.16),
                selectedText: .white
            )
        case .pearl:
            KrakenThemePalette(
                accent: Color(red: 0.00, green: 0.55, blue: 0.62),
                accentDeep: Color(red: 0.48, green: 0.78, blue: 0.84),
                windowBackground: Color(red: 0.915, green: 0.945, blue: 0.948),
                sidebarBackground: Color(red: 0.835, green: 0.895, blue: 0.905),
                detailBackground: Color(red: 0.955, green: 0.972, blue: 0.974),
                messageBackground: Color(red: 0.975, green: 0.986, blue: 0.988),
                panelBackground: Color(red: 0.900, green: 0.935, blue: 0.940),
                elevatedPanel: Color(red: 0.985, green: 0.993, blue: 0.994),
                inputBackground: Color.white,
                separator: Color.black.opacity(0.14),
                selectedText: .white
            )
        }
    }
}

extension Color {
    static func krakenHex(_ value: String) -> Color? {
        var hex = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if hex.hasPrefix("#") {
            hex.removeFirst()
        }
        guard hex.count == 6, let raw = Int(hex, radix: 16) else { return nil }
        return Color(
            red: Double((raw >> 16) & 0xff) / 255.0,
            green: Double((raw >> 8) & 0xff) / 255.0,
            blue: Double(raw & 0xff) / 255.0
        )
    }

    func krakenHexString() -> String? {
        guard let color = NSColor(self).usingColorSpace(.deviceRGB) else { return nil }
        let red = Int((color.redComponent * 255).rounded())
        let green = Int((color.greenComponent * 255).rounded())
        let blue = Int((color.blueComponent * 255).rounded())
        return String(format: "#%02X%02X%02X", red, green, blue)
    }

    func krakenDarkened(by multiplier: CGFloat) -> Color {
        guard let color = NSColor(self).usingColorSpace(.deviceRGB) else { return self }
        return Color(
            red: Double(color.redComponent * multiplier),
            green: Double(color.greenComponent * multiplier),
            blue: Double(color.blueComponent * multiplier)
        )
    }
}

private struct KrakenThemePaletteKey: EnvironmentKey {
    static let defaultValue = KrakenThemePreset.deep.palette
}

extension EnvironmentValues {
    var krakenPalette: KrakenThemePalette {
        get { self[KrakenThemePaletteKey.self] }
        set { self[KrakenThemePaletteKey.self] = newValue }
    }
}

struct KrakenBrandImage: View {
    let asset: KrakenBrandAsset

    var body: some View {
        Image(nsImage: KrakenBrandAssets.image(asset))
            .resizable()
    }
}
