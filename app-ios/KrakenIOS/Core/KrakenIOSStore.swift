import Combine
import Foundation

public enum KrakenIOSStoreError: Error, Equatable, Sendable {
    case localIdentityMissing
    case unsupportedHandshakePayload(KrakenHandshakePayloadKind)
    case unsupportedTransportEnvelope(String)
    case unresolvedTransportRelationship
}

@MainActor
public final class KrakenIOSStore: ObservableObject {
    @Published public private(set) var state: KrakenIOSState
    @Published public var selectedRelationshipId: String?
    @Published public private(set) var outboxRecords: [String: KrakenOutboxRecord]
    @Published public private(set) var diagnosticEvents: [String]

    private let simulator: KrakenIOSSimulator
    private let persistence: KrakenIOSPersistence?
    private var packetPolicyValidator: KrakenPacketPolicyValidator

    public init(
        simulator: KrakenIOSSimulator = KrakenIOSSimulator(),
        persistence: KrakenIOSPersistence? = nil
    ) {
        self.simulator = simulator
        self.persistence = persistence
        self.packetPolicyValidator = KrakenPacketPolicyValidator()
        if let snapshot = try? persistence?.load() {
            self.state = snapshot.state
            self.selectedRelationshipId = snapshot.selectedRelationshipId
            self.outboxRecords = Dictionary(uniqueKeysWithValues: snapshot.outboxRecords.map { ($0.messageId, $0) })
            self.diagnosticEvents = snapshot.diagnosticEvents
        } else {
            let initialState = simulator.makeInitialState()
            self.state = initialState
            self.selectedRelationshipId = nil
            self.outboxRecords = [:]
            self.diagnosticEvents = [initialState.lastEvent]
        }
    }

    public var selectedRelationship: Relationship? {
        guard let selectedRelationshipId else { return nil }
        return state.relationships.first { $0.relationshipId == selectedRelationshipId }
    }

    public var selectedMessages: [LocalMessage] {
        guard let selectedRelationshipId else { return [] }
        return state.messages.filter { $0.relationshipId == selectedRelationshipId }
    }

    public var selectedRoute: PeerRouteSnapshot? {
        guard let selectedRelationshipId else { return nil }
        return state.routes.first { $0.relationshipId == selectedRelationshipId }
    }

    public func createIdentity(displayName: String) {
        state = simulator.createIdentity(in: state, displayName: displayName)
        recordEvent(state.lastEvent)
    }

    @discardableResult
    public func importHandshakePayload(_ rawPayload: String) throws -> String? {
        let kind = KrakenHandshakeQrCodec.detectKind(rawPayload)
        switch kind {
        case .response:
            let response = try KrakenHandshakeQrCodec.decodeResponse(rawPayload)
            let result = simulator.relationshipFromResponse(response, in: state)
            state = result.state
            selectedRelationshipId = result.relationshipId
            recordEvent(state.lastEvent)
            return nil
        case .invite:
            guard let identity = state.localIdentity else {
                throw KrakenIOSStoreError.localIdentityMissing
            }
            let invite = try KrakenHandshakeQrCodec.decodeInvite(rawPayload)
            let responseId = simulator.makeIdentifier(prefix: "response")
            let result = simulator.relationshipFromInvite(
                invite,
                expectedResponseId: responseId,
                responderFingerprint: identity.fingerprint,
                in: state
            )
            state = result.state
            selectedRelationshipId = result.relationshipId
            recordEvent(state.lastEvent)
            return try makeHandshakeResponseQrPayload(for: invite, identity: identity, responseId: responseId)
        case .confirmation:
            let confirmation = try KrakenHandshakeQrCodec.decodeConfirmation(rawPayload)
            guard let result = simulator.activateRelationshipFromConfirmation(confirmation, in: state) else {
                throw KrakenIOSStoreError.unresolvedTransportRelationship
            }
            state = result.state
            selectedRelationshipId = result.relationshipId
            recordEvent(state.lastEvent)
            return nil
        case .unknown, .invalid:
            throw KrakenIOSStoreError.unsupportedHandshakePayload(kind)
        }
    }

    public func exportIdentityQrPayload() throws -> String {
        guard let identity = state.localIdentity else {
            throw KrakenIOSStoreError.localIdentityMissing
        }
        let payload = """
        {
          "type": "one_time_invite",
          "version": 1,
          "invite_id": "ios-\(identity.identityId)",
          "inviter_fingerprint": "\(identity.fingerprint)",
          "inviter_display_name": "\(identity.displayName)",
          "inviter_public_key_encoded": "\(identity.publicKeyEncoded)",
          "created_at_epoch_millis": \(Int64(identity.createdAt.timeIntervalSince1970 * 1000))
        }
        """
        return try KrakenHandshakeQrCodec.encodedQrPayload(payload)
    }

