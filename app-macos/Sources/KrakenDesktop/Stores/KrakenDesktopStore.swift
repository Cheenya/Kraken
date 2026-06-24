import Foundation
import KrakenDesktopCore
import SwiftUI
import AppKit

@MainActor
final class KrakenDesktopStore: ObservableObject {
    @Published private(set) var state: KrakenDesktopState
    @Published var selectedRelationshipId: String?
    @Published var selectedSection: DesktopSection = .chat
    @Published var selectedSettingsPane: SettingsPane = .identity
    @Published var selectedRealmId: String?
    @Published var selectedTheme: KrakenThemePreset {
        didSet {
            UserDefaults.standard.set(selectedTheme.rawValue, forKey: Self.themeDefaultsKey)
        }
    }
    @Published var customAccentEnabled: Bool {
        didSet {
            UserDefaults.standard.set(customAccentEnabled, forKey: Self.customAccentEnabledDefaultsKey)
        }
    }
    @Published var customAccentHex: String {
        didSet {
            UserDefaults.standard.set(customAccentHex, forKey: Self.customAccentDefaultsKey)
        }
    }
    @Published var startGateVisible = false
    @Published var searchText = ""
    @Published var transportPanelVisible = false
    @Published var pendingHandshakeConfirmation: HandshakeConfirmationSnapshot?
    @Published private(set) var latestProbeResult: DesktopProbeResult?
    @Published private(set) var probeRunning = false
    @Published var lanTargetHost = "127.0.0.1"
    @Published var lanTargetPort = "54035"
    @Published var lanTargetFingerprint = "B42B3068934EF618"
    @Published var lanTargetDisplayName = "Xiaomi"
    @Published var lanLocalFingerprint = "MACOSDESKTOP0001"
    @Published var lanLocalPeerId = "macos-desktop"
    @Published var lanListenPort = "43191"
    @Published var lanBody = "Привет с Mac"
    @Published private(set) var lanModeEnabled = true
    @Published private(set) var bluetoothModeEnabled = true
    @Published private(set) var lanListenerRunning = false
    @Published private(set) var lanListenerPort: Int?
    @Published private(set) var lanEvents: [MacLanTransferEvent] = []
    @Published private(set) var lastEvidencePath: String?
    @Published private(set) var bleRunning = false
    @Published private(set) var bleStatus = MacBleTransportStatus()
    @Published private(set) var bleEvents: [MacBleTransferEvent] = []
    @Published private(set) var desktopRealms: [DesktopRealm] = [
        DesktopRealm(
            realmId: "realm-personal",
            name: "Личный круг",
            state: .active,
            memberCount: 1,
            pendingRequests: 0,
            updatedAt: Date()
        ),
    ]

    private let simulator: KrakenDesktopSimulator
    private let probeService: DesktopProbeService
    private let evidenceWriter: DesktopEvidenceWriter
    private let lanListener = MacLanTcpListener()
    private let lanSender = MacLanTcpSender()
    private let bleTransport = MacBleTransport()
    private var lanEndpointBindings: [String: LanEndpointBinding]
    private var outboxRetryRecords: [String: OutboxRetryRecord]
    private var outboxFlushInProgress = false
    private var outboxFlushTask: Task<Void, Never>?
    private static let localIdentityDefaultsKey = "kraken.desktop.localIdentity.v1"
    private static let relationshipsDefaultsKey = "kraken.desktop.relationships.v1"
    private static let messagesDefaultsKey = "kraken.desktop.messages.v1"
    private static let outboxRetryDefaultsKey = "kraken.desktop.outboxRetry.v1"
    private static let lanEndpointBindingsDefaultsKey = "kraken.desktop.lanEndpointBindings.v1"
    private static let themeDefaultsKey = "kraken.desktop.theme.v1"
    private static let customAccentDefaultsKey = "kraken.desktop.customAccent.v1"
    private static let customAccentEnabledDefaultsKey = "kraken.desktop.customAccentEnabled.v1"
    private static let messageTransportTtl: TimeInterval = 5 * 60

    init(
        simulator: KrakenDesktopSimulator = KrakenDesktopSimulator(),
        probeService: DesktopProbeService = DesktopProbeService(),
        evidenceWriter: DesktopEvidenceWriter = DesktopEvidenceWriter()
    ) {
        self.simulator = simulator
        self.probeService = probeService
        self.evidenceWriter = evidenceWriter
        self.selectedTheme = Self.loadTheme()
        self.customAccentEnabled = UserDefaults.standard.bool(forKey: Self.customAccentEnabledDefaultsKey)
        self.customAccentHex = UserDefaults.standard.string(forKey: Self.customAccentDefaultsKey) ?? "#12C8DC"
        self.lanEndpointBindings = Self.loadLanEndpointBindings()
        self.outboxRetryRecords = Self.loadOutboxRetryRecords()
        var initialState = simulator.makeInitialState()
        if var savedIdentity = Self.loadLocalIdentity() {
            if savedIdentity.displayName == "Desktop Kraken" {
                savedIdentity.displayName = "Kraken Desktop"
                Self.saveLocalIdentity(savedIdentity)
            }
            initialState.localIdentity = savedIdentity
            lanLocalFingerprint = savedIdentity.fingerprint
            lanLocalPeerId = savedIdentity.identityId
            if ProcessInfo.processInfo.environment["KRAKEN_DESKTOP_OPEN_QR"] == "1" {
                selectedSection = .settings
                selectedSettingsPane = .identity
            } else {
                startGateVisible = true
            }
        } else if ProcessInfo.processInfo.environment["KRAKEN_DESKTOP_DEMO_IDENTITY"] == "1" {
            initialState = simulator.createIdentity(in: initialState, displayName: "Kraken Desktop")
            selectedSection = .settings
            selectedSettingsPane = .identity
        }
        if let savedRelationships = Self.loadRelationships(), !savedRelationships.isEmpty {
            let savedIds = Set(savedRelationships.map(\.relationshipId))
            initialState.relationships = savedRelationships
            initialState.routes = savedRelationships.map { relationship in
                PeerRouteSnapshot(
                    relationshipId: relationship.relationshipId,
                    peerFingerprint: relationship.peerFingerprint,
                    kind: relationship.state == .active ? .directLan : .none,
                    transportId: relationship.state == .active ? "macos-lan-adb-bridge" : nil,
                    bandwidthClass: relationship.state == .active ? .high : .none,
                    hopCount: relationship.state == .active ? 1 : nil,
                    lastSeenAt: relationship.state == .active ? Date() : nil
                )
            }
            if let savedMessages = Self.loadMessages() {
                initialState.messages = savedMessages.filter { savedIds.contains($0.relationshipId) }
            } else {
                initialState.messages = initialState.messages.filter { savedIds.contains($0.relationshipId) }
            }
        }
        self.state = initialState
        self.selectedRelationshipId = initialState.relationships.first?.relationshipId
        applyLanEndpointBindingForSelectedPeer()
        expireReadyOutbox()
    }

    var activeRelationships: [Relationship] {
        state.relationships.filter { $0.state == .active }
    }

    var selectedRelationship: Relationship? {
        state.relationships.first { $0.relationshipId == selectedRelationshipId }
    }

    var selectedRoute: PeerRouteSnapshot? {
        guard let selectedRelationshipId else { return nil }
        return state.routes.first { $0.relationshipId == selectedRelationshipId }
    }

    var selectedMessages: [LocalMessage] {
        guard let selectedRelationshipId else { return [] }
        return state.messages.filter { $0.relationshipId == selectedRelationshipId }
    }

