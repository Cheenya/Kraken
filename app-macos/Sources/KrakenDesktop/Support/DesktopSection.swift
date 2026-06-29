import SwiftUI

enum DesktopSection: String, CaseIterable, Identifiable {
    case chat
    case contacts
    case mesh
    case research
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .chat: "Чаты"
        case .contacts: "Контакты"
        case .mesh: "Реалмы"
        case .research: "Исследования"
        case .settings: "Настройки"
        }
    }

    var shortTitle: String {
        switch self {
        case .chat: "Чаты"
        case .contacts: "Контакты"
        case .mesh: "Реалмы"
        case .research: "Исслед."
        case .settings: "Настр."
        }
    }

    var systemImage: String {
        switch self {
        case .chat: "bubble.left.and.bubble.right"
        case .contacts: "person.crop.circle"
        case .mesh: "person.3"
        case .research: "chart.xyaxis.line"
        case .settings: "gearshape"
        }
    }

    static let primaryTabs: [DesktopSection] = [.chat, .contacts, .mesh]
}

enum SettingsPane: String, CaseIterable, Identifiable {
    case identity
    case transport
    case lanBridge
    case appearance
    case typography
    case technical

    var id: String { rawValue }

    var title: String {
        switch self {
        case .identity: "Личность"
        case .transport: "Каналы связи"
        case .lanBridge: "LAN/ADB мост"
        case .appearance: "Оформление"
        case .typography: "Шрифты"
        case .technical: "Технические"
        }
    }

    var subtitle: String {
        switch self {
        case .identity: "Профиль и QR"
        case .transport: "WiFi и Bluetooth"
        case .lanBridge: "Адреса и порты"
        case .appearance: "Темы и палитра"
        case .typography: "Размеры текста"
        case .technical: "Диагностика"
        }
    }

    var systemImage: String {
        switch self {
        case .identity: "person.crop.circle"
        case .transport: "antenna.radiowaves.left.and.right"
        case .lanBridge: "network"
        case .appearance: "paintpalette"
        case .typography: "textformat.size"
        case .technical: "terminal"
        }
    }
}
