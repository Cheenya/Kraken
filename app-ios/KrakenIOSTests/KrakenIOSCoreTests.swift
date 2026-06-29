import XCTest
@testable import KrakenIOS

final class KrakenIOSCoreTests: XCTestCase {
    func testQrCodecRoundTripsCompressedAndroidInvitePayload() throws {
        let rawPayload = """
        {
          "type": "one_time_invite",
          "version": 1,
          "invite_id": "invite-ios-fixture",
          "inviter_fingerprint": "ANDROID-FP",
          "created_at_epoch_millis": 1700000000000
        }
        """

        let encoded = try KrakenHandshakeQrCodec.encodedQrPayload(rawPayload)

        XCTAssertTrue(encoded.hasPrefix("https://kraken.local/qr?v=2&z=d&p="))
        XCTAssertEqual(KrakenHandshakeQrCodec.detectKind(encoded), .invite)
        XCTAssertEqual(
            KrakenHandshakeQrCodec.normalizedScannedPayload(encoded),
            #"{"created_at_epoch_millis":1700000000000,"invite_id":"invite-ios-fixture","inviter_fingerprint":"ANDROID-FP","type":"one_time_invite","version":1}"#
        )
    }

    func testPacketPolicyRejectsExpiredTtlAndDuplicatePacketsBeforeTimelineMutation() throws {
        let nowMillis: Int64 = 1_700_000_000_000
        var validator = KrakenPacketPolicyValidator()

        XCTAssertThrowsError(try validator.acceptInbound(
            KrakenPacket.fixture(packetId: "expired", expiresAtEpochMillis: nowMillis - 1),
            nowMillis: nowMillis
        )) { error in
            XCTAssertEqual(error as? KrakenPacketPolicyError, .expired(packetId: "expired"))
        }

        XCTAssertThrowsError(try validator.acceptInbound(
            KrakenPacket.fixture(packetId: "ttl-empty", ttlHops: 0),
            nowMillis: nowMillis
        )) { error in
            XCTAssertEqual(error as? KrakenPacketPolicyError, .ttlExhausted(packetId: "ttl-empty"))
        }

        try validator.acceptInbound(
            KrakenPacket.fixture(packetId: "accepted", expiresAtEpochMillis: nowMillis + 300_000),
            nowMillis: nowMillis
        )
        XCTAssertThrowsError(try validator.acceptInbound(
            KrakenPacket.fixture(packetId: "accepted", expiresAtEpochMillis: nowMillis + 300_000),
            nowMillis: nowMillis
        )) { error in
            XCTAssertEqual(error as? KrakenPacketPolicyError, .duplicatePacket(packetId: "accepted"))
        }
    }

    func testOutboxBackoffPreservesMessageIdAcrossRetries() {
        var record = KrakenOutboxRecord(messageId: "message-ios-1")

        record = record.recordFailure(error: "peer-unavailable")
        XCTAssertEqual(record.messageId, "message-ios-1")
        XCTAssertEqual(record.attempts, 1)
        XCTAssertEqual(record.nextRetryDelay, 2)

        record = record.recordFailure(error: "peer-still-unavailable")
        XCTAssertEqual(record.messageId, "message-ios-1")
        XCTAssertEqual(record.attempts, 2)
        XCTAssertEqual(record.nextRetryDelay, 4)
    }

    func testIOSNearbyTransportDescriptorDoesNotClaimAndroidWifiDirect() {
        let descriptor = IOSNearbyTransportDescriptor()

        XCTAssertEqual(descriptor.serviceType, "kraken-ios")
        XCTAssertTrue(descriptor.serviceType.count >= 1)
        XCTAssertTrue(descriptor.serviceType.count <= 15)
        XCTAssertTrue(descriptor.serviceType.allSatisfy { character in
            character.isLowercase || character.isNumber || character == "-"
        })
        XCTAssertEqual(descriptor.transportId, "ios-multipeerconnectivity")
        XCTAssertEqual(descriptor.routeKind, .appleNearby)
        XCTAssertFalse(descriptor.transportId.lowercased().contains("wifi-direct"))
        XCTAssertFalse(descriptor.boundaryNote.contains("Android Wi-Fi Direct"))
        XCTAssertTrue(descriptor.boundaryNote.contains("Apple MultipeerConnectivity"))
    }
}

private extension KrakenPacket {
    static func fixture(
        packetId: String,
        expiresAtEpochMillis: Int64 = 1_700_000_300_000,
        ttlHops: Int = 4
    ) -> KrakenPacket {
        KrakenPacket(
            packetId: packetId,
            senderFingerprint: "ANDROID-FP",
            recipientFingerprint: "IOS-FP",
            relationshipId: "relationship-ios",
            conversationId: "conversation-ios",
            messageId: "message-\(packetId)",
            createdAtEpochMillis: 1_700_000_000_000,
            expiresAtEpochMillis: expiresAtEpochMillis,
            ttlHops: ttlHops,
            payloadJson: #"{"message_id":"message-fixture","body":"hello"}"#
        )
    }
}
