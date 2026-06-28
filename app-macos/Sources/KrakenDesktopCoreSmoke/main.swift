import Compression
import Foundation
import KrakenDesktopCore

let fixedDate = Date(timeIntervalSince1970: 1_800_000_000)
var ids = ["one", "two", "three", "four"]
let simulator = KrakenDesktopSimulator(
    now: { fixedDate },
    makeId: { ids.removeFirst() }
)

var state = simulator.makeInitialState()
state = simulator.sendMessage(in: state, relationshipId: "rel-xiaomi", body: "hello desktop")
precondition(state.messages.last?.status == .sentToTransport, "active peer should send to transport")

state = simulator.confirmLatestDelivery(in: state, relationshipId: "rel-xiaomi")
precondition(state.messages.last?.status == .deliveredToPeer, "delivery receipt should mark delivered")

let blocked = simulator.sendMessage(in: state, relationshipId: "rel-samsung", body: "blocked")
precondition(blocked.messages.count == state.messages.count, "pending relationship must block message sending")

state = simulator.cycleRoute(in: state, relationshipId: "rel-xiaomi")
precondition(
    state.routes.first { $0.relationshipId == "rel-xiaomi" }?.kind == .routedMesh,
    "direct LAN should cycle to routed mesh"
)

state = simulator.evaluateAdmission(in: state, experimental: false)
precondition(
    !state.admissionResult.decision.acceptedForPacketPolicy,
    "risky Adamova profile should be blocked"
)

let nowMillis = Int64(fixedDate.timeIntervalSince1970 * 1000)
let packet = KrakenPacket(
    packetId: "packet-smoke",
    senderFingerprint: "MACOSDESKTOP0001",
    recipientFingerprint: "B42B3068934EF618",
    relationshipId: "rel-xiaomi",
    conversationId: "desktop-rel-xiaomi",
    messageId: "message-smoke",
    createdAtEpochMillis: nowMillis,
    expiresAtEpochMillis: nowMillis + 300_000,
    payloadJson: #"{"message_id":"message-smoke","body":"hello"}"#
)
let envelope = LanFrameEnvelope(
    senderPeerId: "macos-desktop",
    senderFingerprint: "MACOSDESKTOP0001",
    senderDisplayName: "Desktop Kraken",
    senderReplyPort: 43191,
    packet: packet
)
let frame = try LanFrameCodec.encodeEnvelope(envelope)
let decoded = try LanFrameCodec.decodeFrame(frame)
precondition(decoded == envelope, "LAN frame codec must round-trip Android-compatible envelope")

let androidEnvelopePayload = """
{
    "frame_version": 1,
    "sender_peer_id": "android-fixture-peer",
    "sender_fingerprint": "ANDROID-FP",
    "sender_display_name": "Xiaomi fixture",
    "sender_reply_port": 54035,
    "packet": {
        "packet_id": "packet-android-fixture",
        "protocol_version": 1,
        "packet_type": "MESSAGE",
        "sender_fingerprint": "ANDROID-FP",
        "recipient_fingerprint": "MACOSDESKTOP0001",
        "relationship_id": "relationship-android-fixture",
        "conversation_id": "conversation-android-fixture",
        "message_id": "message-android-fixture",
        "created_at_epoch_millis": 1700000000000,
        "expires_at_epoch_millis": 1700000300000,
        "ttl_hops": 4,
        "payload_type": "LOCAL_MESSAGE_JSON",
        "payload_json": "{\\"body\\":\\"hello from Android\\",\\"message_id\\":\\"message-android-fixture\\"}",
        "crypto_profile_id": "standard-reviewed-primitives-v1",
        "session_profile_id": null,
        "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
        "profile_policy_version": 1,
        "proof_mode": "local-admission-check-v1"
    }
}
"""
let androidPayloadData = androidEnvelopePayload.data(using: .utf8)!
var androidPayloadLength = UInt32(androidPayloadData.count).bigEndian
var androidStyleFrame = Data(bytes: &androidPayloadLength, count: 4)
androidStyleFrame.append(androidPayloadData)
let androidDecoded = try LanFrameCodec.decodeFrame(androidStyleFrame)
precondition(androidDecoded.senderPeerId == "android-fixture-peer", "macOS LAN codec should decode Android sender peer id")
precondition(androidDecoded.senderReplyPort == 54035, "macOS LAN codec should decode Android reply port")
precondition(androidDecoded.packet.packetType == "MESSAGE", "macOS LAN codec should decode Android packet type")
precondition(androidDecoded.packet.payloadType == "LOCAL_MESSAGE_JSON", "macOS LAN codec should decode Android payload type")
precondition(androidDecoded.packet.payloadJson.contains("hello from Android"), "macOS LAN codec should decode Android payload JSON")

