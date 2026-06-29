import AppKit
import KrakenDesktopCore
import SwiftUI

struct ChatView: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore
    let onShowIdentityQr: () -> Void
    let onScanQr: () -> Void
    @State private var draft = ""
    @State private var quotedMessage: LocalMessage?
    @State private var selectedMessageIds = Set<String>()
    @State private var localReactions: [String: String] = [:]

    var body: some View {
        VStack(spacing: 0) {
            if let relationship = store.selectedRelationship {
                ChatHeader(
                    relationship: relationship,
                    route: store.selectedRoute
                )

                Divider()

                HStack(spacing: 0) {
                    MessageTimeline(
                        messages: store.selectedMessages,
                        relationship: relationship,
                        route: store.selectedRoute,
                        pairingStatus: pairingStatus,
                        quotedMessageId: quotedMessage?.messageId,
                        selectedMessageIds: selectedMessageIds,
                        localReactions: localReactions,
                        onShowIdentityQr: onShowIdentityQr,
                        onScanQr: onScanQr,
                        onRetry: store.retryMessage(_:),
                        onDelete: store.deleteMessage(_:),
                        onQuote: { quotedMessage = $0 },
                        onToggleSelected: toggleSelectedMessage(_:),
                        onReact: toggleReaction(_:reaction:)
                    )

                    if store.transportPanelVisible {
                        Divider()
                        TransportInspector()
                            .frame(width: 360)
                            .transition(.move(edge: .trailing).combined(with: .opacity))
                    }
                }

                Divider()

                ChatComposer(
                    draft: $draft,
                    quotedMessage: quotedMessage,
                    enabled: relationship.state.isMessageCapable,
                    onCancelQuote: {
                        quotedMessage = nil
                    },
                    onSend: {
                        store.sendMessage(body: outgoingBody)
                        draft = ""
                        quotedMessage = nil
                    }
                )
            } else {
                ChatEmptyState()
            }
        }
        .background(palette.detailBackground)
    }

    private var outgoingBody: String {
        let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let quotedMessage else { return trimmed }
        let firstLine = quotedMessage.body
            .split(separator: "\n", omittingEmptySubsequences: true)
            .first
            .map(String.init)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let prefix = firstLine?.isEmpty == false ? firstLine! : quotedMessage.body
        return "> \(prefix)\n\(trimmed)"
    }

    private func toggleSelectedMessage(_ message: LocalMessage) {
        if selectedMessageIds.contains(message.messageId) {
            selectedMessageIds.remove(message.messageId)
        } else {
            selectedMessageIds.insert(message.messageId)
        }
    }

    private var pairingStatus: PairingContinuationStatus {
        if store.pendingHandshakeConfirmation != nil {
            return .confirmationQrReady
        }
        if store.selectedMessages.contains(where: { $0.direction == .outgoing && $0.status == .failed }) {
            return .transportFailed
        }
        if store.bluetoothModeEnabled || store.lanModeEnabled {
            return .transportChecking
        }
        return .manualAvailable
    }

    private func toggleReaction(_ message: LocalMessage, reaction: String) {
        if localReactions[message.messageId] == reaction {
            localReactions.removeValue(forKey: message.messageId)
        } else {
            localReactions[message.messageId] = reaction
        }
    }
}

