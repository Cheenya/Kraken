import XCTest
@testable import KrakenIOS

@MainActor
final class KrakenIOSStoreTests: XCTestCase {
    func testQrImportMessageAckAndOutboxFailureFlow() throws {
        let ids = DeterministicIDSequence(["identity", "public", "fingerprint-local", "relationship", "message"])
        let simulator = KrakenIOSSimulator(
            now: { Date(timeIntervalSince1970: 1_800_000_000) },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)

        store.createIdentity(displayName: "iPhone Kraken")
        try store.importHandshakePayload(Self.androidHandshakeResponse)

        let relationship = try XCTUnwrap(store.state.relationships.first)
        XCTAssertEqual(relationship.peerDisplayName, "Android Xiaomi")
        XCTAssertEqual(relationship.peerFingerprint, "ANDROID-FP")
        XCTAssertEqual(relationship.state, .active)
        XCTAssertEqual(store.selectedRelationshipId, relationship.relationshipId)

        store.sendMessage("Привет с iOS")
        let outgoing = try XCTUnwrap(store.state.messages.last)
        XCTAssertEqual(outgoing.messageId, "msg-message")
        XCTAssertEqual(outgoing.status, .sentToTransport)
        XCTAssertEqual(store.outboxRecords[outgoing.messageId]?.attempts, 0)

        store.markTransportFailure(messageId: outgoing.messageId, error: "peer-unavailable")
        XCTAssertEqual(store.state.messages.last?.status, .failed)
        XCTAssertEqual(store.outboxRecords[outgoing.messageId]?.messageId, outgoing.messageId)
        XCTAssertEqual(store.outboxRecords[outgoing.messageId]?.nextRetryDelay, 2)

        store.retryMessage(messageId: outgoing.messageId)
        XCTAssertEqual(store.state.messages.last?.messageId, outgoing.messageId)
        XCTAssertEqual(store.state.messages.last?.status, .sentToTransport)

        store.applyAck(messageId: outgoing.messageId)
        XCTAssertEqual(store.state.messages.last?.messageId, outgoing.messageId)
        XCTAssertEqual(store.state.messages.last?.status, .deliveredToPeer)
        XCTAssertNil(store.outboxRecords[outgoing.messageId])
    }

    func testReceiveTransportEnvelopeAddsIncomingMessageAndAppliesAck() throws {
        let ids = DeterministicIDSequence(["identity", "public", "fingerprint-local", "relationship", "outgoing"])
        let simulator = KrakenIOSSimulator(
            now: { Date(timeIntervalSince1970: 1_800_000_000) },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)

        store.createIdentity(displayName: "iPhone Kraken")
        try store.importHandshakePayload(Self.androidHandshakeResponse)
        let relationship = try XCTUnwrap(store.state.relationships.first)

        let localIdentity = try XCTUnwrap(store.state.localIdentity)
        let incoming = try Self.packetEnvelopeData(
            packetId: "packet-android-message-1",
            messageId: "android-message-1",
            relationshipId: relationship.relationshipId,
            senderFingerprint: relationship.peerFingerprint,
            recipientFingerprint: localIdentity.fingerprint,
            body: "Ответ Android"
        )
        store.receiveTransportEnvelope(incoming, fromPeer: "Android Xiaomi")

        let received = try XCTUnwrap(store.state.messages.last)
        XCTAssertEqual(received.messageId, "android-message-1")
        XCTAssertEqual(received.direction, .incoming)
        XCTAssertEqual(received.status, .deliveredToPeer)
        XCTAssertEqual(received.relationshipId, relationship.relationshipId)
        XCTAssertEqual(store.selectedRelationshipId, relationship.relationshipId)

        let outgoingId = try XCTUnwrap(store.sendMessage("Проверка ACK"))
        XCTAssertNotNil(store.outboxRecords[outgoingId])
        let ack = """
        {
          "type": "kraken.ios.ack.v1",
          "message_id": "\(outgoingId)",
          "relationship_id": "\(relationship.relationshipId)",
          "peer_fingerprint": "\(relationship.peerFingerprint)"
        }
        """.data(using: .utf8)!
        store.receiveTransportEnvelope(ack, fromPeer: "Android Xiaomi")

        XCTAssertEqual(store.state.messages.last?.messageId, outgoingId)
        XCTAssertEqual(store.state.messages.last?.status, .deliveredToPeer)
        XCTAssertNil(store.outboxRecords[outgoingId])
    }