var timelineState = state
timelineState.messages.append(
    LocalMessage(
        messageId: "timeline-outgoing-ack",
        relationshipId: "rel-xiaomi",
        peerFingerprint: "A17C9E2048F0DA11",
        direction: .outgoing,
        status: .sentToTransport,
        body: "ack me",
        createdAt: fixedDate,
        updatedAt: fixedDate
    )
)
var timelineResult = LanTimelineReducer.apply(
    event: MacLanTransferEvent(
        direction: .outbound,
        status: .acked,
        atEpochMillis: nowMillis,
        source: "macos:43191",
        target: "127.0.0.1:54035",
        packetId: "packet-timeline-ack",
        messageId: "timeline-outgoing-ack",
        senderFingerprint: "MACOSDESKTOP0001",
        recipientFingerprint: "A17C9E2048F0DA11",
        relationshipId: "rel-xiaomi"
    ),
    to: timelineState,
    now: fixedDate
)
precondition(
    timelineResult.state.messages.first { $0.messageId == "timeline-outgoing-ack" }?.status == .deliveredToPeer,
    "LAN ACK should mark outgoing message as delivered"
)

timelineState = timelineResult.state
timelineState.messages.append(
    LocalMessage(
        messageId: "timeline-outgoing-fail",
        relationshipId: "rel-xiaomi",
        peerFingerprint: "A17C9E2048F0DA11",
        direction: .outgoing,
        status: .sentToTransport,
        body: "fail me",
        createdAt: fixedDate,
        updatedAt: fixedDate
    )
)
timelineResult = LanTimelineReducer.apply(
    event: MacLanTransferEvent(
        direction: .outbound,
        status: .failed,
        atEpochMillis: nowMillis,
        source: "macos:43191",
        target: "127.0.0.1:54035",
        packetId: "packet-timeline-fail",
        messageId: "timeline-outgoing-fail",
        senderFingerprint: "MACOSDESKTOP0001",
        recipientFingerprint: "A17C9E2048F0DA11",
        relationshipId: "rel-xiaomi",
        error: "ack-missing"
    ),
    to: timelineState,
    now: fixedDate
)
precondition(
    timelineResult.state.messages.first { $0.messageId == "timeline-outgoing-fail" }?.status == .failed,
    "LAN failure should mark outgoing message as failed"
)

let inboundTimelineResult = LanTimelineReducer.apply(
    event: MacLanTransferEvent(
        direction: .inbound,
        status: .accepted,
        atEpochMillis: nowMillis,
        source: "127.0.0.1:54035",
        target: "0.0.0.0:43191",
        packetId: "packet-inbound-new",
        messageId: "timeline-inbound-new",
        payloadJson: #"{"message_id":"timeline-inbound-new","body":"привет с телефона"}"#,
        senderDisplayName: "Телефон Xiaomi",
        senderFingerprint: "FACEFEED00000001",
        recipientFingerprint: "MACOSDESKTOP0001",
        relationshipId: nil
    ),
    to: timelineResult.state,
    now: fixedDate
)
precondition(
    inboundTimelineResult.selectedRelationshipId == "rel-lan-FACEFEED00000001",
    "inbound unknown LAN sender should become selected relationship"
)
precondition(
    inboundTimelineResult.state.messages.contains { $0.messageId == "timeline-inbound-new" && $0.body == "привет с телефона" },
    "inbound LAN payload body should be added to chat timeline"
)
precondition(
    inboundTimelineResult.state.routes.contains { $0.relationshipId == "rel-lan-FACEFEED00000001" && $0.kind == .directLan },
    "inbound unknown LAN sender should get a direct LAN route"
)