private struct ChatHeader: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore
    let relationship: Relationship
    let route: PeerRouteSnapshot?

    var body: some View {
        HStack(spacing: 12) {
            PeerAvatar(name: relationship.peerDisplayName, fingerprint: relationship.peerFingerprint, size: 42)

            VStack(alignment: .leading, spacing: 5) {
                Text(relationship.peerDisplayName)
                    .font(.headline.weight(.semibold))
                    .lineLimit(1)

                HStack(spacing: 8) {
                    Text(KrakenFormatters.compactFingerprint(relationship.peerFingerprint))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)

                    Text("•")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Text(relationship.state.title)
                        .font(.caption)
                        .foregroundStyle(relationship.state.isMessageCapable ? KrakenColors.activeGreen : .secondary)
                        .lineLimit(1)

                    Text("•")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Label(route?.kind.title ?? "нет маршрута", systemImage: routeIcon)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            Button {
                store.transportPanelVisible.toggle()
            } label: {
                Image(systemName: "network")
                    .font(.system(size: 15, weight: .semibold))
                    .frame(width: 34, height: 34)
                    .background(
                        store.transportPanelVisible ? palette.accent.opacity(0.20) : Color.secondary.opacity(0.10),
                        in: Circle()
                    )
            }
            .buttonStyle(.plain)
            .help("LAN/ADB мост")
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 10)
        .background(palette.panelBackground)
    }

    private var routeIcon: String {
        switch route?.kind {
        case .directBle: "dot.radiowaves.left.and.right"
        case .directLan: "wifi"
        case .routedMesh: "point.3.connected.trianglepath.dotted"
        case .some(.none), nil: "slash.circle"
        }
    }
}

private struct TransportInspector: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("LAN/ADB-мост")
                        .font(.headline)
                    Text("Не Wi-Fi Direct")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    store.transportPanelVisible = false
                } label: {
                    Image(systemName: "xmark")
                }
                .buttonStyle(.borderless)
            }

            Grid(alignment: .leading, horizontalSpacing: 8, verticalSpacing: 8) {
                GridRow {
                    Text("Адрес")
                        .foregroundStyle(.secondary)
                    TextField("127.0.0.1", text: $store.lanTargetHost)
                        .textFieldStyle(.roundedBorder)
                }
                GridRow {
                    Text("Порт")
                        .foregroundStyle(.secondary)
                    TextField("54035", text: $store.lanTargetPort)
                        .textFieldStyle(.roundedBorder)
                }
                GridRow {
                    Text("Отпечаток")
                        .foregroundStyle(.secondary)
                    TextField("отпечаток устройства", text: $store.lanTargetFingerprint)
                        .textFieldStyle(.roundedBorder)
                }
                GridRow {
                    Text("Имя")
                        .foregroundStyle(.secondary)
                    TextField("Xiaomi", text: $store.lanTargetDisplayName)
                        .textFieldStyle(.roundedBorder)
                }
                GridRow {
                    Text("Приём")
                        .foregroundStyle(.secondary)
                    TextField("43191", text: $store.lanListenPort)
                        .textFieldStyle(.roundedBorder)
                }
            }
            .font(.caption)

            TextField("Текст сообщения", text: $store.lanBody)
                .textFieldStyle(.roundedBorder)

            HStack {
                Button {
                    store.selectLanBridgeEndpointPeer()
                } label: {
                    Image(systemName: "person.crop.circle.badge.checkmark")
                }
                .help("Выбрать адрес LAN/ADB для устройства")

                Button {
                    store.startLanListener()
                } label: {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                }
                .help("Запустить приём LAN")
                .disabled(store.lanListenerRunning)

                Button {
                    store.stopLanListener()
                } label: {
                    Image(systemName: "stop.circle")
                }
                .help("Остановить приём")
                .disabled(!store.lanListenerRunning)

                Button {
                    store.sendLanFrameToTarget()
                } label: {
                    Image(systemName: "paperplane")
                }
                .help("Отправить LAN-кадр, совместимый с Android")

                Button {
                    store.saveLanEvidence()
                } label: {
                    Image(systemName: "externaldrive")
                }
                .help("Сохранить артефакт проверки")
            }

            StatusPill(
                title: store.lanListenerRunning ? "приём :\(store.lanListenerPort ?? 0)" : "приём остановлен",
                systemImage: store.lanListenerRunning ? "checkmark.circle" : "slash.circle"
            )

            Label(
                store.lanBridgeBindingTitle,
                systemImage: store.lanBridgeSelectedPeerMatchesEndpoint ? "checkmark.circle" : "exclamationmark.triangle"
            )
            .font(.caption)
            .foregroundStyle(store.lanBridgeSelectedPeerMatchesEndpoint ? KrakenColors.activeGreen : .orange)
            .fixedSize(horizontal: false, vertical: true)

            if !store.lanBridgeSelectedPeerMatchesEndpoint {
                Button {
                    store.bindCurrentLanEndpointToSelectedPeer()
                } label: {
                    Label("Привязать адрес к выбранному контакту", systemImage: "link")
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }

            if let path = store.lastEvidencePath {
                Text(path)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .textSelection(.enabled)
            }

            Divider()

            Text("События")
                .font(.subheadline.weight(.semibold))

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 8) {
                    ForEach(store.lanEvents.prefix(10)) { event in
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(event.direction.rawValue)
                                Text(event.status.rawValue)
                                Spacer()
                            }
                            .font(.caption.weight(.semibold))
                            Text(event.packetId ?? event.error ?? "-")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                        }
                        .padding(8)
                        .background(palette.elevatedPanel, in: RoundedRectangle(cornerRadius: 8))
                    }
                }
            }

            Spacer()
        }
        .padding(16)
        .background(palette.panelBackground)
    }
}