    func testImportAndroidInviteGeneratesResponseQrAndConfirmationActivatesRelationship() throws {
        let ids = DeterministicIDSequence(["identity", "public", "fingerprint-local", "relationship", "response"])
        let simulator = KrakenIOSSimulator(
            now: { Date(timeIntervalSince1970: 1_800_000_000) },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)

        store.createIdentity(displayName: "iPhone Kraken")
        let responseQrPayload = try XCTUnwrap(store.importHandshakePayload(Self.androidHandshakeInvite))
        let relationship = try XCTUnwrap(store.state.relationships.first)
        let localIdentity = try XCTUnwrap(store.state.localIdentity)

        XCTAssertEqual(relationship.peerDisplayName, "Android Xiaomi")
        XCTAssertEqual(relationship.peerFingerprint, "ANDROID-FP")
        XCTAssertEqual(relationship.state, .pendingHandshake)
        XCTAssertEqual(relationship.pendingInviteId, "invite-android")
        XCTAssertEqual(store.selectedRelationshipId, relationship.relationshipId)
        XCTAssertEqual(KrakenHandshakeQrCodec.detectKind(responseQrPayload), .response)

        let response = try KrakenHandshakeQrCodec.decodeResponse(responseQrPayload)
        XCTAssertEqual(response.inviteId, "invite-android")
        XCTAssertEqual(response.responderFingerprint, localIdentity.fingerprint)
        XCTAssertEqual(response.responderDisplayName, "iPhone Kraken")
        XCTAssertEqual(response.inviterFingerprint, "ANDROID-FP")
        XCTAssertEqual(response.cryptoProfileId, "standard-reviewed-primitives-v1")

        let confirmation = """
        {
          "type": "kraken.handshake.confirmation.v1",
          "version": 1,
          "confirmation_id": "confirmation-android",
          "response_id": "\(response.responseId)",
          "invite_id": "invite-android",
          "inviter_fingerprint": "ANDROID-FP",
          "responder_fingerprint": "\(localIdentity.fingerprint)",
          "created_at_epoch_millis": 1800000001000,
          "crypto_profile_id": "standard-reviewed-primitives-v1",
          "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
          "profile_policy_version": 1
        }
        """

        XCTAssertNil(try store.importHandshakePayload(confirmation))
        XCTAssertEqual(store.state.relationships.first?.relationshipId, relationship.relationshipId)
        XCTAssertEqual(store.state.relationships.first?.state, .active)
        XCTAssertNil(store.state.relationships.first?.pendingInviteId)
        XCTAssertNil(store.state.relationships.first?.pendingResponseId)
        XCTAssertNil(store.state.relationships.first?.pendingResponderFingerprint)
        XCTAssertEqual(store.selectedRoute?.peerFingerprint, "ANDROID-FP")
    }

