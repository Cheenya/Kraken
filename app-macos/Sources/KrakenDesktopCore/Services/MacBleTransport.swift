import Foundation

#if canImport(CoreBluetooth)
@preconcurrency import CoreBluetooth

public final class MacBleTransport: NSObject {
    private struct DiscoveredBlePeer {
        var identity: MacBlePeerIdentity?
        var peripheral: CBPeripheral
        var packetCharacteristic: CBCharacteristic?
    }

    private let queue: DispatchQueue
    private let usesMainQueue: Bool
    private let now: @Sendable () -> Int64
    private let reassembler: BleFrameReassembler
    private var packetPolicy = KrakenPacketPolicyValidator()
    private var centralManager: CBCentralManager?
    private var peripheralManager: CBPeripheralManager?
    private var identityCharacteristic: CBMutableCharacteristic?
    private var packetCharacteristic: CBMutableCharacteristic?
    private var localIdentity: MacBlePeerIdentity?
    private var discoveredPeers: [UUID: DiscoveredBlePeer] = [:]
    private var status = MacBleTransportStatus()
    private var onEvent: (@Sendable (MacBleTransferEvent) -> Void)?

    public override convenience init() {
        self.init(now: { Int64(Date().timeIntervalSince1970 * 1000) })
    }

    public convenience init(useMainQueue: Bool) {
        self.init(
            now: { Int64(Date().timeIntervalSince1970 * 1000) },
            useMainQueue: useMainQueue
        )
    }

    public init(
        now: @escaping @Sendable () -> Int64,
        useMainQueue: Bool = false
    ) {
        self.queue = useMainQueue ? .main : DispatchQueue(label: "kraken.desktop.ble.transport")
        self.usesMainQueue = useMainQueue
        self.now = now
        self.reassembler = BleFrameReassembler(now: now)
        super.init()
    }

    public func start(
        localIdentity: MacBlePeerIdentity,
        onEvent: @escaping @Sendable (MacBleTransferEvent) -> Void
    ) {
        asyncOnQueue {
            self.localIdentity = localIdentity
            self.onEvent = onEvent
            self.status.authorizationState = Self.authorizationStateName()
            let authorizationPending = self.status.authorizationState == "not-determined"
            self.status.centralState = authorizationPending ? "waiting-authorization" : "starting"
            self.status.peripheralState = authorizationPending ? "waiting-authorization" : "starting"
            if !Self.bluetoothAuthorizationAllowsStartup {
                self.status.centralState = "authorization-\(self.status.authorizationState)"
                self.status.peripheralState = "authorization-\(self.status.authorizationState)"
                self.status.lastError = "ble-authorization-\(self.status.authorizationState)"
                return
            }
            self.centralManager = CBCentralManager(
                delegate: self,
                queue: self.queue,
                options: [CBCentralManagerOptionShowPowerAlertKey: true]
            )
            self.peripheralManager = CBPeripheralManager(
                delegate: self,
                queue: self.queue,
                options: [CBPeripheralManagerOptionShowPowerAlertKey: true]
            )
        }
    }

    public func stop() {
        asyncOnQueue {
            if let centralManager = self.centralManager,
               centralManager.state == .poweredOn,
               centralManager.isScanning {
                centralManager.stopScan()
            }
            if let peripheralManager = self.peripheralManager,
               peripheralManager.state == .poweredOn {
                peripheralManager.stopAdvertising()
                peripheralManager.removeAllServices()
            }
            self.discoveredPeers.removeAll()
            self.centralManager = nil
            self.peripheralManager = nil
            self.identityCharacteristic = nil
            self.packetCharacteristic = nil
            self.status.centralState = "stopped"
            self.status.peripheralState = "stopped"
            self.status.authorizationState = Self.authorizationStateName()
            self.status.discoveredPeerCount = 0
        }
    }