private enum PairingContinuationStatus {
    case confirmationQrReady
    case transportFailed
    case transportChecking
    case manualAvailable

    var title: String {
        switch self {
        case .confirmationQrReady: "QR подтверждения готов"
        case .transportFailed: "Маршрут не сработал"
        case .transportChecking: "Проверка маршрута"
        case .manualAvailable: "QR-сопряжение"
        }
    }

    var subtitle: String {
        switch self {
        case .confirmationQrReady:
            "Покажите QR подтверждения второму устройству или отсканируйте его ответ."
        case .transportFailed:
            "Сообщение не доставлено. Продолжите сопряжение вручную через QR."
        case .transportChecking:
            "Если Bluetooth или LAN не найдёт устройство, продолжите через QR."
        case .manualAvailable:
            "Покажите свой QR или отсканируйте QR второго устройства."
        }
    }

    var icon: String {
        switch self {
        case .confirmationQrReady: "qrcode"
        case .transportFailed: "exclamationmark.triangle"
        case .transportChecking: "antenna.radiowaves.left.and.right"
        case .manualAvailable: "qrcode.viewfinder"
        }
    }
}

private struct PairingContinuationCard: View {
    @Environment(\.krakenPalette) private var palette
    let relationship: Relationship
    let route: PeerRouteSnapshot?
    let status: PairingContinuationStatus
    let onShowIdentityQr: () -> Void
    let onScanQr: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: status.icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(status == .transportFailed ? Color.orange : palette.accent)
                .frame(width: 34, height: 34)
                .background(palette.elevatedPanel, in: Circle())

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(status.title)
                        .font(.subheadline.weight(.semibold))
                    Text(route?.kind.title ?? "нет маршрута")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(palette.elevatedPanel, in: Capsule())
                }

                Text(status.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }

            Spacer(minLength: 8)

            HStack(spacing: 8) {
                Button {
                    onScanQr()
                } label: {
                    Label("Сканировать QR", systemImage: "qrcode.viewfinder")
                }
                .buttonStyle(.bordered)

                Button {
                    onShowIdentityQr()
                } label: {
                    Label("Мой QR", systemImage: "qrcode")
                }
                .buttonStyle(.borderedProminent)
            }
            .controlSize(.small)
        }
        .padding(12)
        .background(palette.panelBackground, in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke((status == .transportFailed ? Color.orange : palette.accent).opacity(0.22), lineWidth: 1)
        }
        .frame(maxWidth: 720)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(relationship.peerDisplayName). \(status.title). \(status.subtitle)")
    }
}