let bleChunks = try BleFrameCodec.encodeChunks(
    packet: packet,
    senderPeerId: envelope.senderPeerId,
    senderFingerprint: envelope.senderFingerprint,
    senderDisplayName: envelope.senderDisplayName,
    chunkPayloadBytes: 64
)
precondition(MacBleConstants.serviceUuidString == "58a1257c-f4a8-48c8-99d5-917b9863d7c4", "BLE service UUID must match Android BleGattTransport")
precondition(MacBleConstants.identityCharacteristicUuidString == "58a1257d-f4a8-48c8-99d5-917b9863d7c4", "BLE identity characteristic UUID must match Android BleGattTransport")
precondition(MacBleConstants.packetCharacteristicUuidString == "58a1257e-f4a8-48c8-99d5-917b9863d7c4", "BLE packet characteristic UUID must match Android BleGattTransport")
precondition(!bleChunks.isEmpty, "BLE frame codec should emit chunks")
precondition(bleChunks.allSatisfy { $0.count <= BleFrameCodec.maxGattWriteBytes }, "BLE chunks must fit Android GATT write payload")
let bleChunkJson = String(data: bleChunks[0], encoding: .utf8) ?? ""
precondition(bleChunkJson.contains("\"frame_version\""), "BLE chunk JSON must use Android snake_case keys")
precondition(bleChunkJson.contains("\"payload_base64\""), "BLE chunk JSON must use Android payload_base64 key")
let bleIdentityJson = String(
    data: try JSONEncoder().encode(MacBlePeerIdentity(peerId: envelope.senderPeerId, fingerprint: envelope.senderFingerprint, displayName: envelope.senderDisplayName)),
    encoding: .utf8
) ?? ""
precondition(bleIdentityJson.contains("\"peer_id\""), "BLE identity JSON must use Android peer_id key")
precondition(bleIdentityJson.contains("\"display_name\""), "BLE identity JSON must use Android display_name key")
let bleReassembler = BleFrameReassembler(now: { nowMillis })
var bleEnvelope: LanFrameEnvelope?
for chunkData in bleChunks {
    let chunk = try BleFrameCodec.decodeChunk(chunkData)
    bleEnvelope = try bleReassembler.accept(chunk) ?? bleEnvelope
}
precondition(bleEnvelope?.packet == packet, "BLE frame codec must reassemble Android-compatible packet")
precondition(bleEnvelope?.senderFingerprint == envelope.senderFingerprint, "BLE sender fingerprint should survive reassembly")

var bleTimelineState = inboundTimelineResult.state
let bleInboundResult = BleTimelineReducer.apply(
    event: MacBleTransferEvent(
        direction: .inbound,
        status: .accepted,
        atEpochMillis: nowMillis,
        peerFingerprint: "BEEFBLE000000001",
        packetId: "packet-ble-inbound",
        messageId: "message-ble-inbound",
        payloadJson: #"{"message_id":"message-ble-inbound","body":"привет по BLE"}"#,
        senderDisplayName: "BLE Android",
        senderFingerprint: "BEEFBLE000000001",
        recipientFingerprint: "MACOSDESKTOP0001",
        relationshipId: nil
    ),
    to: bleTimelineState,
    now: fixedDate
)
precondition(
    bleInboundResult.state.messages.contains { $0.messageId == "message-ble-inbound" && $0.body == "привет по BLE" },
    "inbound BLE payload body should be added to chat timeline"
)
precondition(
    bleInboundResult.state.routes.contains { $0.relationshipId == "rel-ble-BEEFBLE000000001" && $0.kind == .directBle },
    "inbound unknown BLE sender should get a direct BLE route"
)
bleTimelineState = bleInboundResult.state
bleTimelineState.messages.append(
    LocalMessage(
        messageId: "message-ble-failed",
        relationshipId: "rel-ble-BEEFBLE000000001",
        peerFingerprint: "BEEFBLE000000001",
        direction: .outgoing,
        status: .sentToTransport,
        body: "fail over ble",
        createdAt: fixedDate,
        updatedAt: fixedDate
    )
)
let bleFailedResult = BleTimelineReducer.apply(
    event: MacBleTransferEvent(
        direction: .outbound,
        status: .failed,
        atEpochMillis: nowMillis,
        peerFingerprint: "BEEFBLE000000001",
        packetId: "packet-ble-failed",
        messageId: "message-ble-failed",
        error: "ble-peer-not-ready"
    ),
    to: bleTimelineState,
    now: fixedDate
)
precondition(
    bleFailedResult.state.messages.first { $0.messageId == "message-ble-failed" }?.status == .failed,
    "outbound BLE failure should mark message as failed"
)

