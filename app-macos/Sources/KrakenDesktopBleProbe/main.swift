import AppKit
import Foundation
import KrakenDesktopCore

struct Arguments {
    var durationSeconds: TimeInterval = 20
    var outputPath: String?
    var peerId = "macos-ble-probe"
    var fingerprint = "3C4ED5BA9DB88F9B"
    var displayName = "Kraken Desktop BLE Probe"
}

struct StatusSample: Codable {
    var atEpochMillis: Int64
    var status: MacBleTransportStatus
    var discoveredPeers: [MacBlePeerIdentity]
}

struct RolePlan: Codable {
    var macosPeripheralAdvertises: Bool
    var macosCentralScans: Bool
    var androidExpectedPeripheralAdvertises: Bool
    var androidExpectedCentralScans: Bool
    var note: String

    private enum CodingKeys: String, CodingKey {
        case macosPeripheralAdvertises = "macos_peripheral_advertises"
        case macosCentralScans = "macos_central_scans"
        case androidExpectedPeripheralAdvertises = "android_expected_peripheral_advertises"
        case androidExpectedCentralScans = "android_expected_central_scans"
        case note
    }
}

struct AndroidCompatibility: Codable {
    var serviceUuid: String
    var identityCharacteristicUuid: String
    var packetCharacteristicUuid: String
    var chunkEncoding: String
    var maxGattWriteBytes: Int
    var defaultChunkPayloadBytes: Int

    private enum CodingKeys: String, CodingKey {
        case serviceUuid = "service_uuid"
        case identityCharacteristicUuid = "identity_characteristic_uuid"
        case packetCharacteristicUuid = "packet_characteristic_uuid"
        case chunkEncoding = "chunk_encoding"
        case maxGattWriteBytes = "max_gatt_write_bytes"
        case defaultChunkPayloadBytes = "default_chunk_payload_bytes"
    }
}

struct ProbeReport: Codable {
    var success: Bool
    var peerObserved: Bool
    var startedAtEpochMillis: Int64
    var finishedAtEpochMillis: Int64
    var durationSeconds: TimeInterval
    var localIdentity: MacBlePeerIdentity
    var rolePlan: RolePlan
    var androidCompatibility: AndroidCompatibility
    var statusSamples: [StatusSample]
    var events: [MacBleTransferEvent]
    var finalStatus: MacBleTransportStatus
    var discoveredPeers: [MacBlePeerIdentity]
    var claimBoundary: String

    private enum CodingKeys: String, CodingKey {
        case success
        case peerObserved = "peer_observed"
        case startedAtEpochMillis = "started_at_epoch_millis"
        case finishedAtEpochMillis = "finished_at_epoch_millis"
        case durationSeconds = "duration_seconds"
        case localIdentity = "local_identity"
        case rolePlan = "role_plan"
        case androidCompatibility = "android_compatibility"
        case statusSamples = "status_samples"
        case events
        case finalStatus = "final_status"
        case discoveredPeers = "discovered_peers"
        case claimBoundary = "claim_boundary"
    }
}

func nowMillis() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000)
}

func parseArguments(_ raw: [String]) -> Arguments {
    var args = Arguments()
    var index = 1
    while index < raw.count {
        let key = raw[index]
        let value = index + 1 < raw.count ? raw[index + 1] : ""
        switch key {
        case "--duration-seconds":
            args.durationSeconds = max(1, TimeInterval(value) ?? args.durationSeconds)
            index += 2
        case "--out":
            args.outputPath = value
            index += 2
        case "--peer-id":
            args.peerId = value
            index += 2
        case "--fingerprint":
            args.fingerprint = value
            index += 2
        case "--display-name":
            args.displayName = value
            index += 2
        default:
            fputs("Unknown argument: \(key)\n", stderr)
            exit(2)
        }
    }
    return args
}

func writeReport(_ report: ProbeReport, to path: String?) throws {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    let data = try encoder.encode(report)
    if let path {
        let url = URL(fileURLWithPath: path)
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try data.write(to: url)
    }
    print(String(data: data, encoding: .utf8) ?? "{}")
}

final class ProbeAppDelegate: NSObject, NSApplicationDelegate {
    private let args: Arguments
    private let localIdentity: MacBlePeerIdentity
    private let transport = MacBleTransport(useMainQueue: true)
    private let lock = NSLock()
    private var events: [MacBleTransferEvent] = []
    private var samples: [StatusSample] = []
    private var startedAt: Int64 = 0
    private var timer: Timer?
    private var window: NSWindow?

