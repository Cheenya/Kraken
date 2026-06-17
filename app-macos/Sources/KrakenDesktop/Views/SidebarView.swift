import KrakenDesktopCore
import SwiftUI

struct SidebarView: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore
    let onShowIdentityQr: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            SidebarTopBar()

            SectionSwitcher()

            Divider()

            ScrollView {
                LazyVStack(spacing: 0) {
                    if store.selectedSection == .chat {
                        ForEach(store.filteredRelationships) { relationship in
                            Button {
                                store.selectRelationship(relationship.relationshipId)
                            } label: {
                                SidebarPeerRow(
                                    relationship: relationship,
                                    route: store.state.routes.first { $0.relationshipId == relationship.relationshipId },
                                    selected: store.selectedRelationshipId == relationship.relationshipId,
                                    lastMessage: lastMessage(for: relationship)
                                )
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button {
                                    store.selectRelationship(relationship.relationshipId)
                                } label: {
                                    Label("Открыть чат", systemImage: "bubble.left.and.bubble.right")
                                }

                                Button {
                                    store.selectRelationship(relationship.relationshipId)
                                    store.bindCurrentLanEndpointToSelectedPeer()
                                } label: {
                                    Label("Привязать LAN/ADB endpoint", systemImage: "link")
                                }

                                Divider()

                                Button(role: .destructive) {
                                    store.deleteRelationship(relationship.relationshipId)
                                } label: {
                                    Label("Удалить контакт", systemImage: "trash")
                                }
                            }
                        }
                    } else if store.selectedSection == .mesh {
                        RealmsSidebarList()
                    } else if store.selectedSection == .settings {
                        SettingsSidebarList()
                    } else {
                        SidebarSectionSummary(section: store.selectedSection)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
            }

            Divider()

            SidebarFooter(onShowIdentityQr: onShowIdentityQr)
        }
        .background(palette.sidebarBackground)
        .frame(maxHeight: .infinity, alignment: .top)
    }

    private func lastMessage(for relationship: Relationship) -> LocalMessage? {
        store.state.messages.last { $0.relationshipId == relationship.relationshipId }
    }
}

private struct SidebarTopBar: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Text("")
                    .frame(width: 80, height: 28)

                Text(store.selectedSection.title)
                    .font(.system(size: 17, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .frame(maxWidth: .infinity)

                TransportStatusStrip()
                    .frame(width: 80, alignment: .trailing)
            }
            .frame(height: 36)
            .padding(.top, 8)
            .padding(.bottom, 8)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(palette.accent.opacity(0.88))
                TextField("Поиск", text: $store.searchText)
                    .textFieldStyle(.plain)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(palette.inputBackground, in: RoundedRectangle(cornerRadius: 10))
            .overlay {
                RoundedRectangle(cornerRadius: 10)
                    .stroke(palette.accent.opacity(0.16), lineWidth: 1)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 0)
        .padding(.bottom, 10)
        .background(palette.sidebarBackground)
        .fixedSize(horizontal: false, vertical: true)
    }
}

private struct TransportStatusStrip: View {
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        HStack(spacing: 6) {
            TransportModeButton(
                title: "WiFi",
                systemImage: "wifi",
                enabled: store.lanModeEnabled,
                action: store.toggleLanMode
            )
            TransportModeButton(
                title: "BLE",
                systemImage: "dot.radiowaves.left.and.right",
                enabled: store.bluetoothModeEnabled,
                action: store.toggleBluetoothMode
            )
        }
    }
}

private struct TransportModeButton: View {
    @Environment(\.krakenPalette) private var palette
    let title: String
    let systemImage: String
    let enabled: Bool
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(buttonFill)
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
                if !enabled {
                    Rectangle()
                        .fill(Color.red.opacity(0.9))
                        .frame(width: 17, height: 2)
                        .rotationEffect(.degrees(-35))
                }
            }
            .foregroundStyle(enabled ? iconColor : Color.secondary)
            .frame(width: 28, height: 28)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .help(enabled ? "\(title) включён для Kraken" : "\(title) выключен для Kraken")
    }

    private var buttonFill: Color {
        if hovering { return palette.accent.opacity(enabled ? 0.24 : 0.12) }
        return enabled ? palette.accent.opacity(0.16) : Color.secondary.opacity(0.08)
    }

    private var iconColor: Color {
        title == "BLE" ? KrakenColors.activeGreen : palette.accent
    }
}