    var filteredRelationships: [Relationship] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !query.isEmpty else { return state.relationships }
        return state.relationships.filter {
            $0.peerDisplayName.lowercased().contains(query) ||
                $0.peerFingerprint.lowercased().contains(query) ||
                $0.state.title.lowercased().contains(query)
        }
    }

    var effectivePalette: KrakenThemePalette {
        selectedTheme.palette.applyingCustomAccent(customAccentEnabled ? Color.krakenHex(customAccentHex) : nil)
    }

    var lanBridgeSelectedPeerMatchesEndpoint: Bool {
        guard let relationship = selectedRelationship else { return false }
        return Self.normalizedFingerprint(relationship.peerFingerprint) ==
            Self.normalizedFingerprint(lanTargetFingerprint)
    }

    var lanBridgeBindingTitle: String {
        guard let relationship = selectedRelationship else {
            return "LAN/ADB узел не выбран"
        }
        return lanBridgeSelectedPeerMatchesEndpoint
            ? "LAN/ADB привязан к \(relationship.peerDisplayName)"
            : "Отпечаток конечной точки не совпадает с выбранным узлом"
    }

    func selectRelationship(_ relationshipId: String) {
        selectedRelationshipId = relationshipId
        selectedSection = .chat
        applyLanEndpointBindingForSelectedPeer()
    }

    func deleteRelationship(_ relationshipId: String) {
        guard let relationship = state.relationships.first(where: { $0.relationshipId == relationshipId }) else { return }
        let removedMessageIds = Set(state.messages.filter { $0.relationshipId == relationshipId }.map(\.messageId))
        state.relationships.removeAll { $0.relationshipId == relationshipId }
        state.routes.removeAll { $0.relationshipId == relationshipId }
        state.messages.removeAll { $0.relationshipId == relationshipId }
        outboxRetryRecords = outboxRetryRecords.filter { messageId, _ in !removedMessageIds.contains(messageId) }
        lanEndpointBindings.removeValue(forKey: Self.normalizedFingerprint(relationship.peerFingerprint))
        Self.saveLanEndpointBindings(lanEndpointBindings)
        if selectedRelationshipId == relationshipId {
            selectedRelationshipId = state.relationships.first?.relationshipId
            applyLanEndpointBindingForSelectedPeer()
        }
        Self.saveRelationships(state.relationships)
        Self.saveMessages(state.messages)
        Self.saveOutboxRetryRecords(outboxRetryRecords)
        state.lastEvent = "Контакт удалён: \(relationship.peerDisplayName)"
    }

    func deleteMessage(_ messageId: String) {
        state.messages.removeAll { $0.messageId == messageId }
        outboxRetryRecords.removeValue(forKey: messageId)
        Self.saveMessages(state.messages)
        Self.saveOutboxRetryRecords(outboxRetryRecords)
        state.lastEvent = "Сообщение удалено"
    }

    func retryMessage(_ message: LocalMessage) {
        guard message.direction == .outgoing else { return }
        guard message.status == .failed || message.status == .readyForTransport else { return }
        guard let relationship = state.relationships.first(where: { $0.relationshipId == message.relationshipId }) else {
            state.lastEvent = "Повтор невозможен: контакт не найден"
            return
        }
        sendMessage(body: message.body, relationship: relationship, replacingFailedMessageId: message.messageId)
    }

    func bindCurrentLanEndpointToSelectedPeer() {
        guard let relationship = selectedRelationship else {
            state.lastEvent = "LAN/ADB не привязан: контакт не выбран"
            return
        }
        lanTargetFingerprint = relationship.peerFingerprint
        lanTargetDisplayName = relationship.peerDisplayName
        rememberCurrentLanEndpoint(for: relationship)
        upsertLanBridgeRoute(
            relationshipId: relationship.relationshipId,
            peerFingerprint: relationship.peerFingerprint,
            now: Date()
        )
        state.lastEvent = "LAN/ADB конечная точка привязана к \(relationship.peerDisplayName)"
    }

    func createIdentity(displayName: String) {
        state = simulator.createIdentity(in: state, displayName: displayName)
        if let identity = state.localIdentity {
            Self.saveLocalIdentity(identity)
            lanLocalFingerprint = identity.fingerprint
            lanLocalPeerId = identity.identityId
        }
        startGateVisible = false
        selectedSection = .settings
        selectedSettingsPane = .identity
    }

    func openKraken() {
        startGateVisible = false
        selectedSection = .chat
    }

    func showQr() {
        startGateVisible = false
        selectedSection = .settings
        selectedSettingsPane = .identity
    }

    func importPeer(name: String) {
        state = simulator.importPeer(in: state, name: name)
        if let relationshipId = state.relationships.first?.relationshipId {
            selectRelationship(relationshipId)
        }
        Self.saveRelationships(state.relationships)
    }

    func activateSelectedRelationship() {
        guard let selectedRelationshipId else { return }
        state = simulator.activateRelationship(in: state, relationshipId: selectedRelationshipId)
        Self.saveRelationships(state.relationships)
    }

    @discardableResult
    func importScannedInvite(_ rawPayload: String) -> String? {
        guard let identity = state.localIdentity else {
            return "Сначала создайте личность Kraken на этом Mac."
        }
        let trimmedPayload = KrakenHandshakeQrCodec.normalizedScannedPayload(rawPayload)
        guard let data = trimmedPayload.data(using: .utf8) else {
            return "QR не содержит текстовый JSON."
        }
        let decoder = JSONDecoder()
        switch KrakenHandshakeQrCodec.detectKind(trimmedPayload) {
        case .invite:
            return importInvitePayload(data, identity: identity, decoder: decoder)
        case .response:
            do {
                return importHandshakeResponsePayload(
                    try KrakenHandshakeQrCodec.decodeResponse(trimmedPayload),
                    identity: identity
                )
            } catch {
                return "Ответный QR Kraken считан, но его данные неполные: \(KrakenHandshakeQrCodec.decodeFailureDescription(error)). \(KrakenHandshakeQrCodec.payloadSummary(trimmedPayload))"
            }
        case .confirmation:
            do {
                return importHandshakeConfirmationPayload(
                    try KrakenHandshakeQrCodec.decodeConfirmation(trimmedPayload),
                    identity: identity
                )
            } catch {
                return "Финальный QR Kraken считан, но его данные неполные: \(KrakenHandshakeQrCodec.decodeFailureDescription(error)). \(KrakenHandshakeQrCodec.payloadSummary(trimmedPayload))"
            }
        case .unknown:
            return "QR считан, но этот тип полезной нагрузки пока не поддержан в Kraken Desktop. \(KrakenHandshakeQrCodec.payloadSummary(trimmedPayload))"
        case .invalid:
            return "Этот QR не является JSON-полезной нагрузкой Kraken. \(KrakenHandshakeQrCodec.payloadSummary(trimmedPayload))"
        }
    }

    private func importInvitePayload(
        _ data: Data,
        identity: LocalIdentity,
        decoder: JSONDecoder
    ) -> String? {
        let payload: DesktopInvitePayload
        do {
            payload = try decoder.decode(DesktopInvitePayload.self, from: data)
        } catch {
            return "QR-приглашение неполное: \(KrakenHandshakeQrCodec.decodeFailureDescription(error)). Если вы сканируете ответ Samsung, откройте на Samsung именно «Показать ответный QR» в карточке сопряжения, а не «Мой QR»."
        }
        guard payload.version == 1 else {
            return "Версия QR-приглашения не поддерживается."
        }
        guard !payload.inviteId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В QR нет invite_id."
        }
        guard !payload.inviterFingerprint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В QR нет отпечатка устройства."
        }
        guard !payload.inviterPublicKeyEncoded.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В QR нет публичного ключа устройства."
        }
        if Self.normalizedFingerprint(payload.inviterFingerprint) == Self.normalizedFingerprint(identity.fingerprint) ||
            payload.inviterPublicKeyEncoded == identity.publicKeyEncoded {
            return "Нельзя импортировать собственный QR."
        }
        if let expiresAt = payload.expiresAtEpochMillis {
            let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
            guard expiresAt > nowMillis else {
                return "QR-приглашение истекло. Обновите QR на втором устройстве."
            }
        }

        let now = Date()
        let relationshipId = Self.offlineHandshakeRelationshipId(
            inviteId: payload.inviteId,
            inviterFingerprint: payload.inviterFingerprint,
            responderFingerprint: identity.fingerprint
        )
        let displayName = payload.inviterDisplayName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .ifBlank("Устройство Kraken")
        let cryptoProfileId = payload.cryptoProfileId?.ifBlank("standard-reviewed-primitives-v1") ?? "standard-reviewed-primitives-v1"
        let admissionDecisionHash = payload.admissionDecisionHash?.ifBlank("sha256:standard-reviewed-primitives-v1:not-applicable:v1")
            ?? "sha256:standard-reviewed-primitives-v1:not-applicable:v1"
        let importedRelationship = Relationship(
            relationshipId: relationshipId,
            peerDisplayName: displayName,
            peerFingerprint: payload.inviterFingerprint,
            state: .active,
            cryptoProfileId: cryptoProfileId,
            admissionDecisionHash: admissionDecisionHash,
            profilePolicyVersion: payload.profilePolicyVersion ?? 1,
            updatedAt: now
        )

        if let index = state.relationships.firstIndex(where: {
            $0.relationshipId == relationshipId ||
                Self.normalizedFingerprint($0.peerFingerprint) == Self.normalizedFingerprint(payload.inviterFingerprint)
        }) {
            state.relationships[index] = importedRelationship
        } else {
            state.relationships.insert(importedRelationship, at: 0)
        }

        lanTargetFingerprint = payload.inviterFingerprint
        lanTargetDisplayName = displayName
        rememberCurrentLanEndpoint(for: importedRelationship)
        upsertLanBridgeRoute(
            relationshipId: relationshipId,
            peerFingerprint: payload.inviterFingerprint,
            now: now
        )
        selectRelationship(relationshipId)
        selectedSection = .chat
        Self.saveRelationships(state.relationships)
        state.lastEvent = "QR импортирован: \(displayName)"
        return nil
    }

    private func importHandshakeResponsePayload(
        _ payload: KrakenHandshakeResponsePayload,
        identity: LocalIdentity
    ) -> String? {
        guard payload.version == 1 else {
            return "Версия ответного QR не поддерживается."
        }
        guard !payload.responseId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В ответном QR нет response_id."
        }
        guard !payload.inviteId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В ответном QR нет invite_id."
        }
        guard Self.normalizedFingerprint(payload.inviterFingerprint) == Self.normalizedFingerprint(identity.fingerprint) else {
            return "Этот ответный QR адресован другой личности Kraken."
        }
        guard !payload.responderFingerprint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В ответном QR нет отпечатка второго устройства."
        }
        guard !payload.responderPublicKeyEncoded.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В ответном QR нет публичного ключа второго устройства."
        }
        if Self.normalizedFingerprint(payload.responderFingerprint) == Self.normalizedFingerprint(identity.fingerprint) ||
            payload.responderPublicKeyEncoded == identity.publicKeyEncoded {
            return "Нельзя принять собственный ответный QR."
        }

        let now = Date()
        let relationshipId = Self.offlineHandshakeRelationshipId(
            inviteId: payload.inviteId,
            inviterFingerprint: identity.fingerprint,
            responderFingerprint: payload.responderFingerprint
        )
        let displayName = payload.responderDisplayName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .ifBlank("Устройство Kraken")
        let cryptoProfileId = payload.cryptoProfileId?.ifBlank("standard-reviewed-primitives-v1") ?? "standard-reviewed-primitives-v1"
        let admissionDecisionHash = payload.admissionDecisionHash?.ifBlank("sha256:standard-reviewed-primitives-v1:not-applicable:v1")
            ?? "sha256:standard-reviewed-primitives-v1:not-applicable:v1"
        let importedRelationship = Relationship(
            relationshipId: relationshipId,
            peerDisplayName: displayName,
            peerFingerprint: payload.responderFingerprint,
            state: .active,
            cryptoProfileId: cryptoProfileId,
            admissionDecisionHash: admissionDecisionHash,
            profilePolicyVersion: payload.profilePolicyVersion ?? 1,
            updatedAt: now
        )

        if let index = state.relationships.firstIndex(where: {
            $0.relationshipId == relationshipId ||
                Self.normalizedFingerprint($0.peerFingerprint) == Self.normalizedFingerprint(payload.responderFingerprint)
        }) {
            state.relationships[index] = importedRelationship
        } else {
            state.relationships.insert(importedRelationship, at: 0)
        }

        lanTargetFingerprint = payload.responderFingerprint
        lanTargetDisplayName = displayName
        rememberCurrentLanEndpoint(for: importedRelationship)
        upsertLanBridgeRoute(
            relationshipId: relationshipId,
            peerFingerprint: payload.responderFingerprint,
            now: now
        )
        selectRelationship(relationshipId)
        selectedSection = .chat
        Self.saveRelationships(state.relationships)
        pendingHandshakeConfirmation = Self.makeConfirmationSnapshot(
            for: payload,
            identity: identity,
            peerDisplayName: displayName,
            createdAt: now
        )
        state.lastEvent = "Ответный QR принят: \(displayName)"
        return nil
    }

    private func importHandshakeConfirmationPayload(
        _ payload: KrakenHandshakeConfirmationPayload,
        identity: LocalIdentity
    ) -> String? {
        guard payload.version == 1 else {
            return "Версия финального QR не поддерживается."
        }
        guard !payload.confirmationId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В финальном QR нет confirmation_id."
        }
        guard !payload.responseId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В финальном QR нет response_id."
        }
        guard !payload.inviteId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В финальном QR нет invite_id."
        }
        guard !payload.inviterFingerprint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В финальном QR нет отпечатка владельца приглашения."
        }
        guard !payload.responderFingerprint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "В финальном QR нет отпечатка второго устройства."
        }
        guard Self.normalizedFingerprint(payload.responderFingerprint) == Self.normalizedFingerprint(identity.fingerprint) else {
            return "Этот финальный QR адресован другой личности Kraken."
        }
        guard Self.normalizedFingerprint(payload.inviterFingerprint) != Self.normalizedFingerprint(identity.fingerprint) else {
            return "Этот финальный QR предназначен второму устройству, а не владельцу приглашения."
        }

        let now = Date()
        let relationshipId = Self.offlineHandshakeRelationshipId(
            inviteId: payload.inviteId,
            inviterFingerprint: payload.inviterFingerprint,
            responderFingerprint: identity.fingerprint
        )
        let existing = state.relationships.first {
            $0.relationshipId == relationshipId ||
                Self.normalizedFingerprint($0.peerFingerprint) == Self.normalizedFingerprint(payload.inviterFingerprint)
        }
        let displayName = existing?.peerDisplayName.ifBlank(payload.realmName ?? "Устройство Kraken")
            ?? payload.realmName?.ifBlank("Устройство Kraken")
            ?? "Устройство Kraken"
        let cryptoProfileId = payload.cryptoProfileId?.ifBlank("standard-reviewed-primitives-v1") ?? "standard-reviewed-primitives-v1"
        let admissionDecisionHash = payload.admissionDecisionHash?.ifBlank("sha256:standard-reviewed-primitives-v1:not-applicable:v1")
            ?? "sha256:standard-reviewed-primitives-v1:not-applicable:v1"
        let importedRelationship = Relationship(
            relationshipId: relationshipId,
            peerDisplayName: displayName,
            peerFingerprint: payload.inviterFingerprint,
            state: .active,
            cryptoProfileId: cryptoProfileId,
            admissionDecisionHash: admissionDecisionHash,
            profilePolicyVersion: payload.profilePolicyVersion ?? 1,
            updatedAt: now
        )

        if let index = state.relationships.firstIndex(where: {
            $0.relationshipId == relationshipId ||
                Self.normalizedFingerprint($0.peerFingerprint) == Self.normalizedFingerprint(payload.inviterFingerprint)
        }) {
            state.relationships[index] = importedRelationship
        } else {
            state.relationships.insert(importedRelationship, at: 0)
        }

        lanTargetFingerprint = payload.inviterFingerprint
        lanTargetDisplayName = displayName
        rememberCurrentLanEndpoint(for: importedRelationship)
        upsertLanBridgeRoute(
            relationshipId: relationshipId,
            peerFingerprint: payload.inviterFingerprint,
            now: now
        )
        selectRelationship(relationshipId)
        selectedSection = .chat
        pendingHandshakeConfirmation = nil
        Self.saveRelationships(state.relationships)
        state.lastEvent = "Финальный QR принят: \(displayName)"
        return nil
    }

    func sendMessage(body: String) {
        guard let relationship = selectedRelationship else {
            state.lastEvent = "Сообщение не отправлено: устройство не выбрано"
            return
        }
        sendMessage(body: body, relationship: relationship)
    }

    private func sendMessage(body: String, relationship: Relationship, replacingFailedMessageId: String? = nil) {
        let trimmedBody = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedBody.isEmpty else { return }
        guard relationship.state.isMessageCapable else {
            state.lastEvent = "Сообщение не отправлено: связь с устройством не активна"
            return
        }
        if let replacingFailedMessageId,
           state.routes.first(where: { $0.relationshipId == relationship.relationshipId })?.kind != .directLan,
           state.routes.first(where: { $0.relationshipId == relationship.relationshipId })?.kind != .directBle {
            state.messages.removeAll { $0.messageId == replacingFailedMessageId }
            Self.saveMessages(state.messages)
        }
        let route = state.routes.first { $0.relationshipId == relationship.relationshipId }
        switch route?.kind {
        case .directLan:
            sendLanMessage(body: trimmedBody, relationship: relationship, existingMessageId: replacingFailedMessageId)
        case .directBle:
            sendBleMessage(body: trimmedBody, relationship: relationship, existingMessageId: replacingFailedMessageId)
        default:
            if let replacingFailedMessageId {
                outboxRetryRecords.removeValue(forKey: replacingFailedMessageId)
                Self.saveOutboxRetryRecords(outboxRetryRecords)
            }
            state = simulator.sendMessage(in: state, relationshipId: relationship.relationshipId, body: trimmedBody)
            Self.saveMessages(state.messages)
        }
    }

    private func ensureLanListenerRunning() {
        guard lanModeEnabled, !lanListenerRunning else { return }
        startLanListener()
    }

    func confirmLatestDelivery() {
        guard let selectedRelationshipId else { return }
        state = simulator.confirmLatestDelivery(in: state, relationshipId: selectedRelationshipId)
        Self.saveMessages(state.messages)
    }

    func cycleSelectedRoute() {
        guard let selectedRelationshipId else { return }
        state = simulator.cycleRoute(in: state, relationshipId: selectedRelationshipId)
        Self.saveMessages(state.messages)
        flushReadyOutbox(reason: "Маршрут изменён")
    }

    func evaluateAdmission(experimental: Bool) {
        state = simulator.evaluateAdmission(in: state, experimental: experimental)
        Self.saveMessages(state.messages)
    }

    func toggleLanMode() {
        lanModeEnabled.toggle()
        if lanModeEnabled {
            restoreRouteKind(.directLan)
            state.lastEvent = "Wi-Fi/LAN включён для Kraken"
            flushReadyOutbox(reason: "Wi-Fi/LAN включён")
        } else {
            stopLanListener()
            disableRouteKind(.directLan)
            state.lastEvent = "Wi-Fi/LAN выключен для Kraken"
        }
    }

    func toggleBluetoothMode() {
        bluetoothModeEnabled.toggle()
        if bluetoothModeEnabled {
            state.lastEvent = "Bluetooth включён для Kraken"
            flushReadyOutbox(reason: "Bluetooth включён")
        } else {
            stopBleTransport()
            disableRouteKind(.directBle)
            state.lastEvent = "Bluetooth выключен для Kraken"
        }
    }

    func createRealm(name: String) {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return }
        desktopRealms.insert(
            DesktopRealm(
                realmId: "realm-\(UUID().uuidString)",
                name: trimmedName,
                state: .active,
                memberCount: 1,
                pendingRequests: 0,
                updatedAt: Date()
            ),
            at: 0
        )
        selectedRealmId = desktopRealms.first?.realmId
        state.lastEvent = "Реалм создан: \(trimmedName)"
    }

    func refreshAdbDevices() {
        runProbe { self.probeService.adbDevices() }
    }

    func runDesktopRelayPreflight() {
        runProbe { self.probeService.desktopRelayPreflight() }
    }

    func startLanListener() {
        guard lanModeEnabled else {
            state.lastEvent = "Приём LAN не запущен: Wi-Fi/LAN выключен для Kraken"
            return
        }
        guard let port = Int(lanListenPort), port > 0, port <= 65535 else {
            state.lastEvent = "Приём LAN не запущен: неверный порт"
            return
        }
        do {
            let actualPort = try lanListener.start(requestedPort: port) { [weak self] event in
                Task { @MainActor in
                    self?.recordLanEvent(event)
                }
            }
            lanListenerRunning = true
            lanListenerPort = actualPort
            state.lastEvent = "Приём LAN запущен на порту \(actualPort)"
        } catch {
            state.lastEvent = "Ошибка приёма LAN: \(error)"
        }
    }

    func stopLanListener() {
        lanListener.stop()
        lanListenerRunning = false
        lanListenerPort = nil
        state.lastEvent = "Приём LAN остановлен"
    }

    func sendLanFrameToTarget() {
        guard let relationship = selectedRelationship else {
            state.lastEvent = "LAN-отправка невозможна: устройство не выбрано"
            return
        }
        sendLanMessage(body: lanBody, relationship: relationship)
    }

    func selectLanBridgeEndpointPeer() {
        let endpointFingerprint = lanTargetFingerprint.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !endpointFingerprint.isEmpty else {
            state.lastEvent = "LAN/ADB узел не выбран: отпечаток устройства пустой"
            return
        }
        guard let port = Int(lanTargetPort), port > 0, port <= 65535 else {
            state.lastEvent = "LAN/ADB узел не выбран: неверный порт назначения"
            return
        }
        let host = lanTargetHost.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty else {
            state.lastEvent = "LAN/ADB узел не выбран: адрес пустой"
            return
        }

        let normalizedEndpoint = Self.normalizedFingerprint(endpointFingerprint)
        let displayName = lanTargetDisplayName.trimmingCharacters(in: .whitespacesAndNewlines)
            .ifBlank("LAN/ADB устройство")
        let now = Date()
        let relationshipId: String
        if let index = state.relationships.firstIndex(where: {
            Self.normalizedFingerprint($0.peerFingerprint) == normalizedEndpoint
        }) {
            relationshipId = state.relationships[index].relationshipId
            state.relationships[index].peerDisplayName = displayName
            state.relationships[index].peerFingerprint = endpointFingerprint
            state.relationships[index].state = .active
            state.relationships[index].updatedAt = now
        } else {
            relationshipId = "rel-lan-adb-\(normalizedEndpoint.prefix(16))"
            state.relationships.insert(
                Relationship(
                    relationshipId: relationshipId,
                    peerDisplayName: displayName,
                    peerFingerprint: endpointFingerprint,
                    state: .active,
                    cryptoProfileId: "standard-reviewed-primitives-v1",
                    admissionDecisionHash: "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
                    updatedAt: now
                ),
                at: 0
            )
        }

        upsertLanBridgeRoute(
            relationshipId: relationshipId,
            peerFingerprint: endpointFingerprint,
            now: now
        )
        if let relationship = state.relationships.first(where: { $0.relationshipId == relationshipId }) {
            rememberCurrentLanEndpoint(for: relationship)
        }
        selectRelationship(relationshipId)
        selectedSection = .chat
        transportPanelVisible = true
        Self.saveRelationships(state.relationships)
        state.lastEvent = "LAN/ADB конечная точка выбрана как узел: \(displayName)"
        flushReadyOutbox(reason: "LAN/ADB конечная точка выбрана")
    }

    private func sendLanMessage(body: String, relationship: Relationship, existingMessageId: String? = nil) {
        let trimmedBody = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedBody.isEmpty else {
            state.lastEvent = "LAN-отправка невозможна: пустое сообщение"
            return
        }
        guard lanModeEnabled else {
            if let existingMessageId {
                markOutgoingMessage(existingMessageId, as: .readyForTransport)
            } else {
                appendWaitingMessage(body: trimmedBody, relationship: relationship)
            }
            state.lastEvent = "Сообщение ожидает маршрут: Wi-Fi/LAN выключен для Kraken"
            return
        }
        guard let port = Int(lanTargetPort), port > 0, port <= 65535 else {
            appendFailedLanAttempt(
                body: trimmedBody,
                relationship: relationship,
                existingMessageId: existingMessageId,
                error: "invalid-target-port",
                eventMessage: "LAN-отправка невозможна: неверный порт назначения"
            )
            return
        }
        let endpointHost = lanTargetHost.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !endpointHost.isEmpty else {
            appendFailedLanAttempt(
                body: trimmedBody,
                relationship: relationship,
                existingMessageId: existingMessageId,
                error: "empty-target-host",
                eventMessage: "LAN-отправка невозможна: адрес пустой"
            )
            return
        }
        let endpointFingerprint = lanTargetFingerprint.trimmingCharacters(in: .whitespacesAndNewlines)
        guard Self.normalizedFingerprint(endpointFingerprint) == Self.normalizedFingerprint(relationship.peerFingerprint) else {
            appendFailedLanAttempt(
                body: trimmedBody,
                relationship: relationship,
                existingMessageId: existingMessageId,
                error: "endpoint-fingerprint-mismatch",
                eventMessage: "LAN-отправка остановлена: конечная точка не совпадает с выбранным узлом"
            )
            return
        }
        ensureLanListenerRunning()
        let endpoint = MacLanEndpoint(
            host: endpointHost,
            port: port,
            fingerprint: endpointFingerprint,
            displayName: lanTargetDisplayName.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let messageId = existingMessageId ?? "macos-\(UUID().uuidString)"
        let payloadData = try? JSONSerialization.data(
            withJSONObject: ["message_id": messageId, "body": trimmedBody],
            options: [.sortedKeys]
        )
        let payloadJson = payloadData.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        markOrAppendOutgoingMessage(
            messageId: messageId,
            body: trimmedBody,
            relationship: relationship,
            status: .sentToTransport
        )
        let packet = KrakenPacket(
            packetId: "packet-\(UUID().uuidString)",
            senderFingerprint: lanLocalFingerprint.trimmingCharacters(in: .whitespacesAndNewlines),
            recipientFingerprint: endpoint.fingerprint,
            relationshipId: relationship.relationshipId,
            conversationId: "desktop-\(relationship.relationshipId)",
            messageId: messageId,
            createdAtEpochMillis: nowMillis,
            expiresAtEpochMillis: nowMillis + 300_000,
            payloadJson: payloadJson,
            cryptoProfileId: relationship.cryptoProfileId,
            sessionProfileId: Self.sessionProfileId(for: relationship),
            admissionDecisionHash: relationship.admissionDecisionHash,
            profilePolicyVersion: relationship.profilePolicyVersion ?? 1
        )
        let envelope = LanFrameEnvelope(
            senderPeerId: lanLocalPeerId.trimmingCharacters(in: .whitespacesAndNewlines),
            senderFingerprint: packet.senderFingerprint,
            senderDisplayName: state.localIdentity?.displayName ?? "Kraken Desktop",
            senderReplyPort: lanListenerPort,
            packet: packet
        )
        probeRunning = true
        Task {
            let event = await Task.detached(priority: .userInitiated) {
                self.lanSender.send(envelope: envelope, endpoint: endpoint)
            }.value
            recordLanEvent(event)
            probeRunning = false
        }
    }

    private func sendBleMessage(body: String, relationship: Relationship, existingMessageId: String? = nil) {
        let trimmedBody = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedBody.isEmpty else {
            state.lastEvent = "BLE-отправка невозможна: пустое сообщение"
            return
        }
        guard bluetoothModeEnabled else {
            if let existingMessageId {
                markOutgoingMessage(existingMessageId, as: .readyForTransport)
            } else {
                appendWaitingMessage(body: trimmedBody, relationship: relationship)
            }
            state.lastEvent = "Сообщение ожидает маршрут: Bluetooth выключен для Kraken"
            return
        }
        refreshBleStatus()
        guard bleStatus.authorizationState == "allowed" else {
            appendFailedBleAttempt(
                body: trimmedBody,
                relationship: relationship,
                existingMessageId: existingMessageId,
                error: "ble-authorization-\(bleStatus.authorizationState)",
                eventMessage: "BLE-отправка невозможна: macOS не разрешила Bluetooth"
            )
            return
        }
        guard bleRunning else {
            appendFailedBleAttempt(
                body: trimmedBody,
                relationship: relationship,
                existingMessageId: existingMessageId,
                error: "ble-not-running",
                eventMessage: "BLE-отправка невозможна: Bluetooth-транспорт не запущен"
            )
            return
        }

        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let messageId = existingMessageId ?? "macos-\(UUID().uuidString)"
        let payloadData = try? JSONSerialization.data(
            withJSONObject: ["message_id": messageId, "body": trimmedBody],
            options: [.sortedKeys]
        )
        let payloadJson = payloadData.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        markOrAppendOutgoingMessage(
            messageId: messageId,
            body: trimmedBody,
            relationship: relationship,
            status: .sentToTransport
        )
        let packet = KrakenPacket(
            packetId: "packet-\(UUID().uuidString)",
            senderFingerprint: lanLocalFingerprint.trimmingCharacters(in: .whitespacesAndNewlines),
            recipientFingerprint: relationship.peerFingerprint,
            relationshipId: relationship.relationshipId,
            conversationId: "desktop-\(relationship.relationshipId)",
            messageId: messageId,
            createdAtEpochMillis: nowMillis,
            expiresAtEpochMillis: nowMillis + 300_000,
            payloadJson: payloadJson,
            cryptoProfileId: relationship.cryptoProfileId,
            sessionProfileId: Self.sessionProfileId(for: relationship),
            admissionDecisionHash: relationship.admissionDecisionHash,
            profilePolicyVersion: relationship.profilePolicyVersion ?? 1
        )
        let envelope = LanFrameEnvelope(
            senderPeerId: lanLocalPeerId.trimmingCharacters(in: .whitespacesAndNewlines),
            senderFingerprint: packet.senderFingerprint,
            senderDisplayName: state.localIdentity?.displayName ?? "Kraken Desktop",
            senderReplyPort: lanListenerPort,
            packet: packet
        )
        let event = bleTransport.send(envelope: envelope, toPeerFingerprint: relationship.peerFingerprint)
        recordBleEvent(event)
        refreshBleStatus()
    }

    func saveLanEvidence() {
        let endpoint = MacLanEndpoint(
            host: lanTargetHost.trimmingCharacters(in: .whitespacesAndNewlines),
            port: Int(lanTargetPort) ?? 0,
            fingerprint: lanTargetFingerprint.trimmingCharacters(in: .whitespacesAndNewlines),
            displayName: lanTargetDisplayName.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        do {
            let directory = try evidenceWriter.writeLanTransportEvidence(
                listenerPort: lanListenerPort,
                endpoint: endpoint,
                selectedRelationship: selectedRelationship,
                selectedRoute: selectedRoute,
                events: lanEvents,
                boundary: "Артефакт проверки LAN/TCP транспорта Kraken Desktop; это LAN/ADB-мост, не нативный Android Wi-Fi Direct и не промышленная криптография."
            )
            lastEvidencePath = directory.path
            state.lastEvent = "Артефакт LAN сохранён: \(directory.lastPathComponent)"
        } catch {
            state.lastEvent = "Ошибка сохранения LAN-артефакта: \(error)"
        }
    }

    func saveBleEvidence() {
        refreshBleStatus()
        do {
            let directory = try evidenceWriter.writeBleTransportEvidence(
                status: bleStatus,
                selectedRelationship: selectedRelationship,
                selectedRoute: selectedRoute,
                events: bleEvents,
                boundary: "Артефакт проверки CoreBluetooth BLE-транспорта Kraken Desktop; он фиксирует UUID/framing, central/peripheral роли, события и macOS authorization state. Доставка с телефоном доказана только при обнаруженном peer или accepted/queued BLE events."
            )
            lastEvidencePath = directory.path
            state.lastEvent = "Артефакт Bluetooth сохранён: \(directory.lastPathComponent)"
        } catch {
            state.lastEvent = "Ошибка сохранения BLE-артефакта: \(error)"
        }
    }

    func startBleTransport() {
        guard bluetoothModeEnabled else {
            state.lastEvent = "Bluetooth не запущен: выключен для Kraken"
            return
        }
        guard let identity = state.localIdentity else {
            state.lastEvent = "BLE не запущен: профиль не создан"
            return
        }
        let bleIdentity = MacBlePeerIdentity(
            peerId: identity.identityId,
            fingerprint: identity.fingerprint,
            displayName: identity.displayName
        )
        bleTransport.start(localIdentity: bleIdentity) { [weak self] event in
            Task { @MainActor in
                self?.recordBleEvent(event)
                self?.refreshBleStatus()
            }
        }
        bleRunning = true
        refreshBleStatus()
        state.lastEvent = bleStartupStatusMessage()
        flushReadyOutbox(reason: "Bluetooth запущен")
    }

    func stopBleTransport() {
        bleTransport.stop()
        bleRunning = false
        refreshBleStatus()
        state.lastEvent = "Bluetooth остановлен"
    }

    func refreshBleStatus() {
        bleStatus = bleTransport.currentStatus()
    }

    func openBluetoothPrivacySettings() {
        guard let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Bluetooth") else {
            return
        }
        NSWorkspace.shared.open(url)
    }

    private func bleStartupStatusMessage() -> String {
        switch bleStatus.authorizationState {
        case "allowed":
            if bleStatus.centralState == "scanning" || bleStatus.peripheralState == "advertising" {
                return "Bluetooth запущен"
            }
            return "Bluetooth запускается"
        case "not-determined":
            return "Bluetooth ожидает разрешение macOS"
        case "denied":
            return "Bluetooth не запущен: доступ запрещён в macOS"
        case "restricted":
            return "Bluetooth не запущен: доступ ограничен macOS"
        default:
            return "Bluetooth не запущен: \(bleStatus.authorizationState)"
        }
    }

    private func recordLanEvent(_ event: MacLanTransferEvent) {
        lanEvents.insert(event, at: 0)
        if lanEvents.count > 40 {
            lanEvents.removeLast(lanEvents.count - 40)
        }
        let result = LanTimelineReducer.apply(event: event, to: state)
        state = result.state
        let rescheduled = event.direction == .outbound
            ? handleOutboundTransportResult(messageId: event.messageId, status: event.status)
            : false
        Self.saveMessages(state.messages)
        if let selectedRelationshipId = result.selectedRelationshipId {
            self.selectedRelationshipId = selectedRelationshipId
            selectedSection = .chat
        }
        state.lastEvent = rescheduled
            ? "LAN \(lanDirectionTitle(event.direction)): ошибка, ждём повтор"
            : "LAN \(lanDirectionTitle(event.direction)): \(lanStatusTitle(event.status))"
        if !rescheduled {
            flushReadyOutbox(reason: "LAN-маршрут обновлён")
        }
    }

    private func recordBleEvent(_ event: MacBleTransferEvent) {
        bleEvents.insert(event, at: 0)
        if bleEvents.count > 40 {
            bleEvents.removeLast(bleEvents.count - 40)
        }
        let result = BleTimelineReducer.apply(event: event, to: state)
        state = result.state
        let rescheduled = event.direction == .outbound
            ? handleOutboundTransportResult(messageId: event.messageId, status: event.status)
            : false
        Self.saveMessages(state.messages)
        if let selectedRelationshipId = result.selectedRelationshipId {
            self.selectedRelationshipId = selectedRelationshipId
            selectedSection = .chat
        }
        state.lastEvent = rescheduled
            ? "Bluetooth \(bleDirectionTitle(event.direction)): ошибка, ждём повтор"
            : "Bluetooth \(bleDirectionTitle(event.direction)): \(bleStatusTitle(event.status))"
        if !rescheduled {
            flushReadyOutbox(reason: "Bluetooth-маршрут обновлён")
        }
    }

    private func runProbe(_ work: @escaping () -> DesktopProbeResult) {
        probeRunning = true
        Task {
            let result = await Task.detached(priority: .userInitiated) {
                work()
            }.value
            latestProbeResult = result
            probeRunning = false
            state.lastEvent = result.succeeded
                ? "\(result.title): успешно"
                : "\(result.title): ошибка"
        }
    }

    private func appendWaitingMessage(body: String, relationship: Relationship) {
        let messageId = appendOutgoingMessage(
            body: body.trimmingCharacters(in: .whitespacesAndNewlines),
            relationship: relationship,
            status: .readyForTransport
        )
        outboxRetryRecords[messageId] = OutboxRetryRecord(attempts: 0, nextAttemptAt: Date())
        Self.saveOutboxRetryRecords(outboxRetryRecords)
    }

    private func markOutgoingMessage(_ messageId: String, as status: MessageStatus) {
        guard let index = state.messages.firstIndex(where: { $0.messageId == messageId }) else { return }
        state.messages[index].status = status
        state.messages[index].updatedAt = Date()
        Self.saveMessages(state.messages)
    }

    private func markOrAppendOutgoingMessage(
        messageId: String,
        body: String,
        relationship: Relationship,
        status: MessageStatus
    ) {
        if let index = state.messages.firstIndex(where: { $0.messageId == messageId }) {
            state.messages[index].status = status
            state.messages[index].updatedAt = Date()
            Self.saveMessages(state.messages)
        } else {
            appendOutgoingMessage(
                messageId: messageId,
                body: body,
                relationship: relationship,
                status: status
            )
        }
    }

    private func flushReadyOutbox(reason: String) {
        guard !outboxFlushInProgress else { return }
        outboxFlushInProgress = true
        defer { outboxFlushInProgress = false }

        expireReadyOutbox()
        let waitingMessages = state.messages.filter {
            $0.direction == .outgoing && $0.status == .readyForTransport
        }
        guard !waitingMessages.isEmpty else {
            scheduleNextOutboxFlush()
            return
        }

        var flushed = 0
        let now = Date()
        for message in waitingMessages {
            guard let relationship = state.relationships.first(where: { $0.relationshipId == message.relationshipId }),
                  canAttemptQueuedMessage(for: relationship),
                  canAttemptOutboxMessage(message, now: now) else {
                continue
            }
            flushed += 1
            registerOutboxAttempt(messageId: message.messageId, now: now)
            sendMessage(body: message.body, relationship: relationship, replacingFailedMessageId: message.messageId)
        }
        if flushed > 0 {
            state.lastEvent = "\(reason): отправлено из очереди \(flushed)"
        }
        scheduleNextOutboxFlush()
    }

    private func expireReadyOutbox(now: Date = Date()) {
        var changed = false
        for index in state.messages.indices {
            guard state.messages[index].direction == .outgoing,
                  state.messages[index].status == .readyForTransport,
                  now.timeIntervalSince(state.messages[index].createdAt) > Self.messageTransportTtl else {
                continue
            }
            state.messages[index].status = .failed
            state.messages[index].updatedAt = now
            outboxRetryRecords.removeValue(forKey: state.messages[index].messageId)
            changed = true
        }
        if changed {
            Self.saveMessages(state.messages)
            Self.saveOutboxRetryRecords(outboxRetryRecords)
        }
    }

    private func canAttemptQueuedMessage(for relationship: Relationship) -> Bool {
        guard relationship.state.isMessageCapable else { return false }
        let route = state.routes.first { $0.relationshipId == relationship.relationshipId }
        switch route?.kind {
        case .directLan:
            return lanModeEnabled
        case .directBle:
            return bluetoothModeEnabled && bleRunning
        default:
            return false
        }
    }

    private func canAttemptOutboxMessage(_ message: LocalMessage, now: Date) -> Bool {
        guard now.timeIntervalSince(message.createdAt) <= Self.messageTransportTtl else { return false }
        return (outboxRetryRecords[message.messageId]?.nextAttemptAt ?? message.createdAt) <= now
    }

    private func registerOutboxAttempt(messageId: String, now: Date) {
        let attempts = (outboxRetryRecords[messageId]?.attempts ?? 0) + 1
        let delay = KrakenOutboxBackoffPolicy.retryDelay(afterAttempt: attempts)
        outboxRetryRecords[messageId] = OutboxRetryRecord(
            attempts: attempts,
            nextAttemptAt: now.addingTimeInterval(delay)
        )
        Self.saveOutboxRetryRecords(outboxRetryRecords)
    }

    private func handleOutboundTransportResult(messageId: String?, status: MacLanEventStatus) -> Bool {
        guard let messageId else { return false }
        switch status {
        case .acked, .accepted:
            outboxRetryRecords.removeValue(forKey: messageId)
            Self.saveOutboxRetryRecords(outboxRetryRecords)
            return false
        case .failed:
            return rescheduleOutboxMessageAfterFailure(messageId)
        }
    }

    private func handleOutboundTransportResult(messageId: String?, status: MacBleEventStatus) -> Bool {
        guard let messageId else { return false }
        switch status {
        case .queued, .accepted:
            outboxRetryRecords.removeValue(forKey: messageId)
            Self.saveOutboxRetryRecords(outboxRetryRecords)
            return false
        case .failed:
            return rescheduleOutboxMessageAfterFailure(messageId)
        }
    }

    private func rescheduleOutboxMessageAfterFailure(_ messageId: String) -> Bool {
        guard outboxRetryRecords[messageId] != nil,
              let index = state.messages.firstIndex(where: { $0.messageId == messageId }) else {
            return false
        }
        let now = Date()
        guard now.timeIntervalSince(state.messages[index].createdAt) <= Self.messageTransportTtl else {
            outboxRetryRecords.removeValue(forKey: messageId)
            Self.saveOutboxRetryRecords(outboxRetryRecords)
            return false
        }
        state.messages[index].status = .readyForTransport
        state.messages[index].updatedAt = now
        Self.saveMessages(state.messages)
        scheduleNextOutboxFlush()
        return true
    }

    private func scheduleNextOutboxFlush() {
        outboxFlushTask?.cancel()
        let now = Date()
        let nextAttempt = state.messages
            .filter { $0.direction == .outgoing && $0.status == .readyForTransport }
            .compactMap { message -> Date? in
                guard now.timeIntervalSince(message.createdAt) <= Self.messageTransportTtl else { return nil }
                return outboxRetryRecords[message.messageId]?.nextAttemptAt ?? now
            }
            .min()
        guard let nextAttempt else { return }
        let delay = max(0.25, nextAttempt.timeIntervalSince(now))
        outboxFlushTask = Task { [weak self] in
            let nanoseconds = UInt64(delay * 1_000_000_000)
            try? await Task.sleep(nanoseconds: nanoseconds)
            await MainActor.run {
                self?.flushReadyOutbox(reason: "Повторная отправка")
            }
        }
    }

    @discardableResult
    private func appendOutgoingMessage(
        messageId: String = "macos-\(UUID().uuidString)",
        body: String,
        relationship: Relationship,
        status: MessageStatus
    ) -> String {
        let now = Date()
        state.messages.append(
            LocalMessage(
                messageId: messageId,
                relationshipId: relationship.relationshipId,
                peerFingerprint: relationship.peerFingerprint,
                direction: .outgoing,
                status: status,
                body: body,
                createdAt: now,
                updatedAt: now
            )
        )
        Self.saveMessages(state.messages)
        return messageId
    }

    private func appendFailedLanAttempt(
        body: String,
        relationship: Relationship,
        existingMessageId: String? = nil,
        error: String,
        eventMessage: String
    ) {
        let messageId = existingMessageId ?? appendOutgoingMessage(
            body: body,
            relationship: relationship,
            status: .failed
        )
        if existingMessageId != nil {
            markOutgoingMessage(messageId, as: .failed)
        }
        lanEvents.insert(
            MacLanTransferEvent(
                direction: .outbound,
                status: .failed,
                atEpochMillis: Int64(Date().timeIntervalSince1970 * 1000),
                source: lanLocalPeerId.trimmingCharacters(in: .whitespacesAndNewlines),
                target: "\(lanTargetHost.trimmingCharacters(in: .whitespacesAndNewlines)):\(lanTargetPort)",
                packetId: nil,
                messageId: messageId,
                senderDisplayName: state.localIdentity?.displayName ?? "Kraken Desktop",
                senderFingerprint: lanLocalFingerprint.trimmingCharacters(in: .whitespacesAndNewlines),
                recipientFingerprint: relationship.peerFingerprint,
                relationshipId: relationship.relationshipId,
                error: error
            ),
            at: 0
        )
        if lanEvents.count > 40 {
            lanEvents.removeLast(lanEvents.count - 40)
        }
        if existingMessageId != nil, rescheduleOutboxMessageAfterFailure(messageId) {
            state.lastEvent = "\(eventMessage). Повтор запланирован."
            return
        }
        state.lastEvent = eventMessage
    }

    private func appendFailedBleAttempt(
        body: String,
        relationship: Relationship,
        existingMessageId: String? = nil,
        error: String,
        eventMessage: String
    ) {
        let messageId = existingMessageId ?? appendOutgoingMessage(
            body: body,
            relationship: relationship,
            status: .failed
        )
        if existingMessageId != nil {
            markOutgoingMessage(messageId, as: .failed)
        }
        bleEvents.insert(
            MacBleTransferEvent(
                direction: .outbound,
                status: .failed,
                atEpochMillis: Int64(Date().timeIntervalSince1970 * 1000),
                peerFingerprint: relationship.peerFingerprint,
                packetId: nil,
                messageId: messageId,
                error: error
            ),
            at: 0
        )
        if bleEvents.count > 40 {
            bleEvents.removeLast(bleEvents.count - 40)
        }
        if existingMessageId != nil, rescheduleOutboxMessageAfterFailure(messageId) {
            state.lastEvent = "\(eventMessage). Повтор запланирован."
            return
        }
        state.lastEvent = eventMessage
    }

    private func disableRouteKind(_ kind: PeerRouteKind) {
        state.routes = state.routes.map { route in
            guard route.kind == kind else { return route }
            return PeerRouteSnapshot(
                relationshipId: route.relationshipId,
                peerFingerprint: route.peerFingerprint,
                kind: .none,
                transportId: nil,
                bandwidthClass: .none,
                hopCount: nil,
                lastSeenAt: nil
            )
        }
    }

    private func restoreRouteKind(_ kind: PeerRouteKind) {
        state.routes = state.routes.map { route in
            guard route.kind == .none else { return route }
            guard state.relationships.first(where: { $0.relationshipId == route.relationshipId })?.state == .active else {
                return route
            }
            return PeerRouteSnapshot(
                relationshipId: route.relationshipId,
                peerFingerprint: route.peerFingerprint,
                kind: kind,
                transportId: kind == .directLan ? "macos-lan-tcp" : "macos-ble-gatt",
                bandwidthClass: kind == .directLan ? .high : .low,
                hopCount: 1,
                lastSeenAt: Date()
            )
        }
    }

    private func upsertLanBridgeRoute(
        relationshipId: String,
        peerFingerprint: String,
        now: Date
    ) {
        let route = PeerRouteSnapshot(
            relationshipId: relationshipId,
            peerFingerprint: peerFingerprint,
            kind: .directLan,
            transportId: "macos-lan-adb-bridge",
            bandwidthClass: .high,
            hopCount: 1,
            lastSeenAt: now
        )
        if let index = state.routes.firstIndex(where: { $0.relationshipId == relationshipId }) {
            state.routes[index] = route
        } else {
            state.routes.insert(route, at: 0)
        }
    }

    private func rememberCurrentLanEndpoint(for relationship: Relationship) {
        let key = Self.normalizedFingerprint(relationship.peerFingerprint)
        guard !key.isEmpty else { return }
        let host = lanTargetHost.trimmingCharacters(in: .whitespacesAndNewlines)
        let port = lanTargetPort.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty, !port.isEmpty else { return }
        lanEndpointBindings[key] = LanEndpointBinding(
            host: host,
            port: port,
            fingerprint: relationship.peerFingerprint,
            displayName: relationship.peerDisplayName,
            updatedAt: Date()
        )
        Self.saveLanEndpointBindings(lanEndpointBindings)
    }

    private func applyLanEndpointBindingForSelectedPeer() {
        guard let relationship = selectedRelationship else { return }
        let key = Self.normalizedFingerprint(relationship.peerFingerprint)
        if let binding = lanEndpointBindings[key] {
            lanTargetHost = binding.host
            lanTargetPort = binding.port
            lanTargetFingerprint = binding.fingerprint
            lanTargetDisplayName = binding.displayName.ifBlank(relationship.peerDisplayName)
        } else {
            lanTargetFingerprint = relationship.peerFingerprint
            lanTargetDisplayName = relationship.peerDisplayName
        }
    }

    private static func normalizedFingerprint(_ value: String) -> String {
        String(value.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }).uppercased()
    }

    private static func sessionProfileId(for relationship: Relationship) -> String {
        "session-\(relationship.relationshipId)-\(relationship.cryptoProfileId)"
    }

    private static func offlineHandshakeRelationshipId(
        inviteId: String,
        inviterFingerprint: String,
        responderFingerprint: String
    ) -> String {
        "relationship-\(stableToken(inviteId))-\(stableToken(inviterFingerprint))-\(stableToken(responderFingerprint))"
    }

    private static func stableToken(_ value: String) -> String {
        String(value.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }.prefix(12)).ifBlank("unknown")
    }

    private static func responseIdFor(inviteId: String, responderFingerprint: String) -> String {
        "response-\(stableToken(inviteId))-\(stableToken(responderFingerprint))"
    }

    private static func confirmationIdFor(inviteId: String, responderFingerprint: String) -> String {
        "confirmation-\(stableToken(inviteId))-\(stableToken(responderFingerprint))"
    }

    private static func makeConfirmationSnapshot(
        for payload: KrakenHandshakeResponsePayload,
        identity: LocalIdentity,
        peerDisplayName: String,
        createdAt: Date
    ) -> HandshakeConfirmationSnapshot {
        let nowMillis = Int64(createdAt.timeIntervalSince1970 * 1000)
        let confirmation: [String: Any] = [
            "type": KrakenHandshakeQrCodec.confirmationTypeName,
            "version": 1,
            "confirmation_id": confirmationIdFor(inviteId: payload.inviteId, responderFingerprint: payload.responderFingerprint),
            "response_id": payload.responseId.ifBlank(responseIdFor(inviteId: payload.inviteId, responderFingerprint: payload.responderFingerprint)),
            "invite_id": payload.inviteId,
            "realm_id": payload.realmId ?? NSNull(),
            "realm_name": NSNull(),
            "membership_certificate": NSNull(),
            "inviter_fingerprint": identity.fingerprint,
            "responder_fingerprint": payload.responderFingerprint,
            "created_at_epoch_millis": nowMillis,
            "crypto_profile_id": payload.cryptoProfileId ?? NSNull(),
            "crypto_profile_hash": payload.cryptoProfileHash ?? NSNull(),
            "admission_decision_hash": payload.admissionDecisionHash ?? NSNull(),
            "profile_policy_version": payload.profilePolicyVersion.map { $0 as Any } ?? NSNull(),
            "native_backend_version": payload.nativeBackendVersion ?? NSNull(),
            "proof_placeholder": "prototype-offline-qr-confirmation-not-production-crypto",
        ]
        let data = (try? JSONSerialization.data(withJSONObject: confirmation, options: [.sortedKeys])) ?? Data()
        let payloadJson = String(data: data, encoding: .utf8) ?? "{}"
        let qrPayload = (try? KrakenHandshakeQrCodec.encodedQrPayload(payloadJson)) ?? payloadJson
        return HandshakeConfirmationSnapshot(
            title: "Финальный QR",
            subtitle: peerDisplayName,
            payload: qrPayload,
            details: [
                ("Отпечаток", KrakenFormatters.compactFingerprint(payload.responderFingerprint)),
                ("Статус", "покажите этот QR на Samsung"),
            ]
        )
    }

    private static func loadLocalIdentity() -> LocalIdentity? {
        guard let data = UserDefaults.standard.data(forKey: localIdentityDefaultsKey) else {
            return nil
        }
        return try? JSONDecoder().decode(LocalIdentity.self, from: data)
    }

    private static func saveLocalIdentity(_ identity: LocalIdentity) {
        guard let data = try? JSONEncoder().encode(identity) else {
            return
        }
        UserDefaults.standard.set(data, forKey: localIdentityDefaultsKey)
    }

    private static func loadRelationships() -> [Relationship]? {
        guard let data = UserDefaults.standard.data(forKey: relationshipsDefaultsKey) else {
            return nil
        }
        return try? JSONDecoder().decode([Relationship].self, from: data)
    }

    private static func saveRelationships(_ relationships: [Relationship]) {
        guard let data = try? JSONEncoder().encode(relationships) else {
            return
        }
        UserDefaults.standard.set(data, forKey: relationshipsDefaultsKey)
    }

    private static func loadMessages() -> [LocalMessage]? {
        guard let data = UserDefaults.standard.data(forKey: messagesDefaultsKey) else {
            return nil
        }
        return try? JSONDecoder().decode([LocalMessage].self, from: data)
    }

    private static func saveMessages(_ messages: [LocalMessage]) {
        guard let data = try? JSONEncoder().encode(messages) else {
            return
        }
        UserDefaults.standard.set(data, forKey: messagesDefaultsKey)
    }

    private static func loadOutboxRetryRecords() -> [String: OutboxRetryRecord] {
        guard let data = UserDefaults.standard.data(forKey: outboxRetryDefaultsKey),
              let records = try? JSONDecoder().decode([String: OutboxRetryRecord].self, from: data) else {
            return [:]
        }
        return records
    }

    private static func saveOutboxRetryRecords(_ records: [String: OutboxRetryRecord]) {
        guard let data = try? JSONEncoder().encode(records) else {
            return
        }
        UserDefaults.standard.set(data, forKey: outboxRetryDefaultsKey)
    }

    private static func loadLanEndpointBindings() -> [String: LanEndpointBinding] {
        guard let data = UserDefaults.standard.data(forKey: lanEndpointBindingsDefaultsKey),
              let bindings = try? JSONDecoder().decode([String: LanEndpointBinding].self, from: data) else {
            return [:]
        }
        return bindings
    }

    private static func saveLanEndpointBindings(_ bindings: [String: LanEndpointBinding]) {
        guard let data = try? JSONEncoder().encode(bindings) else {
            return
        }
        UserDefaults.standard.set(data, forKey: lanEndpointBindingsDefaultsKey)
    }

    private static func loadTheme() -> KrakenThemePreset {
        guard let rawValue = UserDefaults.standard.string(forKey: themeDefaultsKey),
              let theme = KrakenThemePreset(rawValue: rawValue)
        else {
            return .dusk
        }
        return theme
    }

    private func lanDirectionTitle(_ direction: MacLanEventDirection) -> String {
        switch direction {
        case .inbound: "входящий кадр"
        case .outbound: "исходящий кадр"
        }
    }

    private func lanStatusTitle(_ status: MacLanEventStatus) -> String {
        switch status {
        case .accepted: "принят"
        case .acked: "подтверждён"
        case .failed: "ошибка"
        }
    }

    private func bleDirectionTitle(_ direction: MacBleEventDirection) -> String {
        switch direction {
        case .inbound: "входящий кадр"
        case .outbound: "исходящий кадр"
        }
    }

    private func bleStatusTitle(_ status: MacBleEventStatus) -> String {
        switch status {
        case .accepted: "принят"
        case .queued: "в очереди"
        case .failed: "ошибка"
        }
    }
}