    func testConfirmationRequiresPendingInviteResponseAndResponderBinding() throws {
        let ids = DeterministicIDSequence(["identity", "public", "fingerprint-local", "response", "relationship"])
        let simulator = KrakenIOSSimulator(
            now: { Date(timeIntervalSince1970: 1_800_000_000) },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)

        store.createIdentity(displayName: "iPhone Kraken")
        let responseQrPayload = try XCTUnwrap(store.importHandshakePayload(Self.androidHandshakeInvite))
        let response = try KrakenHandshakeQrCodec.decodeResponse(responseQrPayload)
        let localIdentity = try XCTUnwrap(store.state.localIdentity)
        let relationshipId = try XCTUnwrap(store.state.relationships.first?.relationshipId)

        let wrongResponseId = Self.confirmationPayload(
            responseId: "wrong-response-id",
            inviteId: "invite-android",
            inviterFingerprint: "ANDROID-FP",
            responderFingerprint: localIdentity.fingerprint
        )
        XCTAssertThrowsError(try store.importHandshakePayload(wrongResponseId))
        XCTAssertEqual(store.state.relationships.first?.relationshipId, relationshipId)
        XCTAssertEqual(store.state.relationships.first?.state, .pendingHandshake)
        XCTAssertNil(store.selectedRoute)

        let wrongResponder = Self.confirmationPayload(
            responseId: response.responseId,
            inviteId: "invite-android",
            inviterFingerprint: "ANDROID-FP",
            responderFingerprint: "OTHER-IOS-FP"
        )
        XCTAssertThrowsError(try store.importHandshakePayload(wrongResponder))
        XCTAssertEqual(store.state.relationships.first?.state, .pendingHandshake)
        XCTAssertNil(store.selectedRoute)

        let validConfirmation = Self.confirmationPayload(
            responseId: response.responseId,
            inviteId: "invite-android",
            inviterFingerprint: "ANDROID-FP",
            responderFingerprint: localIdentity.fingerprint
        )
        XCTAssertNil(try store.importHandshakePayload(validConfirmation))
        XCTAssertEqual(store.state.relationships.first?.state, .active)
        XCTAssertEqual(store.selectedRoute?.relationshipId, relationshipId)
    }

    func testReceiveTransportEnvelopeRequiresBoundRelationshipAndFingerprint() throws {
        let ids = DeterministicIDSequence(["identity", "public", "fingerprint-local", "relationship"])
        let simulator = KrakenIOSSimulator(
            now: { Date(timeIntervalSince1970: 1_800_000_000) },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)

        store.createIdentity(displayName: "iPhone Kraken")
        try store.importHandshakePayload(Self.androidHandshakeResponse)
        let relationship = try XCTUnwrap(store.state.relationships.first)
        let localIdentity = try XCTUnwrap(store.state.localIdentity)

        let wrongSender = try Self.packetEnvelopeData(
            packetId: "packet-wrong-sender",
            messageId: "android-message-wrong-sender",
            relationshipId: relationship.relationshipId,
            senderFingerprint: "WRONG-FP",
            recipientFingerprint: localIdentity.fingerprint,
            body: "Чужой отпечаток"
        )
        store.receiveTransportEnvelope(wrongSender, fromPeer: "Android Xiaomi")
        XCTAssertTrue(store.state.messages.isEmpty)
        XCTAssertTrue(store.state.lastEvent.contains("unresolvedTransportRelationship"))

        let wrongRecipient = try Self.packetEnvelopeData(
            packetId: "packet-wrong-recipient",
            messageId: "android-message-wrong-recipient",
            relationshipId: relationship.relationshipId,
            senderFingerprint: relationship.peerFingerprint,
            recipientFingerprint: "OTHER-IOS-FP",
            body: "Чужой получатель"
        )
        store.receiveTransportEnvelope(wrongRecipient, fromPeer: "Android Xiaomi")
        XCTAssertTrue(store.state.messages.isEmpty)
        XCTAssertTrue(store.state.lastEvent.contains("unresolvedTransportRelationship"))
    }

    func testReceiveTransportPacketAppliesPolicyBeforeTimelineMutation() throws {
        let ids = DeterministicIDSequence(["identity", "public", "fingerprint-local", "relationship"])
        let simulator = KrakenIOSSimulator(
            now: { Date(timeIntervalSince1970: 1_800_000_000) },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)

        store.createIdentity(displayName: "iPhone Kraken")
        try store.importHandshakePayload(Self.androidHandshakeResponse)
        let relationship = try XCTUnwrap(store.state.relationships.first)
        let localIdentity = try XCTUnwrap(store.state.localIdentity)
        let duplicate = try Self.packetEnvelopeData(
            packetId: "packet-duplicate",
            messageId: "android-message-duplicate",
            relationshipId: relationship.relationshipId,
            senderFingerprint: relationship.peerFingerprint,
            recipientFingerprint: localIdentity.fingerprint,
            body: "Один раз"
        )

        store.receiveTransportEnvelope(duplicate, fromPeer: "Android Xiaomi")
        store.receiveTransportEnvelope(duplicate, fromPeer: "Android Xiaomi")

        XCTAssertEqual(store.state.messages.count, 1)
        XCTAssertTrue(store.state.lastEvent.contains("packet-policy-duplicate:packet-duplicate"))
    }