    public func currentStatus() -> MacBleTransportStatus {
        syncOnQueue {
            status.authorizationState = Self.authorizationStateName()
            if !Self.bluetoothAuthorizationAllowsStartup &&
                (status.centralState == "starting" || status.peripheralState == "starting") {
                status.centralState = "authorization-\(status.authorizationState)"
                status.peripheralState = "authorization-\(status.authorizationState)"
                status.lastError = "ble-authorization-\(status.authorizationState)"
            } else if status.authorizationState == "not-determined" &&
                (status.centralState == "starting" || status.centralState == "waiting-authorization" ||
                    status.peripheralState == "starting" || status.peripheralState == "waiting-authorization") {
                status.centralState = "waiting-authorization"
                status.peripheralState = "waiting-authorization"
                status.lastError = "ble-authorization-not-determined"
            }
            return status
        }
    }

    public func discoveredPeerIdentities() -> [MacBlePeerIdentity] {
        syncOnQueue {
            discoveredPeers.values
                .compactMap(\.identity)
                .sorted { $0.fingerprint < $1.fingerprint }
        }
    }

    public func send(envelope: LanFrameEnvelope, toPeerFingerprint fingerprint: String) -> MacBleTransferEvent {
        syncOnQueue {
            guard let peer = discoveredPeers.values.first(where: { $0.identity?.fingerprint == fingerprint }),
                  let characteristic = peer.packetCharacteristic else {
                status.lastError = "ble-peer-not-ready"
                return MacBleTransferEvent(
                    direction: .outbound,
                    status: .failed,
                    atEpochMillis: now(),
                    peerFingerprint: fingerprint,
                    packetId: envelope.packet.packetId,
                    messageId: envelope.packet.messageId,
                    error: "ble-peer-not-ready"
                )
            }
            do {
                let chunks = try BleFrameCodec.encodeChunks(
                    packet: envelope.packet,
                    senderPeerId: envelope.senderPeerId,
                    senderFingerprint: envelope.senderFingerprint,
                    senderDisplayName: envelope.senderDisplayName
                )
                chunks.forEach { chunk in
                    peer.peripheral.writeValue(chunk, for: characteristic, type: .withResponse)
                }
                status.outboundChunks += chunks.count
                status.lastError = nil
                return MacBleTransferEvent(
                    direction: .outbound,
                    status: .queued,
                    atEpochMillis: now(),
                    peerFingerprint: fingerprint,
                    packetId: envelope.packet.packetId,
                    messageId: envelope.packet.messageId,
                    chunkCount: chunks.count
                )
            } catch {
                status.lastError = "ble-send:\(error)"
                return MacBleTransferEvent(
                    direction: .outbound,
                    status: .failed,
                    atEpochMillis: now(),
                    peerFingerprint: fingerprint,
                    packetId: envelope.packet.packetId,
                    messageId: envelope.packet.messageId,
                    error: "ble-send:\(error)"
                )
            }
        }
    }

    private func asyncOnQueue(_ work: @escaping () -> Void) {
        if usesMainQueue && Thread.isMainThread {
            work()
        } else {
            queue.async(execute: work)
        }
    }

    private func syncOnQueue<T>(_ work: () -> T) -> T {
        if usesMainQueue && Thread.isMainThread {
            return work()
        }
        return queue.sync(execute: work)
    }

    private func setupPeripheralService() {
        let identity = CBMutableCharacteristic(
            type: Self.identityCharacteristicUuid,
            properties: [.read],
            value: nil,
            permissions: [.readable]
        )
        let packet = CBMutableCharacteristic(
            type: Self.packetCharacteristicUuid,
            properties: [.write],
            value: nil,
            permissions: [.writeable]
        )
        let service = CBMutableService(type: Self.serviceUuid, primary: true)
        service.characteristics = [identity, packet]
        identityCharacteristic = identity
        packetCharacteristic = packet
        peripheralManager?.removeAllServices()
        peripheralManager?.add(service)
    }

