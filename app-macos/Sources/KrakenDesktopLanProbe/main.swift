import Foundation
import KrakenDesktopCore

struct Arguments {
    var host = "127.0.0.1"
    var port = 54035
    var targetFingerprint = "B42B3068934EF618"
    var senderFingerprint = "3C4ED5BA9DB88F9B"
    var senderPeerId = "macos-lan-probe"
    var relationshipId = "relationship-inviteace6db-B42B3068934E-3C4ED5BA9DB8"
    var body = "macOS Swift LAN probe"
    var cryptoProfileId = "standard-reviewed-primitives-v1"
    var sessionProfileId: String?
    var admissionDecisionHash = "sha256:standard-reviewed-primitives-v1:not-applicable:v1"
    var profilePolicyVersion = 1
}

func parseArguments(_ raw: [String]) -> Arguments {
    var args = Arguments()
    var index = 1
    while index < raw.count {
        let key = raw[index]
        let value = index + 1 < raw.count ? raw[index + 1] : ""
        switch key {
        case "--host":
            args.host = value
            index += 2
        case "--port":
            args.port = Int(value) ?? args.port
            index += 2
        case "--target-fingerprint":
            args.targetFingerprint = value
            index += 2
        case "--sender-fingerprint":
            args.senderFingerprint = value
            index += 2
        case "--sender-peer-id":
            args.senderPeerId = value
            index += 2
        case "--relationship-id":
            args.relationshipId = value
            index += 2
        case "--body":
            args.body = value
            index += 2
        case "--crypto-profile-id":
            args.cryptoProfileId = value
            index += 2
        case "--session-profile-id":
            args.sessionProfileId = value
            index += 2
        case "--admission-decision-hash":
            args.admissionDecisionHash = value
            index += 2
        case "--profile-policy-version":
            args.profilePolicyVersion = Int(value) ?? args.profilePolicyVersion
            index += 2
        default:
            fputs("Unknown argument: \(key)\n", stderr)
            exit(2)
        }
    }
    return args
}

let args = parseArguments(CommandLine.arguments)
let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
let messageId = "message-\(UUID().uuidString)"
let payload = try JSONSerialization.data(
    withJSONObject: ["message_id": messageId, "body": args.body],
    options: [.sortedKeys]
)
let payloadJson = String(data: payload, encoding: .utf8) ?? "{}"
let packet = KrakenPacket(
    packetId: "packet-\(UUID().uuidString)",
    senderFingerprint: args.senderFingerprint,
    recipientFingerprint: args.targetFingerprint,
    relationshipId: args.relationshipId,
    conversationId: "desktop-\(args.relationshipId)",
    messageId: messageId,
    createdAtEpochMillis: nowMillis,
    expiresAtEpochMillis: nowMillis + 300_000,
    payloadJson: payloadJson,
    cryptoProfileId: args.cryptoProfileId,
    sessionProfileId: args.sessionProfileId,
    admissionDecisionHash: args.admissionDecisionHash,
    profilePolicyVersion: args.profilePolicyVersion
)
let envelope = LanFrameEnvelope(
    senderPeerId: args.senderPeerId,
    senderFingerprint: args.senderFingerprint,
    senderDisplayName: "Kraken Desktop",
    senderReplyPort: nil,
    packet: packet
)
let endpoint = MacLanEndpoint(
    host: args.host,
    port: args.port,
    fingerprint: args.targetFingerprint,
    displayName: "Android target"
)
let event = MacLanTcpSender().send(envelope: envelope, endpoint: endpoint)
let json = try JSONEncoder().encode(event)
print(String(data: json, encoding: .utf8) ?? "{}")
exit(event.status == .acked ? 0 : 1)