let androidHandshakeResponseQr = """
{
    "type": "kraken.handshake.response.v1",
    "version": 1,
    "response_id": "response-invite-samsung-SAMSUNGFP",
    "invite_id": "invite-samsung",
    "realm_id": null,
    "requires_approval": false,
    "responder_fingerprint": "SAMSUNGFP00000001",
    "responder_display_name": "Samsung лабораторный",
    "responder_public_key_encoded": "samsung-public-key",
    "inviter_fingerprint": "MACOSDESKTOP0001",
    "created_at_epoch_millis": 1800000000000,
    "relationship_hint": "relationship-samsung-pending",
    "crypto_profile_id": "standard-reviewed-primitives-v1",
    "crypto_profile_hash": null,
    "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
    "profile_policy_version": 1,
    "native_backend_version": "android-fixture",
    "proof_placeholder": "offline-qr-handshake-check-v1"
}
"""
precondition(
    KrakenHandshakeQrCodec.detectKind(androidHandshakeResponseQr) == .response,
    "QR codec should detect Android handshake response payload"
)
let decodedHandshakeResponse = try KrakenHandshakeQrCodec.decodeResponse(androidHandshakeResponseQr)
precondition(decodedHandshakeResponse.responseId == "response-invite-samsung-SAMSUNGFP", "QR codec should decode response_id")
precondition(decodedHandshakeResponse.responderFingerprint == "SAMSUNGFP00000001", "QR codec should decode responder fingerprint")
precondition(decodedHandshakeResponse.inviterFingerprint == "MACOSDESKTOP0001", "QR codec should decode inviter fingerprint")

let macEncodedResponseQr = try KrakenHandshakeQrCodec.encodedQrPayload(androidHandshakeResponseQr)
precondition(
    macEncodedResponseQr.hasPrefix("https://kraken.local/qr?v=2&z=d&p="),
    "QR encoder should emit compact Android web URI"
)
precondition(
    !macEncodedResponseQr.contains("#Intent;"),
    "QR encoder should not use verbose Android intent wrapper for generated compact QR"
)
let decodedMacEncodedResponseQr = try KrakenHandshakeQrCodec.decodeResponse(macEncodedResponseQr)
precondition(
    decodedMacEncodedResponseQr == decodedHandshakeResponse,
    "QR encoder output should round-trip through scanner decoder"
)

let deflatedResponsePayload = String(macEncodedResponseQr.split(separator: "p=", maxSplits: 1).last ?? "")
let androidWebResponseQr = "https://kraken.local/qr?v=2&z=d&p=\(deflatedResponsePayload)"
precondition(
    KrakenHandshakeQrCodec.detectKind(androidWebResponseQr) == .response,
    "QR codec should decode Android compact web URI raw-deflate payload"
)
let decodedAndroidWebResponse = try KrakenHandshakeQrCodec.decodeResponse(androidWebResponseQr)
precondition(
    decodedAndroidWebResponse == decodedHandshakeResponse,
    "QR codec should decode Android compact v2 web URI response payload"
)
let escapedAndroidWebResponseQr = "https://kraken.local/qr?v=2&amp;z=d&amp;p=\(deflatedResponsePayload)"
precondition(
    KrakenHandshakeQrCodec.detectKind(escapedAndroidWebResponseQr) == .response,
    "QR codec should decode escaped Android compact web URI"
)
let androidIntentResponseQr = "intent://qr?v=2&amp;z=deflate&amp;payload=\(deflatedResponsePayload)#Intent;scheme=kraken;package=com.disser.kraken;end"
precondition(
    KrakenHandshakeQrCodec.detectKind(androidIntentResponseQr) == .response,
    "QR codec should decode Android intent URI deflated payload"
)
let decodedAndroidIntentResponse = try KrakenHandshakeQrCodec.decodeResponse(androidIntentResponseQr)
precondition(
    decodedAndroidIntentResponse == decodedHandshakeResponse,
    "QR codec should decode Android v2 intent URI response payload"
)
let parsedKrakenDataResponseQr = "kraken://qr?v=2&z=deflate&payload=\(deflatedResponsePayload)"
precondition(
    KrakenHandshakeQrCodec.detectKind(parsedKrakenDataResponseQr) == .response,
    "QR codec should decode parsed kraken data URI deflated payload"
)
let decodedParsedKrakenDataResponse = try KrakenHandshakeQrCodec.decodeResponse(parsedKrakenDataResponseQr)
precondition(
    decodedParsedKrakenDataResponse == decodedHandshakeResponse,
    "QR codec should decode parsed v2 kraken data URI response payload"
)

