import KrakenDesktopCore
import SwiftUI

struct ContactsView: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HeaderBlock(
                    title: "Контакты",
                    subtitle: "Устройства Kraken, с которыми уже выполнено сопряжение."
                )

                if store.filteredRelationships.isEmpty {
                    SectionBlock(title: "Контакты") {
                        KrakenEmptyContactsState()
                    }
                } else {
                    VStack(spacing: 10) {
                        ForEach(store.filteredRelationships) { relationship in
                            ContactRow(
                                relationship: relationship,
                                route: route(for: relationship),
                                selected: store.selectedRelationshipId == relationship.relationshipId,
                                openAction: {
                                    store.selectRelationship(relationship.relationshipId)
                                    store.selectedSection = .chat
                                },
                                bindAction: {
                                    store.selectRelationship(relationship.relationshipId)
                                    store.bindCurrentLanEndpointToSelectedPeer()
                                },
                                deleteAction: {
                                    store.deleteRelationship(relationship.relationshipId)
                                }
                            )
                        }
                    }
                }
            }
            .padding(24)
            .frame(maxWidth: 900, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .background(palette.detailBackground)
    }

    private func route(for relationship: Relationship) -> PeerRouteSnapshot? {
        store.state.routes.first { $0.relationshipId == relationship.relationshipId }
    }
}

private struct KrakenEmptyContactsState: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Контактов пока нет", systemImage: "person.crop.circle.badge.plus")
                .font(.headline)
            Text("Добавьте устройство через QR, после чего оно появится здесь и в списке чатов.")
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ContactRow: View {
    @Environment(\.krakenPalette) private var palette
    let relationship: Relationship
    let route: PeerRouteSnapshot?
    let selected: Bool
    let openAction: () -> Void
    let bindAction: () -> Void
    let deleteAction: () -> Void
    @State private var hovering = false

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            PeerAvatar(
                name: relationship.peerDisplayName,
                fingerprint: relationship.peerFingerprint,
                size: 48
            )

            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 8) {
                    Text(relationship.peerDisplayName)
                        .font(.headline)
                        .lineLimit(1)

                    Circle()
                        .fill(routeColor)
                        .frame(width: 8, height: 8)

                    Spacer(minLength: 12)

                    Text(relationship.updatedAt.formatted(date: .abbreviated, time: .shortened))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 8) {
                    StatusPill(title: relationship.state.title, systemImage: stateIcon)
                    StatusPill(title: route?.kind.title ?? "нет маршрута", systemImage: routeIcon)
                }

                Text(KrakenFormatters.compactFingerprint(relationship.peerFingerprint))
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }

            VStack(alignment: .trailing, spacing: 8) {
                Button(action: openAction) {
                    Label("Чат", systemImage: "bubble.left.and.bubble.right")
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)

                Button(action: bindAction) {
                    Label("LAN/ADB", systemImage: "link")
                }
                .buttonStyle(.bordered)
                .controlSize(.small)

                Button(role: .destructive, action: deleteAction) {
                    Label("Удалить", systemImage: "trash")
                }
                .buttonStyle(.borderless)
                .controlSize(.small)
            }
        }
        .padding(14)
        .background(rowFill, in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(rowStroke, lineWidth: selected ? 1 : 0)
        }
        .contentShape(RoundedRectangle(cornerRadius: 8))
        .onHover { hovering = $0 }
    }

    private var stateIcon: String {
        switch relationship.state {
        case .active: "checkmark.circle"
        case .pendingImport, .pendingHandshake: "clock"
        case .unlinkRequested, .unlinked, .rejoinRequired: "arrow.clockwise"
        case .blockedByPeer: "hand.raised"
        }
    }

    private var routeIcon: String {
        switch route?.kind {
        case .directBle: "dot.radiowaves.left.and.right"
        case .directLan: "wifi"
        case .routedMesh: "point.3.connected.trianglepath.dotted"
        case .some(.none), nil: "slash.circle"
        }
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
        if hovering { return AnyShapeStyle(palette.elevatedPanel) }
        return AnyShapeStyle(palette.panelBackground)
    }

    private var rowStroke: Color {
        selected ? palette.accent.opacity(0.5) : Color.clear
    }
}