    func testTransportAckRequiresBoundOutgoingRelationship() throws {
        let ids = DeterministicIDSequence(["identity", "public", "fingerprint-local", "relationship", "outgoing"])
        let simulator = KrakenIOSSimulator(
            now: { Date(timeIntervalSince1970: 1_800_000_000) },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)

        store.createIdentity(displayName: "iPhone Kraken")
        try store.importHandshakePayload(Self.androidHandshakeResponse)
        let relationship = try XCTUnwrap(store.state.relationships.first)
        let outgoingId = try XCTUnwrap(store.sendMessage("Проверка ACK"))

        let missingBinding = """
        {
          "type": "kraken.ios.ack.v1",
          "message_id": "\(outgoingId)"
        }
        """.data(using: .utf8)!
        store.receiveTransportEnvelope(missingBinding, fromPeer: "Android Xiaomi")
        XCTAssertEqual(store.state.messages.last?.status, .sentToTransport)
        XCTAssertNotNil(store.outboxRecords[outgoingId])
        XCTAssertTrue(store.state.lastEvent.contains("unresolvedTransportRelationship"))

        let wrongFingerprint = """
        {
          "type": "kraken.ios.ack.v1",
          "message_id": "\(outgoingId)",
          "relationship_id": "\(relationship.relationshipId)",
          "peer_fingerprint": "WRONG-FP"
        }
        """.data(using: .utf8)!
        store.receiveTransportEnvelope(wrongFingerprint, fromPeer: "Android Xiaomi")
        XCTAssertEqual(store.state.messages.last?.status, .sentToTransport)
        XCTAssertNotNil(store.outboxRecords[outgoingId])
        XCTAssertTrue(store.state.lastEvent.contains("unresolvedTransportRelationship"))
    }

    func testReceiveTransportEnvelopeRejectsUnresolvedRelationship() {
        let store = KrakenIOSStore()
        let incoming = try! Self.packetEnvelopeData(
            packetId: "packet-unknown",
            messageId: "unknown-message",
            relationshipId: "unknown-relationship",
            senderFingerprint: "UNKNOWN-FP",
            recipientFingerprint: "IOS-FP",
            body: "Недоверенное сообщение"
        )

        store.receiveTransportEnvelope(incoming, fromPeer: "Unknown")

        XCTAssertTrue(store.state.messages.isEmpty)
        XCTAssertTrue(store.state.lastEvent.contains("unresolvedTransportRelationship"))
    }

    func testRealmCreateMembershipAndLocalLifecycleFlow() throws {
        let ids = DeterministicIDSequence(["identity", "public", "fingerprint-local", "relationship", "realm"])
        let simulator = KrakenIOSSimulator(
            now: { Date(timeIntervalSince1970: 1_800_000_000) },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)

        XCTAssertNil(store.createRealm(name: "До профиля"))
        store.createIdentity(displayName: "iPhone Kraken")
        try store.importHandshakePayload(Self.androidHandshakeResponse)
        let relationship = try XCTUnwrap(store.state.relationships.first)

        let realmId = try XCTUnwrap(store.createRealm(name: "Локальный круг"))
        let realm = try XCTUnwrap(store.state.realms.first)
        XCTAssertEqual(realm.realmId, realmId)
        XCTAssertEqual(realm.name, "Локальный круг")
        XCTAssertEqual(realm.localState, .active)
        XCTAssertEqual(realm.memberRelationshipIds, [relationship.relationshipId])
        XCTAssertTrue(store.exportEvidenceJson().contains("Локальный круг"))

        store.removeRelationshipFromRealm(realmId: realmId, relationshipId: relationship.relationshipId)
        XCTAssertEqual(store.state.realms.first?.memberRelationshipIds, [])

        store.addRelationshipToRealm(realmId: realmId, relationshipId: relationship.relationshipId)
        XCTAssertEqual(store.state.realms.first?.memberRelationshipIds, [relationship.relationshipId])

        store.updateRealmState(realmId: realmId, state: .paused)
        XCTAssertEqual(store.state.realms.first?.localState, .paused)
        store.updateRealmState(realmId: realmId, state: .archived)
        XCTAssertEqual(store.state.realms.first?.localState, .archived)
    }