    private func startAdvertising() {
        peripheralManager?.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [Self.serviceUuid],
        ])
        status.peripheralState = "advertising"
    }

    private func startScanning() {
        centralManager?.scanForPeripherals(
            withServices: [Self.serviceUuid],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
        status.centralState = "scanning"
    }

    private func localIdentityBytes() -> Data? {
        guard let localIdentity else { return nil }
        return try? JSONEncoder().encode(localIdentity)
    }

    private func handleWrite(_ value: Data) throws -> LanFrameEnvelope? {
        let chunk = try BleFrameCodec.decodeChunk(value)
        status.inboundChunks += 1
        let envelope = try reassembler.accept(chunk)
        if let envelope {
            try packetPolicy.acceptInbound(envelope.packet, nowMillis: now())
            status.reassembledPackets += 1
        }
        return envelope
    }

    private func updateDiscoveredPeerCount() {
        status.discoveredPeerCount = discoveredPeers.values
            .compactMap(\.identity?.fingerprint)
            .filter { !$0.isEmpty }
            .count
    }

    private func stateName(_ state: CBManagerState) -> String {
        switch state {
        case .unknown: "unknown"
        case .resetting: "resetting"
        case .unsupported: "unsupported"
        case .unauthorized: "unauthorized"
        case .poweredOff: "powered-off"
        case .poweredOn: "powered-on"
        @unknown default: "unknown"
        }
    }

    private static var bluetoothAuthorizationAllowsStartup: Bool {
        switch CBManager.authorization {
        case .allowedAlways, .notDetermined: true
        case .denied, .restricted: false
        @unknown default: false
        }
    }

    private static func authorizationStateName() -> String {
        switch CBManager.authorization {
        case .allowedAlways: "allowed"
        case .notDetermined: "not-determined"
        case .denied: "denied"
        case .restricted: "restricted"
        @unknown default: "unknown"
        }
    }

    private static let serviceUuid = CBUUID(string: MacBleConstants.serviceUuidString)
    private static let identityCharacteristicUuid = CBUUID(string: MacBleConstants.identityCharacteristicUuidString)
    private static let packetCharacteristicUuid = CBUUID(string: MacBleConstants.packetCharacteristicUuidString)
}

extension MacBleTransport: CBPeripheralManagerDelegate {
    public func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        status.authorizationState = Self.authorizationStateName()
        guard peripheral.state == .poweredOn else {
            status.peripheralState = stateName(peripheral.state)
            status.lastError = peripheral.state == .unsupported
                ? "ble-peripheral-unsupported"
                : (peripheral.state == .unauthorized ? "ble-authorization-\(status.authorizationState)" : status.lastError)
            return
        }
        setupPeripheralService()
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error {
            status.peripheralState = "add-service-failed"
            status.lastError = "ble-add-service:\(error.localizedDescription)"
            return
        }
        startAdvertising()
    }

    public func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error {
            status.peripheralState = "advertising-failed"
            status.lastError = "ble-advertising:\(error.localizedDescription)"
        } else {
            status.peripheralState = "advertising"
            status.lastError = nil
        }
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        guard request.characteristic.uuid == Self.identityCharacteristicUuid,
              let value = localIdentityBytes(),
              request.offset <= value.count else {
            peripheral.respond(to: request, withResult: .requestNotSupported)
            return
        }
        request.value = value.suffix(from: request.offset)
        peripheral.respond(to: request, withResult: .success)
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        var result: CBATTError.Code = .success
        for request in requests {
            guard request.characteristic.uuid == Self.packetCharacteristicUuid,
                  request.offset == 0,
                  let value = request.value else {
                result = .requestNotSupported
                continue
            }
            do {
                if let envelope = try handleWrite(value) {
                    onEvent?(
                        MacBleTransferEvent(
                            direction: .inbound,
                            status: .accepted,
                            atEpochMillis: now(),
                            peerFingerprint: envelope.senderFingerprint,
                            packetId: envelope.packet.packetId,
                            messageId: envelope.packet.messageId,
                            payloadJson: envelope.packet.payloadJson,
                            senderDisplayName: envelope.senderDisplayName,
                            senderFingerprint: envelope.packet.senderFingerprint,
                            recipientFingerprint: envelope.packet.recipientFingerprint,
                            relationshipId: envelope.packet.relationshipId
                        )
                    )
                }
            } catch {
                status.lastError = "ble-write:\(error)"
                result = .unlikelyError
            }
        }
        if let request = requests.first {
            peripheral.respond(to: request, withResult: result)
        }
    }
}

