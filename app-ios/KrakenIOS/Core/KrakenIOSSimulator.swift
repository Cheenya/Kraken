import Foundation

public struct KrakenIOSSimulator: Sendable {
    private let now: @Sendable () -> Date
    private let makeId: @Sendable () -> String

    public init(
        now: @escaping @Sendable () -> Date = { Date() },
        makeId: @escaping @Sendable () -> String = { UUID().uuidString }
    ) {
        self.now = now
        self.makeId = makeId
    }

    public func makeInitialState() -> KrakenIOSState {
        let current = now()
        return KrakenIOSState(
            localIdentity: nil,
            relationships: [],
            messages: [],
            routes: [],
            admissionResult: AdmissionResult(
                profileId: "standard-reviewed-primitives-v1",
                decision: .notApplicableStandardProfile,
                decisionHash: "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
                nativeBackendVersion: "not-applicable-standard-profile",
                riskFlags: [],
                evaluatedAt: current
            ),
            lastEvent: "Kraken инициализирован"
        )
    }

    public func currentDate() -> Date {
        now()
    }

    public func makeIdentifier(prefix: String) -> String {
        "\(prefix)-\(makeId())"
    }

    public func createIdentity(in state: KrakenIOSState, displayName: String) -> KrakenIOSState {
        var next = state
        let normalizedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        next.localIdentity = LocalIdentity(
            identityId: "identity-\(makeId())",
            displayName: normalizedName.isEmpty ? "Kraken" : normalizedName,
            publicKeyEncoded: "kraken-public-key-\(makeId().prefix(12))",
            privateKeyReference: "kraken-keychain-placeholder",
            fingerprint: normalizedFingerprint(from: makeId()),
            createdAt: now()
        )
        next.lastEvent = "Создан профиль Kraken"
        return next
    }

    public func relationshipFromResponse(
        _ response: KrakenHandshakeResponsePayload,
        in state: KrakenIOSState
    ) -> (state: KrakenIOSState, relationshipId: String) {
        var next = state
        let current = now()
        let relationshipId = "rel-\(makeId())"
        let relationship = Relationship(
            relationshipId: relationshipId,
            peerDisplayName: response.responderDisplayName,
            peerFingerprint: response.responderFingerprint,
            state: .active,
            cryptoProfileId: response.cryptoProfileId ?? "standard-reviewed-primitives-v1",
            admissionDecisionHash: response.admissionDecisionHash ?? "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
            profilePolicyVersion: response.profilePolicyVersion ?? 1,
            updatedAt: current
        )
        next.relationships.insert(relationship, at: 0)
        next.routes.insert(
            PeerRouteSnapshot(
                relationshipId: relationshipId,
                peerFingerprint: response.responderFingerprint,
                kind: .appleNearby,
                transportId: IOSNearbyTransportDescriptor().transportId,
                bandwidthClass: .medium,
                hopCount: 1,
                lastSeenAt: current
            ),
            at: 0
        )
        next.lastEvent = "QR-рукопожатие импортировано"
        return (next, relationshipId)
    }

    public func relationshipFromInvite(
        _ invite: KrakenHandshakeInvitePayload,
        expectedResponseId: String,
        responderFingerprint: String,
        in state: KrakenIOSState
    ) -> (state: KrakenIOSState, relationshipId: String) {
        var next = state
        let current = now()
        let relationshipId = "rel-\(makeId())"
        let relationship = Relationship(
            relationshipId: relationshipId,
            peerDisplayName: invite.inviterDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
                ? invite.inviterDisplayName!
                : "Устройство Kraken",
            peerFingerprint: invite.inviterFingerprint,
            state: .pendingHandshake,
            cryptoProfileId: invite.cryptoProfileId ?? "standard-reviewed-primitives-v1",
            admissionDecisionHash: invite.admissionDecisionHash ?? "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
            profilePolicyVersion: invite.profilePolicyVersion ?? 1,
            pendingInviteId: invite.inviteId,
            pendingResponseId: expectedResponseId,
            pendingResponderFingerprint: responderFingerprint,
            updatedAt: current
        )
        next.relationships.insert(relationship, at: 0)
        next.lastEvent = "QR-приглашение импортировано, нужен ответ"
        return (next, relationshipId)
    }

