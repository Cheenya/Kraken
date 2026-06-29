import Foundation
import KrakenDesktopCore

struct DesktopEvidenceWriter {
    private let repoRoot: URL

    init(repoRoot: URL = DesktopRepoPaths.resolveRepoRoot()) {
        self.repoRoot = repoRoot
    }

    func writeLanTransportEvidence(
        listenerPort: Int?,
        endpoint: MacLanEndpoint,
        selectedRelationship: Relationship?,
        selectedRoute: PeerRouteSnapshot?,
        events: [MacLanTransferEvent],
        boundary: String
    ) throws -> URL {
        let stamp = Self.timestamp()
        let directory = repoRoot
            .appendingPathComponent("artifacts", isDirectory: true)
            .appendingPathComponent("macos-lan-transport", isDirectory: true)
            .appendingPathComponent(stamp, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)

        let payload: [String: Any] = [
            "generated_at": Self.isoTimestamp(),
            "claim_boundary": boundary,
            "listener": [
                "port": listenerPort.map { $0 as Any } ?? NSNull(),
                "mode": "macos-lan-tcp-listener",
            ],
            "target": [
                "host": endpoint.host,
                "port": endpoint.port,
                "fingerprint": endpoint.fingerprint,
                "display_name": Self.jsonString(endpoint.displayName),
            ],
            "selected_peer": [
                "relationship_id": Self.jsonString(selectedRelationship?.relationshipId),
                "display_name": Self.jsonString(selectedRelationship?.peerDisplayName),
                "fingerprint": Self.jsonString(selectedRelationship?.peerFingerprint),
                "state": Self.jsonString(selectedRelationship?.state.rawValue),
            ],
            "selected_route": [
                "kind": Self.jsonString(selectedRoute?.kind.rawValue),
                "transport_id": Self.jsonString(selectedRoute?.transportId),
                "bandwidth_class": Self.jsonString(selectedRoute?.bandwidthClass.rawValue),
                "hop_count": Self.jsonInt(selectedRoute?.hopCount),
            ],
            "events": events.map(Self.eventJson),
            "not_closed": [
                "ble_corebluetooth_transport",
                "wifi_direct_native_android_style_transport_on_macos",
                "production_crypto_security",
            ],
        ]
        let jsonData = try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys])
        try jsonData.write(to: directory.appendingPathComponent("macos_lan_transport.json"))
        try markdown(
            generatedAt: payload["generated_at"] as? String ?? "",
            listenerPort: listenerPort,
            endpoint: endpoint,
            selectedRelationship: selectedRelationship,
            selectedRoute: selectedRoute,
            events: events,
            boundary: boundary
        ).write(
            to: directory.appendingPathComponent("macos_lan_transport.md"),
            atomically: true,
            encoding: .utf8
        )
        return directory
    }

    func writeBleTransportEvidence(
        status: MacBleTransportStatus,
        selectedRelationship: Relationship?,
        selectedRoute: PeerRouteSnapshot?,
        events: [MacBleTransferEvent],
        boundary: String
    ) throws -> URL {
        let stamp = Self.timestamp()
        let directory = repoRoot
            .appendingPathComponent("artifacts", isDirectory: true)
            .appendingPathComponent("macos-ble-transport", isDirectory: true)
            .appendingPathComponent(stamp, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)

        let payload: [String: Any] = [
            "generated_at": Self.isoTimestamp(),
            "claim_boundary": boundary,
            "status": [
                "mode_id": status.modeId,
                "service_uuid": status.serviceUuid,
                "identity_characteristic_uuid": status.identityCharacteristicUuid,
                "packet_characteristic_uuid": status.packetCharacteristicUuid,
                "authorization_state": status.authorizationState,
                "central_state": status.centralState,
                "peripheral_state": status.peripheralState,
                "discovered_peer_count": status.discoveredPeerCount,
                "inbound_chunks": status.inboundChunks,
                "outbound_chunks": status.outboundChunks,
                "reassembled_packets": status.reassembledPackets,
                "last_error": Self.jsonString(status.lastError),
            ],
            "selected_peer": [
                "relationship_id": Self.jsonString(selectedRelationship?.relationshipId),
                "display_name": Self.jsonString(selectedRelationship?.peerDisplayName),
                "fingerprint": Self.jsonString(selectedRelationship?.peerFingerprint),
                "state": Self.jsonString(selectedRelationship?.state.rawValue),
            ],
            "selected_route": [
                "kind": Self.jsonString(selectedRoute?.kind.rawValue),
                "transport_id": Self.jsonString(selectedRoute?.transportId),
                "bandwidth_class": Self.jsonString(selectedRoute?.bandwidthClass.rawValue),
                "hop_count": Self.jsonInt(selectedRoute?.hopCount),
            ],
            "events": events.map(Self.bleEventJson),
            "not_closed": [
                "phone_peer_discovery_unless_events_or_peer_count_prove_it",
                "native_android_style_wifi_direct_transport_on_macos",
                "production_crypto_security",
            ],
        ]
        let jsonData = try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys])
        try jsonData.write(to: directory.appendingPathComponent("macos_ble_transport.json"))
        try bleMarkdown(
            generatedAt: payload["generated_at"] as? String ?? "",
            status: status,
            selectedRelationship: selectedRelationship,
            selectedRoute: selectedRoute,
            events: events,
            boundary: boundary
        ).write(
            to: directory.appendingPathComponent("macos_ble_transport.md"),
            atomically: true,
            encoding: .utf8
        )
        return directory
    }

    private static func eventJson(_ event: MacLanTransferEvent) -> [String: Any] {
        [
            "id": event.id,
            "direction": event.direction.rawValue,
            "status": event.status.rawValue,
            "at_epoch_millis": event.atEpochMillis,
            "source": Self.jsonString(event.source),
            "target": Self.jsonString(event.target),
            "packet_id": Self.jsonString(event.packetId),
            "message_id": Self.jsonString(event.messageId),
            "payload_json": Self.jsonString(event.payloadJson),
            "sender_fingerprint": Self.jsonString(event.senderFingerprint),
            "recipient_fingerprint": Self.jsonString(event.recipientFingerprint),
            "relationship_id": Self.jsonString(event.relationshipId),
            "error": Self.jsonString(event.error),
        ]
    }

    private static func bleEventJson(_ event: MacBleTransferEvent) -> [String: Any] {
        [
            "id": event.id,
            "direction": event.direction.rawValue,
            "status": event.status.rawValue,
            "at_epoch_millis": event.atEpochMillis,
            "peer_fingerprint": Self.jsonString(event.peerFingerprint),
            "packet_id": Self.jsonString(event.packetId),
            "message_id": Self.jsonString(event.messageId),
            "payload_json": Self.jsonString(event.payloadJson),
            "sender_display_name": Self.jsonString(event.senderDisplayName),
            "sender_fingerprint": Self.jsonString(event.senderFingerprint),
            "recipient_fingerprint": Self.jsonString(event.recipientFingerprint),
            "relationship_id": Self.jsonString(event.relationshipId),
            "chunk_count": Self.jsonInt(event.chunkCount),
            "error": Self.jsonString(event.error),
        ]
    }

    private func markdown(
        generatedAt: String,
        listenerPort: Int?,
        endpoint: MacLanEndpoint,
        selectedRelationship: Relationship?,
        selectedRoute: PeerRouteSnapshot?,
        events: [MacLanTransferEvent],
        boundary: String
    ) -> String {
        var lines = [
            "# Evidence macOS LAN",
            "",
            "Сформировано: `\(generatedAt)`.",
            "",
            "## Область проверки",
            "",
            boundary,
            "",
            "## Адреса",
            "",
            "- приёмник macOS: `\(listenerPort.map(String.init) ?? "not-running")`.",
            "- цель: `\(endpoint.host):\(endpoint.port)` / `\(endpoint.fingerprint)`.",
            "- выбранное устройство: `\(selectedRelationship?.peerDisplayName ?? "-")` / `\(selectedRelationship?.peerFingerprint ?? "-")`.",
            "- выбранный маршрут: `\(selectedRoute?.kind.rawValue ?? "-")` / `\(selectedRoute?.transportId ?? "-")`.",
            "",
            "## События",
            "",
        ]
        if events.isEmpty {
            lines.append("- События не записаны.")
        } else {
            lines.append("| Направление | Статус | Пакет | Сообщение | Устройство | Данные | Ошибка |")
            lines.append("| --- | --- | --- | --- | --- | --- | --- |")
            for event in events {
                lines.append(
                    "| `\(event.direction.rawValue)` | `\(event.status.rawValue)` | `\(event.packetId ?? "-")` | `\(event.messageId ?? "-")` | `\(event.senderFingerprint ?? "-") -> \(event.recipientFingerprint ?? "-")` | `\(Self.markdownCell(event.payloadJson))` | `\(event.error ?? "-")` |"
                )
            }
        }
        lines += [
            "",
            "## Что не закрывает этот артефакт",
            "",
            "- BLE/CoreBluetooth-транспорт.",
            "- Android Wi-Fi Direct на macOS.",
            "- Полную криптографическую проверку приложения.",
            "",
        ]
        return lines.joined(separator: "\n")
    }

    private func bleMarkdown(
        generatedAt: String,
        status: MacBleTransportStatus,
        selectedRelationship: Relationship?,
        selectedRoute: PeerRouteSnapshot?,
        events: [MacBleTransferEvent],
        boundary: String
    ) -> String {
        var lines = [
            "# Evidence macOS BLE",
            "",
            "Сформировано: `\(generatedAt)`.",
            "",
            "## Область проверки",
            "",
            boundary,
            "",
            "## Статус CoreBluetooth",
            "",
            "- режим: `\(status.modeId)`.",
            "- service UUID: `\(status.serviceUuid)`.",
            "- UUID профиля: `\(status.identityCharacteristicUuid)`.",
            "- UUID пакета: `\(status.packetCharacteristicUuid)`.",
            "- разрешение: `\(status.authorizationState)`.",
            "- central: `\(status.centralState)`.",
            "- peripheral: `\(status.peripheralState)`.",
            "- найдено устройств: `\(status.discoveredPeerCount)`.",
            "- фрагменты вход/выход: `\(status.inboundChunks)/\(status.outboundChunks)`.",
            "- собранные пакеты: `\(status.reassembledPackets)`.",
            "- последняя ошибка: `\(status.lastError ?? "-")`.",
            "",
            "## Выбранное устройство",
            "",
            "- выбранное устройство: `\(selectedRelationship?.peerDisplayName ?? "-")` / `\(selectedRelationship?.peerFingerprint ?? "-")`.",
            "- выбранный маршрут: `\(selectedRoute?.kind.rawValue ?? "-")` / `\(selectedRoute?.transportId ?? "-")`.",
            "",
            "## События",
            "",
        ]
        if events.isEmpty {
            lines.append("- События не записаны.")
        } else {
            lines.append("| Направление | Статус | Пакет | Сообщение | Устройство | Фрагменты | Данные | Ошибка |")
            lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
            for event in events {
                lines.append(
                    "| `\(event.direction.rawValue)` | `\(event.status.rawValue)` | `\(event.packetId ?? "-")` | `\(event.messageId ?? "-")` | `\(event.senderFingerprint ?? event.peerFingerprint ?? "-") -> \(event.recipientFingerprint ?? "-")` | `\(event.chunkCount.map(String.init) ?? "-")` | `\(Self.markdownCell(event.payloadJson))` | `\(event.error ?? "-")` |"
                )
            }
        }
        lines += [
            "",
            "## Что не закрывает этот артефакт",
            "",
            "- Обнаружение телефона, если это не подтверждают счётчики найденных устройств или события accepted/queued.",
            "- Android Wi-Fi Direct на macOS.",
            "- Полную криптографическую проверку приложения.",
            "",
        ]
        return lines.joined(separator: "\n")
    }

    private static func markdownCell(_ value: String?) -> String {
        guard let value, !value.isEmpty else { return "-" }
        let collapsed = value
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "|", with: "\\|")
        if collapsed.count <= 96 {
            return collapsed
        }
        return "\(collapsed.prefix(93))..."
    }

    private static func jsonString(_ value: String?) -> Any {
        value ?? NSNull()
    }

    private static func jsonInt(_ value: Int?) -> Any {
        value.map { $0 as Any } ?? NSNull()
    }

    private static func timestamp() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        return formatter.string(from: Date())
    }

    private static func isoTimestamp() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withTimeZone]
        return formatter.string(from: Date())
    }
}