let wrappedResponseData = try JSONSerialization.data(
    withJSONObject: ["payload_json": androidHandshakeResponseQr],
    options: [.sortedKeys]
)
let wrappedResponseQr = String(data: wrappedResponseData, encoding: .utf8) ?? ""
precondition(
    KrakenHandshakeQrCodec.detectKind(wrappedResponseQr) == .response,
    "QR codec should unwrap nested payload_json response payload"
)
let decodedWrappedHandshakeResponse = try KrakenHandshakeQrCodec.decodeResponse(wrappedResponseQr)
precondition(
    decodedWrappedHandshakeResponse == decodedHandshakeResponse,
    "QR codec should decode wrapped Android response payload"
)
let quotedResponseQr = String(data: try JSONEncoder().encode(androidHandshakeResponseQr), encoding: .utf8) ?? ""
precondition(
    KrakenHandshakeQrCodec.detectKind(quotedResponseQr) == .response,
    "QR codec should unwrap JSON string response payload"
)
let base64ResponseQr = Data(androidHandshakeResponseQr.utf8).base64EncodedString()
precondition(
    KrakenHandshakeQrCodec.detectKind(base64ResponseQr) == .response,
    "QR codec should unwrap base64 JSON response payload"
)
let urlResponseQr = "kraken://handshake?payload_json=\(androidHandshakeResponseQr.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
precondition(
    KrakenHandshakeQrCodec.detectKind(urlResponseQr) == .response,
    "QR codec should unwrap URL query response payload"
)
let base64QueryResponseQr = "kraken://qr?v=1&payload=\(base64ResponseQr)"
precondition(
    KrakenHandshakeQrCodec.detectKind(base64QueryResponseQr) == .response,
    "QR codec should unwrap kraken URL base64 response payload"
)
let base64URLResponseQr = base64ResponseQr
    .replacingOccurrences(of: "+", with: "-")
    .replacingOccurrences(of: "/", with: "_")
    .trimmingCharacters(in: CharacterSet(charactersIn: "="))
let base64URLQueryResponseQr = "kraken://qr?v=1&payload=\(base64URLResponseQr)"
precondition(
    KrakenHandshakeQrCodec.detectKind(base64URLQueryResponseQr) == .response,
    "QR codec should unwrap legacy kraken URL base64url response payload"
)
let unpaddedBase64QueryResponseQr = "kraken://qr?v=1&payload=\(base64ResponseQr.trimmingCharacters(in: CharacterSet(charactersIn: "=")))"
precondition(
    KrakenHandshakeQrCodec.detectKind(unpaddedBase64QueryResponseQr) == .response,
    "QR codec should unwrap unpadded kraken URL base64 response payload"
)
let schemeResponseQr = "kraken:\(androidHandshakeResponseQr.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
precondition(
    KrakenHandshakeQrCodec.detectKind(schemeResponseQr) == .response,
    "QR codec should unwrap kraken scheme response payload"
)

let androidHandshakeConfirmationQr = """
{
    "type": "kraken.handshake.confirmation.v1",
    "version": 1,
    "confirmation_id": "confirmation-invite-samsung-SAMSUNGFP",
    "response_id": "response-invite-samsung-SAMSUNGFP",
    "invite_id": "invite-samsung",
    "realm_id": null,
    "realm_name": null,
    "membership_certificate": null,
    "inviter_fingerprint": "MACOSDESKTOP0001",
    "responder_fingerprint": "SAMSUNGFP00000001",
    "created_at_epoch_millis": 1800000000001,
    "crypto_profile_id": "standard-reviewed-primitives-v1",
    "crypto_profile_hash": null,
    "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
    "profile_policy_version": 1,
    "native_backend_version": "android-fixture",
    "proof_placeholder": "offline-qr-confirmation-check-v1"
}
"""
precondition(
    KrakenHandshakeQrCodec.detectKind(androidHandshakeConfirmationQr) == .confirmation,
    "QR codec should detect Android handshake confirmation payload"
)
let decodedHandshakeConfirmation = try KrakenHandshakeQrCodec.decodeConfirmation(androidHandshakeConfirmationQr)
precondition(decodedHandshakeConfirmation.confirmationId == "confirmation-invite-samsung-SAMSUNGFP", "QR codec should decode confirmation_id")
precondition(decodedHandshakeConfirmation.responderFingerprint == "SAMSUNGFP00000001", "QR codec should decode confirmation responder")
precondition(decodedHandshakeConfirmation.inviterFingerprint == "MACOSDESKTOP0001", "QR codec should decode confirmation inviter")