    public func importPeer(displayName: String, fingerprint: String) {
        let responseJson = """
        {
          "type": "kraken.handshake.response.v1",
          "version": 1,
          "response_id": "manual-ios-response-\(UUID().uuidString)",
          "invite_id": "manual-ios-invite",
          "responder_fingerprint": "\(fingerprint.trimmingCharacters(in: .whitespacesAndNewlines))",
          "responder_display_name": "\(displayName.trimmingCharacters(in: .whitespacesAndNewlines))",
          "responder_public_key_encoded": "manual-public-key",
          "inviter_fingerprint": "\(state.localIdentity?.fingerprint ?? "IOS-FP")",
          "created_at_epoch_millis": \(Int64(Date().timeIntervalSince1970 * 1000)),
          "crypto_profile_id": "standard-reviewed-primitives-v1",
          "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
          "profile_policy_version": 1
        }
        """
        _ = try? importHandshakePayload(responseJson)
    }

    public func selectRelationship(_ relationshipId: String) {
        selectedRelationshipId = relationshipId
        persist()
    }

    @discardableResult
    public func sendMessage(_ body: String) -> String? {
        guard let selectedRelationshipId else {
                state.lastEvent = "Сообщение не отправлено: устройство не выбрано"
            recordEvent(state.lastEvent)
            return nil
        }
        let result = simulator.sendMessage(in: state, relationshipId: selectedRelationshipId, body: body)
        state = result.state
        if let messageId = result.messageId {
            outboxRecords[messageId] = KrakenOutboxRecord(messageId: messageId)
        }
        recordEvent(state.lastEvent)
        return result.messageId
    }

    public func markTransportFailure(messageId: String, error: String) {
        guard let index = state.messages.firstIndex(where: { $0.messageId == messageId }) else { return }
        state.messages[index].status = .failed
        state.messages[index].updatedAt = simulator.currentDate()
        let existing = outboxRecords[messageId] ?? KrakenOutboxRecord(messageId: messageId)
        outboxRecords[messageId] = existing.recordFailure(error: error)
        state.lastEvent = "Ошибка локальной связи: \(error)"
        recordEvent(state.lastEvent)
    }

    public func retryMessage(messageId: String) {
        guard let index = state.messages.firstIndex(where: { $0.messageId == messageId }) else { return }
        guard state.messages[index].direction == .outgoing else { return }
        state.messages[index].status = .sentToTransport
        state.messages[index].updatedAt = simulator.currentDate()
        state.lastEvent = "Повторная отправка из очереди"
        recordEvent(state.lastEvent)
    }

    public func applyAck(messageId: String) {
        guard let index = state.messages.firstIndex(where: { $0.messageId == messageId }) else { return }
        state.messages[index].status = .deliveredToPeer
        state.messages[index].updatedAt = simulator.currentDate()
        outboxRecords.removeValue(forKey: messageId)
        state.lastEvent = "Подтверждение применено к сообщению"
        recordEvent(state.lastEvent)
    }

