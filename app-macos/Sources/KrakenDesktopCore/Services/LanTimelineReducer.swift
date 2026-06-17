import Foundation

public enum LanTimelineReducer {
    public struct ApplyResult: Sendable {
        public var state: KrakenDesktopState
        public var selectedRelationshipId: String?

        public init(state: KrakenDesktopState, selectedRelationshipId: String?) {
            self.state = state
            self.selectedRelationshipId = selectedRelationshipId
        }
    }

    public static func apply(
        event: MacLanTransferEvent,
        to state: KrakenDesktopState,
        now: Date = Date()
    ) -> ApplyResult {
        var next = state
        var selectedRelationshipId: String?

        switch event.direction {
        case .outbound:
            applyOutbound(event: event, to: &next, now: now)
        case .inbound:
            selectedRelationshipId = applyInbound(event: event, to: &next, now: now)
        }

        refreshRoute(event: event, in: &next, now: now)
        return ApplyResult(state: next, selectedRelationshipId: selectedRelationshipId)
    }

    private static func applyOutbound(
        event: MacLanTransferEvent,
        to state: inout KrakenDesktopState,
        now: Date
    ) {
        guard let messageId = event.messageId else { return }
        guard let index = state.messages.firstIndex(where: { $0.messageId == messageId }) else { return }

        state.messages[index].status = event.status == .acked ? .deliveredToPeer : .failed
        state.messages[index].updatedAt = now
    }

    private static func applyInbound(
        event: MacLanTransferEvent,
        to state: inout KrakenDesktopState,
        now: Date
    ) -> String? {
        guard event.status == .accepted else { return nil }
        guard let messageId = event.messageId else { return nil }
        guard state.messages.allSatisfy({ $0.messageId != messageId }) else {
            return nil
        }
        guard let relationship = ensureRelationship(for: event, in: &state, now: now) else {
            return nil
        }

        state.messages.append(
            LocalMessage(
                messageId: messageId,
                relationshipId: relationship.relationshipId,
                peerFingerprint: relationship.peerFingerprint,
                direction: .incoming,
                status: .deliveredToPeer,
                body: messageBody(fromPayloadJson: event.payloadJson),
                createdAt: now,
                updatedAt: now
            )
        )
        return relationship.relationshipId
    }

    private static func ensureRelationship(
        for event: MacLanTransferEvent,
        in state: inout KrakenDesktopState,
        now: Date
    ) -> Relationship? {
        if let relationship = findRelationship(for: event, in: state) {
            return relationship
        }
        guard let senderFingerprint = event.senderFingerprint, !senderFingerprint.isEmpty else {
            return nil
        }

        let relationship = Relationship(
            relationshipId: event.relationshipId ?? "rel-lan-\(senderFingerprint)",
            peerDisplayName: event.senderDisplayName ?? "LAN-устройство",
            peerFingerprint: senderFingerprint,
            state: .active,
            cryptoProfileId: "standard-reviewed-primitives-v1",
            admissionDecisionHash: "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
            updatedAt: now
        )
        state.relationships.insert(relationship, at: 0)
        state.routes.insert(
            PeerRouteSnapshot(
                relationshipId: relationship.relationshipId,
                peerFingerprint: relationship.peerFingerprint,
                kind: .directLan,
                transportId: "macos-lan-tcp",
                bandwidthClass: .high,
                hopCount: 1,
                lastSeenAt: now
            ),
            at: 0
        )
        return relationship
    }

    private static func refreshRoute(
        event: MacLanTransferEvent,
        in state: inout KrakenDesktopState,
        now: Date
    ) {
        guard event.status != .failed else { return }
        guard let relationship = findRelationship(for: event, in: state) else { return }

        let route = PeerRouteSnapshot(
            relationshipId: relationship.relationshipId,
            peerFingerprint: relationship.peerFingerprint,
            kind: .directLan,
            transportId: "macos-lan-tcp",
            bandwidthClass: .high,
            hopCount: 1,
            lastSeenAt: now
        )
        if let index = state.routes.firstIndex(where: { $0.relationshipId == relationship.relationshipId }) {
            state.routes[index] = route
        } else {
            state.routes.insert(route, at: 0)
        }
    }

    private static func findRelationship(
        for event: MacLanTransferEvent,
        in state: KrakenDesktopState
    ) -> Relationship? {
        if let relationshipId = event.relationshipId,
           let relationship = state.relationships.first(where: { $0.relationshipId == relationshipId }) {
            return relationship
        }

        let fingerprints = [
            event.senderFingerprint,
            event.recipientFingerprint,
        ].compactMap { $0 }

        return state.relationships.first { relationship in
            fingerprints.contains(relationship.peerFingerprint)
        }
    }

    public static func messageBody(fromPayloadJson payloadJson: String?) -> String {
        guard
            let payloadJson,
            let data = payloadJson.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return payloadJson ?? ""
        }
        if let body = object["body"] as? String {
            return body
        }
        if let text = object["text"] as? String {
            return text
        }
        return payloadJson
    }
}