private struct MessageTimeline: View {
    @Environment(\.krakenPalette) private var palette
    let messages: [LocalMessage]
    let relationship: Relationship
    let route: PeerRouteSnapshot?
    let pairingStatus: PairingContinuationStatus
    let quotedMessageId: String?
    let selectedMessageIds: Set<String>
    let localReactions: [String: String]
    let onShowIdentityQr: () -> Void
    let onScanQr: () -> Void
    let onRetry: (LocalMessage) -> Void
    let onDelete: (String) -> Void
    let onQuote: (LocalMessage) -> Void
    let onToggleSelected: (LocalMessage) -> Void
    let onReact: (LocalMessage, String) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                Text("Сегодня")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 6)

                PairingContinuationCard(
                    relationship: relationship,
                    route: route,
                    status: pairingStatus,
                    onShowIdentityQr: onShowIdentityQr,
                    onScanQr: onScanQr
                )

                if messages.isEmpty {
                    VStack(spacing: 12) {
                        PeerAvatar(name: relationship.peerDisplayName, fingerprint: relationship.peerFingerprint, size: 58)
                        Text("История пуста")
                            .font(.headline)
                        Text("Отправьте сообщение или включите приём LAN, чтобы получить кадр с Android.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: 360)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 80)
                } else {
                    ForEach(messages) { message in
                        MessageBubble(
                            message: message,
                            selected: selectedMessageIds.contains(message.messageId),
                            quoted: quotedMessageId == message.messageId,
                            localReaction: localReactions[message.messageId],
                            onRetry: onRetry,
                            onDelete: onDelete,
                            onQuote: onQuote,
                            onToggleSelected: onToggleSelected,
                            onReact: onReact
                        )
                    }
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 18)
            .frame(maxWidth: .infinity)
        }
        .background(palette.messageBackground)
    }
}

private struct ChatComposer: View {
    @Environment(\.krakenPalette) private var palette
    @Binding var draft: String
    let quotedMessage: LocalMessage?
    let enabled: Bool
    let onCancelQuote: () -> Void
    let onSend: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            if let quotedMessage {
                HStack(spacing: 10) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(palette.accent)
                        .frame(width: 3)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Ответ")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(palette.accent)
                        Text(quotedMessage.body)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    Button(action: onCancelQuote) {
                        Image(systemName: "xmark")
                            .frame(width: 24, height: 24)
                    }
                    .buttonStyle(.plain)
                    .background(Color.secondary.opacity(0.12), in: Circle())
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(palette.elevatedPanel, in: RoundedRectangle(cornerRadius: 8))
            }

            HStack(alignment: .center, spacing: 10) {
                TextField("Сообщение", text: $draft, axis: .vertical)
                    .lineLimit(1...3)
                    .textFieldStyle(.plain)
                    .submitLabel(.send)
                    .onSubmit(sendIfPossible)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(minHeight: 36)
                    .background(palette.inputBackground, in: RoundedRectangle(cornerRadius: 8))
                    .overlay {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(palette.separator, lineWidth: 1)
                    }

                Button(action: sendIfPossible) {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
                .background(sendDisabled ? Color.secondary.opacity(0.18) : palette.accent, in: Circle())
                .foregroundStyle(sendDisabled ? Color.secondary : Color.white)
                .disabled(sendDisabled)
                .help(enabled ? "Отправить" : "Связь с устройством не активна")
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(palette.panelBackground)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(palette.separator)
                .frame(height: 1)
        }
        .opacity(enabled ? 1 : 0.62)
    }

    private var sendDisabled: Bool {
        draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !enabled
    }

    private func sendIfPossible() {
        guard !sendDisabled else { return }
        onSend()
    }
}

private struct MessageBubble: View {
    @Environment(\.krakenPalette) private var palette
    let message: LocalMessage
    let selected: Bool
    let quoted: Bool
    let localReaction: String?
    let onRetry: (LocalMessage) -> Void
    let onDelete: (String) -> Void
    let onQuote: (LocalMessage) -> Void
    let onToggleSelected: (LocalMessage) -> Void
    let onReact: (LocalMessage, String) -> Void

    private var outgoing: Bool { message.direction == .outgoing }
    private var retryAvailable: Bool {
        outgoing && (message.status == .failed || message.status == .readyForTransport)
    }