    @discardableResult
    public func createRealm(name: String) -> String? {
        guard state.localIdentity != nil else {
            state.lastEvent = "Реалм не создан: профиль Kraken отсутствует"
            recordEvent(state.lastEvent)
            return nil
        }
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            state.lastEvent = "Реалм не создан: пустое название"
            recordEvent(state.lastEvent)
            return nil
        }
        let current = simulator.currentDate()
        let realmId = simulator.makeIdentifier(prefix: "realm")
        state.realms.insert(
            LocalRealm(
                realmId: realmId,
                name: trimmedName,
                description: "Локальный реалм по QR-приглашениям. Публичного поиска нет.",
                localState: .active,
                capacity: 16,
                memberRelationshipIds: state.relationships
                    .filter { $0.state.isMessageCapable }
                    .map(\.relationshipId),
                createdAt: current,
                updatedAt: current
            ),
            at: 0
        )
        state.lastEvent = "Локальный реалм создан"
        recordEvent(state.lastEvent)
        return realmId
    }

    public func updateRealmState(realmId: String, state nextState: LocalRealmState) {
        guard let index = state.realms.firstIndex(where: { $0.realmId == realmId }) else { return }
        state.realms[index].localState = nextState
        state.realms[index].updatedAt = simulator.currentDate()
        state.lastEvent = "Состояние реалма изменено: \(nextState.rawValue)"
        recordEvent(state.lastEvent)
    }

    public func addRelationshipToRealm(realmId: String, relationshipId: String) {
        guard let realmIndex = state.realms.firstIndex(where: { $0.realmId == realmId }),
              state.relationships.contains(where: { $0.relationshipId == relationshipId && $0.state.isMessageCapable }) else {
            return
        }
        guard state.realms[realmIndex].memberRelationshipIds.contains(relationshipId) == false,
              state.realms[realmIndex].hasCapacity else {
            return
        }
        state.realms[realmIndex].memberRelationshipIds.append(relationshipId)
        state.realms[realmIndex].updatedAt = simulator.currentDate()
        state.lastEvent = "Участник добавлен в реалм"
        recordEvent(state.lastEvent)
    }

    public func removeRelationshipFromRealm(realmId: String, relationshipId: String) {
        guard let realmIndex = state.realms.firstIndex(where: { $0.realmId == realmId }) else { return }
        state.realms[realmIndex].memberRelationshipIds.removeAll { $0 == relationshipId }
        state.realms[realmIndex].updatedAt = simulator.currentDate()
        state.lastEvent = "Участник удалён из реалма"
        recordEvent(state.lastEvent)
    }

    public func receiveTransportEnvelope(_ data: Data, fromPeer peerDisplayName: String) {
        do {
            let envelope = try JSONDecoder().decode(IOSNearbyEnvelope.self, from: data)
            switch envelope.type {
            case "kraken.ios.packet.v1":
                try receivePacketEnvelope(envelope, fromPeer: peerDisplayName)
            case "kraken.ios.ack.v1":
                try receiveAckEnvelope(envelope, fromPeer: peerDisplayName)
                recordEvent("Подтверждение локальной связи получено от \(peerDisplayName)")
            default:
                throw KrakenIOSStoreError.unsupportedTransportEnvelope(envelope.type)
            }
        } catch {
            state.lastEvent = "Пакет локальной связи отклонён: \(error)"
            recordEvent(state.lastEvent)
        }
    }

    public func recordDiagnosticEvent(_ event: String) {
        recordEvent(event)
    }

    public func handleDeepLink(_ action: KrakenDeepLinkAction) {
        switch action {
        case .importQrPayload(let payload):
            do {
                try importHandshakePayload(payload)
                recordEvent("Deep link QR import выполнен")
            } catch {
                state.lastEvent = "Deep link QR import отклонён: \(error)"
                recordEvent(state.lastEvent)
            }
        case .unsupported:
            state.lastEvent = "Deep link не поддержан"
            recordEvent(state.lastEvent)
        }
    }

    public func exportEvidenceJson() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        let snapshot = KrakenIOSEvidenceSnapshot(
            state: state,
            outboxRecords: outboxRecords.values.sorted { $0.messageId < $1.messageId },
            diagnosticEvents: diagnosticEvents,
            transportBoundary: IOSNearbyTransportDescriptor().boundaryNote
        )
        guard let data = try? encoder.encode(snapshot),
              let json = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return json
    }

    private func recordEvent(_ event: String) {
        diagnosticEvents.insert(event, at: 0)
        if diagnosticEvents.count > 50 {
            diagnosticEvents.removeLast(diagnosticEvents.count - 50)
        }
        persist()
    }

    private func persist() {
        guard let persistence else { return }
        let snapshot = KrakenIOSStoreSnapshot(
            state: state,
            selectedRelationshipId: selectedRelationshipId,
            outboxRecords: outboxRecords.values.sorted { $0.messageId < $1.messageId },
            diagnosticEvents: diagnosticEvents
        )
        try? persistence.save(snapshot)
    }

    private func makeHandshakeResponseQrPayload(
        for invite: KrakenHandshakeInvitePayload,
        identity: LocalIdentity,
        responseId: String
    ) throws -> String {
        var object: [String: Any] = [
            "type": KrakenHandshakeQrCodec.responseTypeName,
            "version": 1,
            "response_id": responseId,
            "invite_id": invite.inviteId,
            "responder_fingerprint": identity.fingerprint,
            "responder_display_name": identity.displayName,
            "responder_public_key_encoded": identity.publicKeyEncoded,
            "inviter_fingerprint": invite.inviterFingerprint,
            "created_at_epoch_millis": Int64(simulator.currentDate().timeIntervalSince1970 * 1000),
            "crypto_profile_id": invite.cryptoProfileId ?? "standard-reviewed-primitives-v1",
            "admission_decision_hash": invite.admissionDecisionHash ?? "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
            "profile_policy_version": invite.profilePolicyVersion ?? 1,
            "proof_placeholder": "prototype-offline-qr-handshake-not-production-crypto",
        ]
        object["realm_id"] = invite.realmId
        object["requires_approval"] = invite.requiresApproval
        object["crypto_profile_hash"] = invite.cryptoProfileHash
        object["native_backend_version"] = invite.nativeBackendVersion

        let data = try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
        let json = String(decoding: data, as: UTF8.self)
        return try KrakenHandshakeQrCodec.encodedQrPayload(json)
    }

    private func receivePacketEnvelope(_ envelope: IOSNearbyEnvelope, fromPeer peerDisplayName: String) throws {
        guard let packet = envelope.packet else {
            throw KrakenIOSStoreError.unsupportedTransportEnvelope(envelope.type)
        }
        try packetPolicyValidator.acceptInbound(packet, nowMillis: Int64(simulator.currentDate().timeIntervalSince1970 * 1000))
        guard let body = bodyFromPacket(packet),
              !body.isEmpty,
              let messageId = packet.messageId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !messageId.isEmpty else {
            throw KrakenIOSStoreError.unsupportedTransportEnvelope(envelope.type)
        }
        guard state.messages.contains(where: { $0.messageId == messageId }) == false else {
            state.lastEvent = "Дубликат сообщения локальной связи проигнорирован"
            recordEvent(state.lastEvent)
            return
        }
        guard let relationship = resolveRelationship(for: packet) else {
            throw KrakenIOSStoreError.unresolvedTransportRelationship
        }
        let current = simulator.currentDate()
        state.messages.append(
            LocalMessage(
                messageId: messageId,
                relationshipId: relationship.relationshipId,
                peerFingerprint: relationship.peerFingerprint,
                direction: .incoming,
                status: .deliveredToPeer,
                body: body,
                createdAt: current,
                updatedAt: current
            )
        )
        selectedRelationshipId = relationship.relationshipId
        state.lastEvent = "Сообщение локальной связи получено от \(peerDisplayName)"
        recordEvent(state.lastEvent)
    }

    private func receiveAckEnvelope(_ envelope: IOSNearbyEnvelope, fromPeer _: String) throws {
        guard let messageId = envelope.messageId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !messageId.isEmpty else {
            throw KrakenIOSStoreError.unsupportedTransportEnvelope(envelope.type)
        }
        guard let relationship = resolveRelationship(for: envelope) else {
            throw KrakenIOSStoreError.unresolvedTransportRelationship
        }
        guard let message = state.messages.first(where: { $0.messageId == messageId }),
              message.direction == .outgoing,
              message.relationshipId == relationship.relationshipId,
              message.peerFingerprint == relationship.peerFingerprint else {
            throw KrakenIOSStoreError.unresolvedTransportRelationship
        }
        applyAck(messageId: messageId)
    }

    private func resolveRelationship(for envelope: IOSNearbyEnvelope) -> Relationship? {
        guard let relationshipId = envelope.relationshipId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !relationshipId.isEmpty,
              let peerFingerprint = envelope.peerFingerprint?.trimmingCharacters(in: .whitespacesAndNewlines),
              !peerFingerprint.isEmpty else {
            return nil
        }
        return state.relationships.first {
            $0.relationshipId == relationshipId
                && $0.peerFingerprint == peerFingerprint
                && $0.state.isMessageCapable
        }
    }

    private func resolveRelationship(for packet: KrakenPacket) -> Relationship? {
        guard state.localIdentity?.fingerprint == packet.recipientFingerprint else {
            return nil
        }
        return state.relationships.first {
            $0.relationshipId == packet.relationshipId
                && $0.peerFingerprint == packet.senderFingerprint
                && $0.state.isMessageCapable
        }
    }

    private func bodyFromPacket(_ packet: KrakenPacket) -> String? {
        guard packet.packetType == "MESSAGE",
              packet.payloadType == "LOCAL_MESSAGE_JSON",
              let data = packet.payloadJson.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let body = object["body"] as? String else {
            return nil
        }
        return body.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

public struct KrakenIOSEvidenceSnapshot: Codable, Equatable, Sendable {
    public var state: KrakenIOSState
    public var outboxRecords: [KrakenOutboxRecord]
    public var diagnosticEvents: [String]
    public var transportBoundary: String
}

private struct IOSNearbyEnvelope: Decodable {
    var type: String
    var messageId: String?
    var body: String?
    var relationshipId: String?
    var peerFingerprint: String?
    var packet: KrakenPacket?

    private enum CodingKeys: String, CodingKey {
        case type
        case messageId = "message_id"
        case body
        case relationshipId = "relationship_id"
        case peerFingerprint = "peer_fingerprint"
        case packet
    }
}
