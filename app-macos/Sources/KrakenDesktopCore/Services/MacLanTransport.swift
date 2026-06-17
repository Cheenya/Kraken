import Foundation
import Network

public final class MacLanTcpListener: @unchecked Sendable {
    private let queue = DispatchQueue(label: "kraken.desktop.lan.listener")
    private let now: @Sendable () -> Int64
    private var listener: NWListener?
    private var packetPolicy = KrakenPacketPolicyValidator()

    public private(set) var localPort: Int?

    public init(now: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
        self.now = now
    }

    public func start(
        requestedPort: Int,
        onEvent: @escaping @Sendable (MacLanTransferEvent) -> Void
    ) throws -> Int {
        stop()
        guard let port = NWEndpoint.Port(rawValue: UInt16(requestedPort)) else {
            throw MacLanTransportError.invalidFrameLength
        }
        let listener = try NWListener(using: .tcp, on: port)
        self.listener = listener
        listener.newConnectionHandler = { [weak self] connection in
            self?.handle(connection: connection, onEvent: onEvent)
        }
        listener.start(queue: queue)
        localPort = requestedPort
        return requestedPort
    }

    public func stop() {
        listener?.cancel()
        listener = nil
        localPort = nil
    }

    private func handle(
        connection: NWConnection,
        onEvent: @escaping @Sendable (MacLanTransferEvent) -> Void
    ) {
        connection.start(queue: queue)
        connection.receive(minimumIncompleteLength: 4, maximumLength: 4) { [weak self] lengthData, _, _, error in
            guard let self else { return }
            if let error {
                onEvent(self.failureEvent(direction: .inbound, source: nil, error: error.localizedDescription))
                connection.cancel()
                return
            }
            guard let lengthData, lengthData.count == 4 else {
                onEvent(self.failureEvent(direction: .inbound, source: nil, error: "missing-length-prefix"))
                connection.cancel()
                return
            }
            let length = lengthData.reduce(UInt32(0)) { partial, byte in
                (partial << 8) | UInt32(byte)
            }
            guard length > 0 && length <= LanFrameCodec.maxFrameBytes else {
                onEvent(self.failureEvent(direction: .inbound, source: nil, error: "invalid-frame-length"))
                connection.cancel()
                return
            }
            connection.receive(
                minimumIncompleteLength: Int(length),
                maximumLength: Int(length)
            ) { payload, _, _, error in
                if let error {
                    onEvent(self.failureEvent(direction: .inbound, source: nil, error: error.localizedDescription))
                    connection.cancel()
                    return
                }
                guard let payload, payload.count == Int(length) else {
                    onEvent(self.failureEvent(direction: .inbound, source: nil, error: "truncated-frame"))
                    connection.cancel()
                    return
                }
                do {
                    let envelope = try LanFrameCodec.decodeEnvelope(framePayload: payload)
                    try self.packetPolicy.acceptInbound(envelope.packet, nowMillis: self.now())
                    connection.send(content: Data([LanFrameCodec.ackByte]), completion: .contentProcessed { _ in
                        connection.cancel()
                    })
                    onEvent(
                        MacLanTransferEvent(
                            direction: .inbound,
                            status: .accepted,
                            atEpochMillis: self.now(),
                            source: connection.endpoint.debugDescription,
                            target: self.localPort.map { "0.0.0.0:\($0)" },
                            packetId: envelope.packet.packetId,
                            messageId: envelope.packet.messageId,
                            payloadJson: envelope.packet.payloadJson,
                            senderDisplayName: envelope.senderDisplayName,
                            senderFingerprint: envelope.senderFingerprint,
                            recipientFingerprint: envelope.packet.recipientFingerprint,
                            relationshipId: envelope.packet.relationshipId
                        )
                    )
                } catch {
                    onEvent(self.failureEvent(direction: .inbound, source: connection.endpoint.debugDescription, error: "\(error)"))
                    connection.cancel()
                }
            }
        }
    }