private extension String {
    func ifBlank(_ fallback: String) -> String {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallback : self
    }
}

struct HandshakeConfirmationSnapshot {
    let title: String
    let subtitle: String
    let payload: String
    let details: [(String, String)]
}

private struct LanEndpointBinding: Codable {
    let host: String
    let port: String
    let fingerprint: String
    let displayName: String
    let updatedAt: Date
}

private struct OutboxRetryRecord: Codable {
    var attempts: Int
    var nextAttemptAt: Date
}

private struct DesktopInvitePayload: Decodable {
    static let typeName = "one_time_invite"

    let type: String
    let version: Int
    let inviteId: String
    let scope: String?
    let realmId: String?
    let realmName: String?
    let inviterDisplayName: String
    let inviterPublicKeyEncoded: String
    let inviterFingerprint: String
    let createdAtEpochMillis: Int64
    let expiresAtEpochMillis: Int64?
    let oneTime: Bool?
    let requiresHandshake: Bool?
    let requiresApproval: Bool?
    let nonce: String?
    let capabilities: [String]?
    let cryptoProfileId: String?
    let cryptoProfileHash: String?
    let admissionDecisionHash: String?
    let profilePolicyVersion: Int?
    let nativeBackendVersion: String?
    let signature: String?

    enum CodingKeys: String, CodingKey {
        case type
        case version
        case inviteId = "invite_id"
        case scope
        case realmId = "realm_id"
        case realmName = "realm_name"
        case inviterDisplayName = "inviter_display_name"
        case inviterPublicKeyEncoded = "inviter_public_key_encoded"
        case inviterFingerprint = "inviter_fingerprint"
        case createdAtEpochMillis = "created_at_epoch_millis"
        case expiresAtEpochMillis = "expires_at_epoch_millis"
        case oneTime = "one_time"
        case requiresHandshake = "requires_handshake"
        case requiresApproval = "requires_approval"
        case nonce
        case capabilities
        case cryptoProfileId = "crypto_profile_id"
        case cryptoProfileHash = "crypto_profile_hash"
        case admissionDecisionHash = "admission_decision_hash"
        case profilePolicyVersion = "profile_policy_version"
        case nativeBackendVersion = "native_backend_version"
        case signature
    }
}