extension MacBleTransport: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        status.authorizationState = Self.authorizationStateName()
        guard central.state == .poweredOn else {
            status.centralState = stateName(central.state)
            status.lastError = central.state == .unsupported
                ? "ble-central-unsupported"
                : (central.state == .unauthorized ? "ble-authorization-\(status.authorizationState)" : status.lastError)
            return
        }
        startScanning()
    }

    public func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        if discoveredPeers[peripheral.identifier] == nil {
            discoveredPeers[peripheral.identifier] = DiscoveredBlePeer(
                identity: nil,
                peripheral: peripheral,
                packetCharacteristic: nil
            )
        }
        peripheral.delegate = self
        central.connect(peripheral)
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([Self.serviceUuid])
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        status.lastError = "ble-connect:\(error?.localizedDescription ?? "failed")"
    }
}

extension MacBleTransport: CBPeripheralDelegate {
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            status.lastError = "ble-services:\(error.localizedDescription)"
            return
        }
        peripheral.services?
            .filter { $0.uuid == Self.serviceUuid }
            .forEach {
                peripheral.discoverCharacteristics(
                    [Self.identityCharacteristicUuid, Self.packetCharacteristicUuid],
                    for: $0
                )
            }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error {
            status.lastError = "ble-characteristics:\(error.localizedDescription)"
            return
        }
        var peer = discoveredPeers[peripheral.identifier] ?? DiscoveredBlePeer(
            identity: nil,
            peripheral: peripheral,
            packetCharacteristic: nil
        )
        service.characteristics?.forEach { characteristic in
            if characteristic.uuid == Self.packetCharacteristicUuid {
                peer.packetCharacteristic = characteristic
            } else if characteristic.uuid == Self.identityCharacteristicUuid {
                peripheral.readValue(for: characteristic)
            }
        }
        discoveredPeers[peripheral.identifier] = peer
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error {
            status.lastError = "ble-read-identity:\(error.localizedDescription)"
            return
        }
        guard characteristic.uuid == Self.identityCharacteristicUuid,
              let value = characteristic.value,
              let identity = try? JSONDecoder().decode(MacBlePeerIdentity.self, from: value),
              identity.fingerprint != localIdentity?.fingerprint else {
            return
        }
        var peer = discoveredPeers[peripheral.identifier] ?? DiscoveredBlePeer(
            identity: nil,
            peripheral: peripheral,
            packetCharacteristic: nil
        )
        peer.identity = identity
        discoveredPeers[peripheral.identifier] = peer
        updateDiscoveredPeerCount()
        status.lastError = nil
    }

    public func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error {
            status.lastError = "ble-write:\(error.localizedDescription)"
        }
    }
}

#else
public final class MacBleTransport {
    public init() {}

    public convenience init(useMainQueue: Bool) {
        self.init()
    }

    public func start(
        localIdentity: MacBlePeerIdentity,
        onEvent: @escaping @Sendable (MacBleTransferEvent) -> Void
    ) {}

    public func stop() {}

    public func currentStatus() -> MacBleTransportStatus {
        MacBleTransportStatus(
            centralState: "unsupported",
            peripheralState: "unsupported",
            authorizationState: "unsupported",
            lastError: "corebluetooth-unavailable"
        )
    }

    public func discoveredPeerIdentities() -> [MacBlePeerIdentity] {
        []
    }

    public func send(envelope: LanFrameEnvelope, toPeerFingerprint fingerprint: String) -> MacBleTransferEvent {
        MacBleTransferEvent(
            direction: .outbound,
            status: .failed,
            atEpochMillis: Int64(Date().timeIntervalSince1970 * 1000),
            peerFingerprint: fingerprint,
            packetId: envelope.packet.packetId,
            messageId: envelope.packet.messageId,
            error: "corebluetooth-unavailable"
        )
    }
}
#endif