var packetPolicy = KrakenPacketPolicyValidator()
try packetPolicy.acceptInbound(envelope.packet, nowMillis: nowMillis)
do {
    try packetPolicy.acceptInbound(envelope.packet, nowMillis: nowMillis)
    preconditionFailure("packet policy should reject duplicate packet_id")
} catch KrakenPacketPolicyError.duplicatePacket(let packetId) {
    precondition(packetId == envelope.packet.packetId, "duplicate policy should report packet_id")
}
var expiredPacket = envelope.packet
expiredPacket.packetId = "packet-expired-policy"
expiredPacket.expiresAtEpochMillis = nowMillis - 1
do {
    try packetPolicy.acceptInbound(expiredPacket, nowMillis: nowMillis)
    preconditionFailure("packet policy should reject expired packet")
} catch KrakenPacketPolicyError.expired(let packetId) {
    precondition(packetId == expiredPacket.packetId, "expiry policy should report packet_id")
}
var ttlPacket = envelope.packet
ttlPacket.packetId = "packet-ttl-policy"
ttlPacket.ttlHops = 0
do {
    try packetPolicy.acceptInbound(ttlPacket, nowMillis: nowMillis)
    preconditionFailure("packet policy should reject exhausted TTL")
} catch KrakenPacketPolicyError.ttlExhausted(let packetId) {
    precondition(packetId == ttlPacket.packetId, "TTL policy should report packet_id")
}
precondition(KrakenOutboxBackoffPolicy.retryDelay(afterAttempt: 0) == 0, "outbox backoff should not delay before first attempt")
precondition(KrakenOutboxBackoffPolicy.retryDelay(afterAttempt: 1) == 2, "outbox backoff should delay retry after first failure")
precondition(KrakenOutboxBackoffPolicy.retryDelay(afterAttempt: 4) == 16, "outbox backoff should grow exponentially")
precondition(KrakenOutboxBackoffPolicy.retryDelay(afterAttempt: 9) == 30, "outbox backoff should cap retry delay")

let listener = MacLanTcpListener(now: { nowMillis })
let received = DispatchSemaphore(value: 0)
var inboundEvents: [MacLanTransferEvent] = []
let loopbackPort = 49391
_ = try listener.start(requestedPort: loopbackPort) { event in
    inboundEvents.append(event)
    received.signal()
}
let outbound = MacLanTcpSender(now: { nowMillis }).send(
    envelope: envelope,
    endpoint: MacLanEndpoint(
        host: "127.0.0.1",
        port: loopbackPort,
        fingerprint: envelope.packet.recipientFingerprint,
        displayName: "loopback"
    )
)
precondition(outbound.status == .acked, "loopback LAN sender should receive Android-compatible ACK")
precondition(received.wait(timeout: .now() + 2) == .success, "listener should record inbound frame")
precondition(inboundEvents.first?.packetId == envelope.packet.packetId, "listener should decode inbound packet id")
precondition(inboundEvents.first?.payloadJson == envelope.packet.payloadJson, "listener should expose inbound payload JSON")
listener.stop()

print("KrakenDesktopCoreSmoke passed")

private func rawDeflatedBase64URLJsonPayload(_ rawJson: String) throws -> String {
    let compactJsonData = try compactJsonData(rawJson)
    let compressed = try rawDeflatedData(compactJsonData)
    return compressed.base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .trimmingCharacters(in: CharacterSet(charactersIn: "="))
}

private func compactJsonData(_ rawJson: String) throws -> Data {
    let object = try JSONSerialization.jsonObject(with: Data(rawJson.utf8))
    return try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
}

private func rawDeflatedData(_ data: Data) throws -> Data {
    var capacity = max(4096, data.count * 2)
    let maxCapacity = 4 * 1024 * 1024
    while capacity <= maxCapacity {
        let encoded = UnsafeMutablePointer<UInt8>.allocate(capacity: capacity)
        defer { encoded.deallocate() }
        let encodedCount = data.withUnsafeBytes { sourceBuffer in
            guard let source = sourceBuffer.bindMemory(to: UInt8.self).baseAddress else { return 0 }
            return compression_encode_buffer(
                encoded,
                capacity,
                source,
                data.count,
                nil,
                COMPRESSION_ZLIB
            )
        }
        if encodedCount > 0 {
            return Data(bytes: encoded, count: encodedCount)
        }
        capacity *= 2
    }
    throw NSError(domain: "KrakenDesktopCoreSmoke", code: 1)
}