    private func failureEvent(direction: MacLanEventDirection, source: String?, error: String) -> MacLanTransferEvent {
        MacLanTransferEvent(
            direction: direction,
            status: .failed,
            atEpochMillis: now(),
            source: source,
            target: localPort.map { "0.0.0.0:\($0)" },
            packetId: nil,
            messageId: nil,
            senderFingerprint: nil,
            recipientFingerprint: nil,
            relationshipId: nil,
            error: error
        )
    }
}

public struct MacLanTcpSender: Sendable {
    private let now: @Sendable () -> Int64

    public init(now: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
        self.now = now
    }

    public func send(envelope: LanFrameEnvelope, endpoint: MacLanEndpoint, timeoutSeconds: TimeInterval = 8) -> MacLanTransferEvent {
        let target = "\(endpoint.host):\(endpoint.port)"
        do {
            let frame = try LanFrameCodec.encodeEnvelope(envelope)
            guard let port = NWEndpoint.Port(rawValue: UInt16(endpoint.port)) else {
                throw MacLanTransportError.invalidFrameLength
            }
            let connection = NWConnection(host: NWEndpoint.Host(endpoint.host), port: port, using: .tcp)
            let queue = DispatchQueue(label: "kraken.desktop.lan.sender.\(UUID().uuidString)")
            let semaphore = DispatchSemaphore(value: 0)
            let lock = NSLock()
            var acked = false
            var failure: String?

            func finish(success: Bool, error: String? = nil) {
                lock.lock()
                if !acked && failure == nil {
                    if success {
                        acked = true
                    } else {
                        failure = error ?? "lan-send-failed"
                    }
                    semaphore.signal()
                }
                lock.unlock()
            }

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    connection.send(content: frame, completion: .contentProcessed { error in
                        if let error {
                            finish(success: false, error: error.localizedDescription)
                            return
                        }
                        connection.receive(minimumIncompleteLength: 1, maximumLength: 1) { data, _, _, error in
                            if let error {
                                finish(success: false, error: error.localizedDescription)
                            } else if data == Data([LanFrameCodec.ackByte]) {
                                finish(success: true)
                            } else {
                                finish(success: false, error: "ack-missing")
                            }
                        }
                    })
                case .failed(let error):
                    finish(success: false, error: error.localizedDescription)
                case .cancelled:
                    break
                default:
                    break
                }
            }
            connection.start(queue: queue)

            if semaphore.wait(timeout: .now() + timeoutSeconds) == .timedOut {
                failure = "timeout"
            }
            connection.cancel()

            return MacLanTransferEvent(
                direction: .outbound,
                status: acked ? .acked : .failed,
                atEpochMillis: now(),
                source: envelope.senderReplyPort.map { "macos:\($0)" },
                target: target,
                packetId: envelope.packet.packetId,
                messageId: envelope.packet.messageId,
                payloadJson: envelope.packet.payloadJson,
                senderDisplayName: envelope.senderDisplayName,
                senderFingerprint: envelope.senderFingerprint,
                recipientFingerprint: envelope.packet.recipientFingerprint,
                relationshipId: envelope.packet.relationshipId,
                error: acked ? nil : (failure ?? "lan-send-failed")
            )
        } catch {
            return MacLanTransferEvent(
                direction: .outbound,
                status: .failed,
                atEpochMillis: now(),
                source: envelope.senderReplyPort.map { "macos:\($0)" },
                target: target,
                packetId: envelope.packet.packetId,
                messageId: envelope.packet.messageId,
                payloadJson: envelope.packet.payloadJson,
                senderDisplayName: envelope.senderDisplayName,
                senderFingerprint: envelope.senderFingerprint,
                recipientFingerprint: envelope.packet.recipientFingerprint,
                relationshipId: envelope.packet.relationshipId,
                error: "\(error)"
            )
        }
    }
}