    init(args: Arguments) {
        self.args = args
        self.localIdentity = MacBlePeerIdentity(
            peerId: args.peerId,
            fingerprint: args.fingerprint,
            displayName: args.displayName
        )
        super.init()
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
        showProbeWindow()
        startedAt = nowMillis()
        transport.start(localIdentity: localIdentity) { [weak self] event in
            self?.lock.lock()
            self?.events.append(event)
            self?.lock.unlock()
        }
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            self?.sampleStatus()
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + args.durationSeconds) { [weak self] in
            self?.finish()
        }
    }

    private func showProbeWindow() {
        let label = NSTextField(labelWithString: "Kraken BLE probe is checking CoreBluetooth roles.")
        label.alignment = .center
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.textColor = .secondaryLabelColor
        label.frame = NSRect(x: 20, y: 34, width: 380, height: 24)

        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 420, height: 92),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )
        window.title = "Kraken BLE Probe"
        window.center()
        window.contentView = NSView(frame: window.contentRect(forFrameRect: window.frame))
        window.contentView?.addSubview(label)
        window.makeKeyAndOrderFront(nil)
        self.window = window
    }

    private func sampleStatus() {
        let sample = StatusSample(
            atEpochMillis: nowMillis(),
            status: transport.currentStatus(),
            discoveredPeers: transport.discoveredPeerIdentities()
        )
        lock.lock()
        samples.append(sample)
        lock.unlock()
    }

    private func finish() {
        timer?.invalidate()
        timer = nil
        sampleStatus()

        var finalStatus = transport.currentStatus()
        let discoveredPeers = transport.discoveredPeerIdentities()
        transport.stop()

        lock.lock()
        let eventSnapshot = events
        let sampleSnapshot = samples
        lock.unlock()

        if finalStatus.authorizationState != "allowed" {
            finalStatus.lastError = "ble-authorization-\(finalStatus.authorizationState)"
        } else if finalStatus.centralState == "starting" || finalStatus.peripheralState == "starting" ||
            finalStatus.centralState == "waiting-authorization" || finalStatus.peripheralState == "waiting-authorization" {
            finalStatus.lastError = "ble-state-callback-timeout"
        }
        let peerObserved = !discoveredPeers.isEmpty || finalStatus.discoveredPeerCount > 0
        let startupSucceeded = finalStatus.centralState == "scanning" &&
            finalStatus.peripheralState == "advertising"
        let report = ProbeReport(
            success: startupSucceeded,
            peerObserved: peerObserved,
            startedAtEpochMillis: startedAt,
            finishedAtEpochMillis: nowMillis(),
            durationSeconds: args.durationSeconds,
            localIdentity: localIdentity,
            rolePlan: RolePlan(
                macosPeripheralAdvertises: true,
                macosCentralScans: true,
                androidExpectedPeripheralAdvertises: true,
                androidExpectedCentralScans: true,
                note: "macOS starts both CoreBluetooth roles so it can interoperate with Android BleGattTransport, which also advertises and scans the Kraken service."
            ),
            androidCompatibility: AndroidCompatibility(
                serviceUuid: MacBleConstants.serviceUuidString,
                identityCharacteristicUuid: MacBleConstants.identityCharacteristicUuidString,
                packetCharacteristicUuid: MacBleConstants.packetCharacteristicUuidString,
                chunkEncoding: "JSON snake_case BleFrameChunk with payload_base64 KrakenPacket chunks",
                maxGattWriteBytes: BleFrameCodec.maxGattWriteBytes,
                defaultChunkPayloadBytes: BleFrameCodec.defaultChunkPayloadBytes
            ),
            statusSamples: sampleSnapshot,
            events: eventSnapshot,
            finalStatus: finalStatus,
            discoveredPeers: discoveredPeers,
            claimBoundary: "This artifact records macOS CoreBluetooth role startup state and Android-compatible BLE UUID/framing configuration. Role startup is proven only when success is true. Peer discovery or delivery is proven only when peer_observed is true or accepted/queued events are present."
        )

        do {
            try writeReport(report, to: args.outputPath)
            exit(report.success ? 0 : 1)
        } catch {
            fputs("write BLE probe report failed: \(error)\n", stderr)
            exit(1)
        }
    }
}

let args = parseArguments(CommandLine.arguments)
let app = NSApplication.shared
let delegate = ProbeAppDelegate(args: args)
app.delegate = delegate
app.run()