    private static let androidHandshakeResponse = """
    {
      "type": "kraken.handshake.response.v1",
      "version": 1,
      "response_id": "response-android",
      "invite_id": "invite-ios",
      "responder_fingerprint": "ANDROID-FP",
      "responder_display_name": "Android Xiaomi",
      "responder_public_key_encoded": "android-public-key",
      "inviter_fingerprint": "IOS-FP",
      "created_at_epoch_millis": 1700000000000,
      "crypto_profile_id": "standard-reviewed-primitives-v1",
      "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
      "profile_policy_version": 1
    }
    """

    private static let androidHandshakeInvite = """
    {
      "type": "one_time_invite",
      "version": 1,
      "invite_id": "invite-android",
      "scope": "DIRECT_CONTACT",
      "inviter_display_name": "Android Xiaomi",
      "inviter_fingerprint": "ANDROID-FP",
      "inviter_public_key_encoded": "android-public-key",
      "created_at_epoch_millis": 1700000000000,
      "expires_at_epoch_millis": null,
      "one_time": true,
      "requires_handshake": true,
      "requires_approval": false,
      "capabilities": ["kraken.invite.v1"],
      "crypto_profile_id": "standard-reviewed-primitives-v1",
      "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
      "profile_policy_version": 1
    }
    """

    private static func confirmationPayload(
        responseId: String,
        inviteId: String,
        inviterFingerprint: String,
        responderFingerprint: String
    ) -> String {
        """
        {
          "type": "kraken.handshake.confirmation.v1",
          "version": 1,
          "confirmation_id": "confirmation-android",
          "response_id": "\(responseId)",
          "invite_id": "\(inviteId)",
          "inviter_fingerprint": "\(inviterFingerprint)",
          "responder_fingerprint": "\(responderFingerprint)",
          "created_at_epoch_millis": 1800000001000,
          "crypto_profile_id": "standard-reviewed-primitives-v1",
          "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
          "profile_policy_version": 1
        }
        """
    }

    private static func packetEnvelopeData(
        packetId: String,
        messageId: String,
        relationshipId: String,
        senderFingerprint: String,
        recipientFingerprint: String,
        body: String,
        expiresAtEpochMillis: Int64 = 1_800_000_300_000
    ) throws -> Data {
        let payloadData = try JSONSerialization.data(
            withJSONObject: [
                "message_id": messageId,
                "body": body,
            ],
            options: [.sortedKeys]
        )
        let payloadJson = String(decoding: payloadData, as: UTF8.self)
        let packet = KrakenPacket(
            packetId: packetId,
            senderFingerprint: senderFingerprint,
            recipientFingerprint: recipientFingerprint,
            relationshipId: relationshipId,
            conversationId: relationshipId,
            messageId: messageId,
            createdAtEpochMillis: 1_800_000_000_000,
            expiresAtEpochMillis: expiresAtEpochMillis,
            payloadJson: payloadJson
        )
        let packetData = try JSONEncoder().encode(packet)
        let packetObject = try XCTUnwrap(JSONSerialization.jsonObject(with: packetData) as? [String: Any])
        return try JSONSerialization.data(
            withJSONObject: [
                "type": "kraken.ios.packet.v1",
                "packet": packetObject,
            ],
            options: [.sortedKeys]
        )
    }
}

private final class DeterministicIDSequence: @unchecked Sendable {
    private let lock = NSLock()
    private var values: [String]

    init(_ values: [String]) {
        self.values = values
    }

    func next() -> String {
        lock.lock()
        defer { lock.unlock() }
        return values.removeFirst()
    }
}