private struct SidebarPeerRow: View {
    @Environment(\.krakenPalette) private var palette
    let relationship: Relationship
    let route: PeerRouteSnapshot?
    let selected: Bool
    let lastMessage: LocalMessage?
    @State private var hovering = false

    var body: some View {
        HStack(spacing: 10) {
            PeerAvatar(name: relationship.peerDisplayName, fingerprint: relationship.peerFingerprint, size: 44)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(relationship.peerDisplayName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(selected ? .white : .primary)
                        .lineLimit(1)

                    Spacer()

                    Text(lastTimestamp)
                        .font(.caption)
                        .foregroundStyle(selected ? .white.opacity(0.76) : .secondary)
                }

                HStack(spacing: 6) {
                    Circle()
                        .fill(routeColor)
                        .frame(width: 7, height: 7)
                    Text(preview)
                        .font(.caption)
                        .foregroundStyle(selected ? .white.opacity(0.76) : .secondary)
                        .lineLimit(1)
                    Spacer()
                    if relationship.state != .active {
                        Image(systemName: "clock")
                            .font(.caption)
                            .foregroundStyle(selected ? .white.opacity(0.76) : .secondary)
                    }
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background {
            RoundedRectangle(cornerRadius: 10)
                .fill(rowFill)
        }
        .overlay {
            RoundedRectangle(cornerRadius: 10)
                .stroke(rowStroke, lineWidth: selected ? 1 : 0)
        }
        .contentShape(Rectangle())
        .onHover { hovering = $0 }
    }

    private var preview: String {
        if let lastMessage {
            return lastMessage.body
        }
        let routeTitle = route?.kind.title ?? "нет маршрута"
        return "\(relationship.state.title) · \(routeTitle)"
    }

    private var lastTimestamp: String {
        guard let lastMessage else { return "" }
        return lastMessage.updatedAt.formatted(date: .omitted, time: .shortened)
    }

    private var routeColor: Color {
        switch route?.kind {
        case .directLan: KrakenColors.activeGreen
        case .directBle, .routedMesh: .orange
        case .some(.none), nil: .secondary
        }
    }

    private var rowFill: AnyShapeStyle {
        if selected { return AnyShapeStyle(palette.selectionGradient) }
        if hovering { return AnyShapeStyle(Color.secondary.opacity(0.12)) }
        return AnyShapeStyle(Color.clear)
    }

    private var rowStroke: Color {
        selected ? palette.accent.opacity(0.45) : Color.clear
    }
}

private struct SidebarFooter: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore
    let onShowIdentityQr: () -> Void
    @State private var profileHovering = false
    @State private var settingsHovering = false

    var body: some View {
        HStack(spacing: 10) {
            Button(action: onShowIdentityQr) {
                HStack(spacing: 10) {
                    KrakenMark(size: 42)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(store.state.localIdentity?.displayName ?? "Создать личность")
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)

                        HStack(spacing: 5) {
                            Text(store.state.localIdentity.map { KrakenFormatters.compactFingerprint($0.fingerprint) } ?? "личность не создана")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                            Image(systemName: "qrcode")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(palette.accent)
                        }
                    }
                }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .padding(9)
            .background(profileHovering ? Color.secondary.opacity(0.12) : Color.clear, in: RoundedRectangle(cornerRadius: 10))
            .onHover { profileHovering = $0 }
            .help("Показать мой QR")

            Spacer()

            Button {
                store.selectedSection = .settings
                store.selectedSettingsPane = .identity
            } label: {
                Image(systemName: "gearshape")
                    .font(.system(size: 20, weight: .semibold))
                    .frame(width: 44, height: 44)
                    .background(settingsBackground, in: RoundedRectangle(cornerRadius: 10))
                    .contentShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .foregroundStyle(store.selectedSection == .settings ? .white : .primary)
            .onHover { settingsHovering = $0 }
            .help("Настройки")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(height: 76)
        .background(palette.panelBackground)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(palette.separator)
                .frame(height: 1)
        }
    }

    private var settingsBackground: AnyShapeStyle {
        if store.selectedSection == .settings { return AnyShapeStyle(palette.selectionGradient) }
        if settingsHovering { return AnyShapeStyle(Color.secondary.opacity(0.14)) }
        return AnyShapeStyle(Color.clear)
    }
}

private struct SectionSwitcher: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore
    @State private var hoveringSection: DesktopSection?

