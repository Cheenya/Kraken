import Foundation
import KrakenDesktopCore

struct ListenArguments {
    var port = 49392
    var timeoutSeconds: TimeInterval = 45
    var outputPath: String?
}

func parseArguments(_ raw: [String]) -> ListenArguments {
    var args = ListenArguments()
    var index = 1
    while index < raw.count {
        let key = raw[index]
        let value = index + 1 < raw.count ? raw[index + 1] : ""
        switch key {
        case "--port":
            args.port = Int(value) ?? args.port
            index += 2
        case "--timeout-seconds":
            args.timeoutSeconds = TimeInterval(value) ?? args.timeoutSeconds
            index += 2
        case "--out":
            args.outputPath = value
            index += 2
        default:
            fputs("Unknown argument: \(key)\n", stderr)
            exit(2)
        }
    }
    return args
}

func write(_ payload: [String: Any], to path: String?) {
    guard let path else { return }
    do {
        let url = URL(fileURLWithPath: path)
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let data = try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys])
        try data.write(to: url)
    } catch {
        fputs("write evidence failed: \(error)\n", stderr)
    }
}

let args = parseArguments(CommandLine.arguments)
let listener = MacLanTcpListener()
let semaphore = DispatchSemaphore(value: 0)
let lock = NSLock()
var events: [MacLanTransferEvent] = []

do {
    _ = try listener.start(requestedPort: args.port) { event in
        lock.lock()
        events.append(event)
        lock.unlock()
        semaphore.signal()
    }
} catch {
    let payload: [String: Any] = [
        "success": false,
        "port": args.port,
        "error": "\(error)",
        "events": [],
    ]
    write(payload, to: args.outputPath)
    print(String(data: try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]), encoding: .utf8) ?? "{}")
    exit(1)
}

let result = semaphore.wait(timeout: .now() + args.timeoutSeconds)
listener.stop()

lock.lock()
let snapshot = events
lock.unlock()

let encodedEvents = snapshot.map { event -> [String: Any] in
    [
        "id": event.id,
        "direction": event.direction.rawValue,
        "status": event.status.rawValue,
        "at_epoch_millis": event.atEpochMillis,
        "source": event.source as Any,
        "target": event.target as Any,
        "packet_id": event.packetId as Any,
        "message_id": event.messageId as Any,
        "payload_json": event.payloadJson as Any,
        "sender_display_name": event.senderDisplayName as Any,
        "sender_fingerprint": event.senderFingerprint as Any,
        "recipient_fingerprint": event.recipientFingerprint as Any,
        "relationship_id": event.relationshipId as Any,
        "error": event.error as Any,
    ]
}
let payload: [String: Any] = [
    "success": result == .success && snapshot.contains { $0.status == .accepted },
    "port": args.port,
    "timeout_seconds": args.timeoutSeconds,
    "events": encodedEvents,
]
write(payload, to: args.outputPath)
let data = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
print(String(data: data, encoding: .utf8) ?? "{}")
exit((payload["success"] as? Bool) == true ? 0 : 1)