    public func activateRelationshipFromConfirmation(
        _ confirmation: KrakenHandshakeConfirmationPayload,
        in state: KrakenIOSState
    ) -> (state: KrakenIOSState, relationshipId: String)? {
        guard let index = state.relationships.firstIndex(where: {
            $0.peerFingerprint == confirmation.inviterFingerprint
                && $0.state == .pendingHandshake
                && $0.pendingInviteId == confirmation.inviteId
                && $0.pendingResponseId == confirmation.responseId
                && $0.pendingResponderFingerprint == confirmation.responderFingerprint
        }) else {
            return nil
        }
        var next = state
        let current = now()
        next.relationships[index].state = .active
        next.relationships[index].pendingInviteId = nil
        next.relationships[index].pendingResponseId = nil
        next.relationships[index].pendingResponderFingerprint = nil
        next.relationships[index].cryptoProfileId = confirmation.cryptoProfileId ?? next.relationships[index].cryptoProfileId
        next.relationships[index].admissionDecisionHash = confirmation.admissionDecisionHash ?? next.relationships[index].admissionDecisionHash
        next.relationships[index].profilePolicyVersion = confirmation.profilePolicyVersion ?? next.relationships[index].profilePolicyVersion
        next.relationships[index].updatedAt = current
        next.routes.insert(
            PeerRouteSnapshot(
                relationshipId: next.relationships[index].relationshipId,
                peerFingerprint: next.relationships[index].peerFingerprint,
                kind: .appleNearby,
                transportId: IOSNearbyTransportDescriptor().transportId,
                bandwidthClass: .medium,
                hopCount: 1,
                lastSeenAt: current
            ),
            at: 0
        )
        next.lastEvent = "QR-подтверждение импортировано"
        return (next, next.relationships[index].relationshipId)
    }

    public func sendMessage(
        in state: KrakenIOSState,
        relationshipId: String,
        body: String
    ) -> (state: KrakenIOSState, messageId: String?) {
        guard let relationship = state.relationships.first(where: { $0.relationshipId == relationshipId }) else {
            var failed = state
            failed.lastEvent = "Сообщение не отправлено: устройство не найдено"
            return (failed, nil)
        }
        guard relationship.state.isMessageCapable else {
            var failed = state
            failed.lastEvent = "Сообщение не отправлено: связь не активна"
            return (failed, nil)
        }
        let trimmedBody = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedBody.isEmpty else {
            var failed = state
            failed.lastEvent = "Сообщение не отправлено: пустой текст"
            return (failed, nil)
        }
        var next = state
        let current = now()
        let messageId = "msg-\(makeId())"
        let route = state.routes.first(where: { $0.relationshipId == relationshipId })
        let status: MessageStatus = (route?.kind ?? PeerRouteKind.none) == PeerRouteKind.none
            ? .readyForTransport
            : .sentToTransport
        next.messages.append(
            LocalMessage(
                messageId: messageId,
                relationshipId: relationship.relationshipId,
                peerFingerprint: relationship.peerFingerprint,
                direction: .outgoing,
                status: status,
                body: trimmedBody,
                createdAt: current,
                updatedAt: current
            )
        )
        next.lastEvent = status == .sentToTransport
            ? "Сообщение передано через локальную связь"
            : "Сообщение ожидает маршрут"
        return (next, messageId)
    }

    private func normalizedFingerprint(from rawId: String) -> String {
        let compact = rawId
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: "_", with: "")
            .uppercased()
        return String(compact.prefix(16))
    }
}