    var body: some View {
        HStack(spacing: 6) {
            ForEach(DesktopSection.primaryTabs) { section in
                Button {
                    store.selectedSection = section
                } label: {
                    VStack(spacing: 5) {
                        Image(systemName: section.systemImage)
                            .font(.system(size: 16, weight: .semibold))
                        Text(section.shortTitle)
                    .font(.caption2.weight(.semibold))
                    .lineLimit(1)
            }
                    .foregroundStyle(store.selectedSection == section ? .white : .primary)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(sectionFill(section))
                    }
                    .overlay {
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(sectionStroke(section), lineWidth: store.selectedSection == section ? 1 : 0)
                    }
                }
                .buttonStyle(.plain)
                .onHover { hoveringSection = $0 ? section : nil }
                .help(section.title)
            }
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 9)
    }

    private func sectionFill(_ section: DesktopSection) -> AnyShapeStyle {
        if store.selectedSection == section { return AnyShapeStyle(palette.selectionGradient) }
        if hoveringSection == section { return AnyShapeStyle(Color.secondary.opacity(0.14)) }
        return AnyShapeStyle(Color.clear)
    }

    private func sectionStroke(_ section: DesktopSection) -> Color {
        store.selectedSection == section ? palette.accent.opacity(0.50) : Color.clear
    }
}

private struct SidebarSectionSummary: View {
    @Environment(\.krakenPalette) private var palette
    let section: DesktopSection

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(section.title, systemImage: section.systemImage)
                .font(.headline)
            Text(summary)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.panelBackground, in: RoundedRectangle(cornerRadius: 8))
    }

    private var summary: String {
        switch section {
        case .chat:
            "Диалоги и сообщения."
        case .mesh:
            "Локальные реалмы и приглашения."
        case .research:
            "Исследовательский режим и границы прототипа."
        case .settings:
            "Темы, шрифты и технические параметры Kraken."
        }
    }
}

private struct RealmsSidebarList: View {
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        VStack(spacing: 8) {
            SidebarNavigationRow(
                title: "Обзор",
                subtitle: "Создание и заявки",
                systemImage: "rectangle.grid.1x2",
                selected: store.selectedRealmId == nil
            ) {
                store.selectedRealmId = nil
            }

            ForEach(activeRealms) { realm in
                SidebarNavigationRow(
                    title: realm.name,
                    subtitle: "\(realm.memberCount) · \(realm.state.title)",
                    systemImage: "person.3.sequence",
                    selected: store.selectedRealmId == realm.realmId
                ) {
                    store.selectedRealmId = realm.realmId
                }
            }
        }
    }

    private var activeRealms: [DesktopRealm] {
        store.desktopRealms.filter { $0.state == .active }
    }
}

private struct SettingsSidebarList: View {
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        VStack(spacing: 8) {
            ForEach(SettingsPane.allCases) { pane in
                SidebarNavigationRow(
                    title: pane.title,
                    subtitle: pane.subtitle,
                    systemImage: pane.systemImage,
                    selected: store.selectedSettingsPane == pane
                ) {
                    store.selectedSettingsPane = pane
                }
            }
        }
    }
}

private struct SidebarNavigationRow: View {
    @Environment(\.krakenPalette) private var palette
    let title: String
    let subtitle: String
    let systemImage: String
    let selected: Bool
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 30)
                    .foregroundStyle(selected ? palette.selectedText : palette.accent)

                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(selected ? palette.selectedText : .primary)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(selected ? palette.selectedText.opacity(0.76) : .secondary)
                        .lineLimit(1)
                }

                Spacer()
            }
            .padding(12)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(rowFill)
            }
            .contentShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
    }

    private var rowFill: AnyShapeStyle {
        if selected { return AnyShapeStyle(palette.selectionGradient) }
        if hovering { return AnyShapeStyle(Color.secondary.opacity(0.12)) }
        return AnyShapeStyle(Color.clear)
    }
}