    var body: some View {
        HStack(alignment: .bottom) {
            if outgoing { Spacer(minLength: 80) }

            VStack(alignment: .leading, spacing: 5) {
                if quoted {
                    Text("Выбрано для ответа")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(palette.accent)
                }

                Text(message.body)
                    .font(.body)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 5) {
                    Text(message.status.title)
                    Image(systemName: statusIcon)
                }
                .font(.caption2)
                .foregroundStyle(.secondary)

                if let localReaction {
                    Text(localReaction)
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(palette.panelBackground, in: Capsule())
                }

                if retryAvailable {
                    Button {
                        onRetry(message)
                    } label: {
                        Label("Повторить", systemImage: "arrow.clockwise")
                            .font(.caption.weight(.semibold))
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(palette.accent)
                    .padding(.top, 2)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(outgoing ? palette.accentDeep.opacity(0.76) : palette.elevatedPanel)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(selected ? palette.accent : Color.clear, lineWidth: 2)
            }
            .frame(maxWidth: 520, alignment: outgoing ? .trailing : .leading)
            .contextMenu {
                Button {
                    onReact(message, "👍")
                } label: {
                    Label(localReaction == "👍" ? "Убрать реакцию 👍" : "Быстрая реакция 👍", systemImage: "hand.thumbsup")
                }

                Button {
                    onQuote(message)
                } label: {
                    Label("Ответить", systemImage: "quote.bubble")
                }

                Button {
                    onToggleSelected(message)
                } label: {
                    Label(selected ? "Снять выделение" : "Выделить", systemImage: selected ? "checkmark.circle.fill" : "checkmark.circle")
                }

                Button {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(message.body, forType: .string)
                } label: {
                    Label("Скопировать", systemImage: "doc.on.doc")
                }

                if retryAvailable {
                    Button {
                        onRetry(message)
                    } label: {
                        Label("Повторить отправку", systemImage: "arrow.clockwise")
                    }
                }

                Divider()

                Button(role: .destructive) {
                    onDelete(message.messageId)
                } label: {
                    Label("Удалить сообщение", systemImage: "trash")
                }
            }

            if !outgoing { Spacer(minLength: 80) }
        }
    }

    private var statusIcon: String {
        switch message.status {
        case .localPending: "clock"
        case .readyForTransport: "tray.and.arrow.up"
        case .sentToTransport: "paperplane"
        case .deliveredToPeer: "checkmark.circle"
        case .failed: "exclamationmark.triangle"
        }
    }
}

private struct ChatEmptyState: View {
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "bubble.left.and.bubble.right")
                .font(.system(size: 44, weight: .medium))
                .foregroundStyle(.secondary)
            Text("Выберите диалог")
                .font(.title2.weight(.semibold))
            Text("Выберите чат слева или добавьте устройство через QR.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 420)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct PeerAvatar: View {
    @Environment(\.krakenPalette) private var palette
    let name: String
    let fingerprint: String
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [palette.accent.opacity(0.78), KrakenColors.activeGreen.opacity(0.62)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Text(initials)
                .font(.system(size: max(12, size * 0.34), weight: .bold, design: .rounded))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
        .overlay(alignment: .bottomTrailing) {
            Circle()
                .fill(fingerprint.isEmpty ? Color.gray : KrakenColors.activeGreen)
                .frame(width: max(8, size * 0.2), height: max(8, size * 0.2))
                .overlay(Circle().stroke(palette.windowBackground, lineWidth: 2))
        }
    }

    private var initials: String {
        let parts = name.split(separator: " ")
        let value = parts.prefix(2).compactMap { $0.first }.map(String.init).joined()
        return value.isEmpty ? "K" : value.uppercased()
    }
}

private struct RoutePill: View {
    let route: PeerRouteSnapshot?

    var body: some View {
        StatusPill(
            title: route?.kind.title ?? "нет маршрута",
            systemImage: routeIcon
        )
    }

    private var routeIcon: String {
        switch route?.kind {
        case .directBle: "dot.radiowaves.left.and.right"
        case .directLan: "wifi"
        case .routedMesh: "point.3.connected.trianglepath.dotted"
        case .some(.none), nil: "slash.circle"
        }
    }
}

struct StatusPill: View {
    @Environment(\.krakenPalette) private var palette
    let title: String
    let systemImage: String

    var body: some View {
        Label(title, systemImage: systemImage)
            .font(.caption.weight(.medium))
            .labelStyle(.titleAndIcon)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(palette.elevatedPanel, in: Capsule())
            .foregroundStyle(.secondary)
    }
}
