import KrakenDesktopCore
import SwiftUI

struct MeshStatusView: View {
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HeaderBlock(
                title: "Связь",
                subtitle: "LAN/ADB-мост для macOS <-> Android, Bluetooth и границы Wi-Fi Direct на macOS."
            )

            Table(store.state.routes, selection: $store.selectedRelationshipId) {
                TableColumn("Устройство") { route in
                    Text(peerName(for: route.relationshipId))
                }
                TableColumn("Маршрут") { route in
                    Label(route.kind.title, systemImage: routeIcon(route.kind))
                }
                TableColumn("Транспорт") { route in
                    Text(route.transportId ?? "-")
                }
                TableColumn("Канал") { route in
                    Text(bandwidthTitle(route.bandwidthClass))
                }
                TableColumn("Прыжки") { route in
                    Text(route.hopCount.map(String.init) ?? "-")
                }
            }

            HStack {
                Button {
                    store.cycleSelectedRoute()
                } label: {
                    Label("Сменить выбранный маршрут", systemImage: "arrow.triangle.2.circlepath")
                }
                .disabled(store.selectedRelationship == nil)

                Text(store.state.lastEvent)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            SectionBlock(title: "Проверка Xiaomi / macOS") {
                HStack {
                    Button {
                        store.refreshAdbDevices()
                    } label: {
                        Label("Устройства ADB", systemImage: "iphone.gen3.radiowaves.left.and.right")
                    }

                    Button {
                        store.runDesktopRelayPreflight()
                    } label: {
                        Label("Проверить мост", systemImage: "network")
                    }

                    if store.probeRunning {
                        ProgressView()
                            .controlSize(.small)
                    }
                }

                if let result = store.latestProbeResult {
                    VStack(alignment: .leading, spacing: 8) {
                        Label(
                            result.succeeded ? "успешно" : "ошибка",
                            systemImage: result.succeeded ? "checkmark.circle" : "xmark.octagon"
                        )
                        .foregroundStyle(result.succeeded ? .green : .red)

                        Text(result.command)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .textSelection(.enabled)

                        ScrollView {
                            Text(result.output.isEmpty ? "вывода нет" : result.output)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .frame(minHeight: 80, maxHeight: 150)
                    }
                }
            }

            SectionBlock(title: "LAN/ADB-мост") {
                Grid(alignment: .leading, horizontalSpacing: 10, verticalSpacing: 10) {
                    GridRow {
                        Text("Адрес")
                            .foregroundStyle(.secondary)
                        TextField("127.0.0.1", text: $store.lanTargetHost)
                            .textFieldStyle(.roundedBorder)
                        Text("Порт")
                            .foregroundStyle(.secondary)
                        TextField("54035", text: $store.lanTargetPort)
                            .textFieldStyle(.roundedBorder)
                            .frame(maxWidth: 90)
                    }
                    GridRow {
                        Text("Отпечаток")
                            .foregroundStyle(.secondary)
                        TextField("Отпечаток Xiaomi", text: $store.lanTargetFingerprint)
                            .textFieldStyle(.roundedBorder)
                        Text("Порт приёма")
                            .foregroundStyle(.secondary)
                        TextField("43191", text: $store.lanListenPort)
                            .textFieldStyle(.roundedBorder)
                            .frame(maxWidth: 90)
                    }
                    GridRow {
                        Text("Наш отпечаток")
                            .foregroundStyle(.secondary)
                        TextField("Отпечаток этого Mac", text: $store.lanLocalFingerprint)
                            .textFieldStyle(.roundedBorder)
                        Text("ID устройства")
                            .foregroundStyle(.secondary)
                        TextField("macos-desktop", text: $store.lanLocalPeerId)
                            .textFieldStyle(.roundedBorder)
                            .frame(maxWidth: 150)
                    }
                }

                TextField("Текст сообщения", text: $store.lanBody)
                    .textFieldStyle(.roundedBorder)

                HStack {
                    Button {
                        store.startLanListener()
                    } label: {
                        Label("Включить приём", systemImage: "antenna.radiowaves.left.and.right")
                    }
                    .disabled(store.lanListenerRunning)

                    Button {
                        store.stopLanListener()
                    } label: {
                        Label("Остановить", systemImage: "stop.circle")
                    }
                    .disabled(!store.lanListenerRunning)

                    Button {
                        store.sendLanFrameToTarget()
                    } label: {
                        Label("Отправить LAN-кадр", systemImage: "paperplane")
                    }

                    Button {
                        store.saveLanEvidence()
                    } label: {
                        Label("Сохранить артефакт", systemImage: "externaldrive")
                    }
                }

                Text("Приём: \(store.lanListenerRunning ? "включён" : "остановлен") \(store.lanListenerPort.map { ":\($0)" } ?? "")")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let path = store.lastEvidencePath {
                    Text(path)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }

                LanEventsTable(events: store.lanEvents)
            }

            SectionBlock(title: "Bluetooth") {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Button {
                            store.startBleTransport()
                        } label: {
                            Label("Включить Bluetooth", systemImage: "dot.radiowaves.left.and.right")
                        }
                        .disabled(store.bleRunning)

                        Button {
                            store.stopBleTransport()
                        } label: {
                            Label("Остановить", systemImage: "stop.circle")
                        }
                        .disabled(!store.bleRunning)

                        Button {
                            store.refreshBleStatus()
                        } label: {
                            Label("Обновить", systemImage: "arrow.clockwise")
                        }

                        if store.bleStatus.authorizationState != "allowed" {
                            Button {
                                store.openBluetoothPrivacySettings()
                            } label: {
                                Label("Разрешить в macOS", systemImage: "lock.open")
                            }
                        }

                        Button {
                            store.saveBleEvidence()
                        } label: {
                            Label("Сохранить артефакт", systemImage: "externaldrive")
                        }
                    }

                    KeyValueGrid(items: [
                        ("Сервис UUID", store.bleStatus.serviceUuid),
                        ("Личность UUID", store.bleStatus.identityCharacteristicUuid),
                        ("Пакет UUID", store.bleStatus.packetCharacteristicUuid),
                        ("Доступ macOS", bleAuthorizationTitle(store.bleStatus.authorizationState)),
                        ("Центральная роль", bleStateTitle(store.bleStatus.centralState)),
                        ("Периферийная роль", bleStateTitle(store.bleStatus.peripheralState)),
                        ("Устройства", "\(store.bleStatus.discoveredPeerCount)"),
                        ("Фрагменты вход/выход", "\(store.bleStatus.inboundChunks)/\(store.bleStatus.outboundChunks)"),
                        ("Пакеты", "\(store.bleStatus.reassembledPackets)"),
                    ])

                    if let error = store.bleStatus.lastError {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .textSelection(.enabled)
                    }

                    Text("Bluetooth-транспорт использует Android GATT UUID и фрагментацию кадров. Доставку с телефоном ещё нужно проверить вручную.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(24)
        .navigationTitle("Связь")
    }

    private func peerName(for relationshipId: String) -> String {
        store.state.relationships.first { $0.relationshipId == relationshipId }?.peerDisplayName ?? relationshipId
    }

    private func routeIcon(_ kind: PeerRouteKind) -> String {
        switch kind {
        case .none: "slash.circle"
        case .directBle: "dot.radiowaves.left.and.right"
        case .directLan: "wifi"
        case .routedMesh: "point.3.connected.trianglepath.dotted"
        }
    }

    private func bandwidthTitle(_ value: BandwidthClass) -> String {
        switch value {
        case .none: "нет"
        case .low: "низкий"
        case .medium: "средний"
        case .high: "высокий"
        }
    }

    private func bleStateTitle(_ value: String) -> String {
        switch value {
        case "stopped": "остановлен"
        case "starting": "запускается"
        case "waiting-authorization": "ожидает разрешение macOS"
        case "scanning": "сканирует"
        case "advertising": "объявляет сервис"
        case "powered-on", "poweredOn": "включён"
        case "powered-off", "poweredOff": "выключен"
        case "unsupported": "не поддерживается"
        case "unauthorized": "нет доступа"
        case "resetting": "перезапуск"
        case let state where state.hasPrefix("authorization-"): "нет доступа"
        default: value
        }
    }

    private func bleAuthorizationTitle(_ value: String) -> String {
        switch value {
        case "allowed": "разрешён"
        case "not-determined": "ожидает разрешение"
        case "denied": "запрещён"
        case "restricted": "ограничен"
        default: value
        }
    }
}

private struct LanEventsTable: View {
    let events: [MacLanTransferEvent]

    var body: some View {
        if events.isEmpty {
            Text("События LAN пока не записаны.")
                .foregroundStyle(.secondary)
        } else {
            Table(events) {
                TableColumn("Направление") { event in
                    Text(event.direction == .inbound ? "входящее" : "исходящее")
                }
                TableColumn("Статус") { event in
                    Text(statusTitle(event.status))
                }
                TableColumn("Пакет") { event in
                    Text(event.packetId ?? "-")
                        .lineLimit(1)
                        .textSelection(.enabled)
                }
                TableColumn("Устройство") { event in
                    Text("\(event.senderFingerprint ?? "-") -> \(event.recipientFingerprint ?? "-")")
                        .lineLimit(1)
                }
                TableColumn("Ошибка") { event in
                    Text(event.error ?? "-")
                        .lineLimit(1)
                }
            }
            .frame(minHeight: 160)
        }
    }

    private func statusTitle(_ status: MacLanEventStatus) -> String {
        switch status {
        case .accepted: "принято"
        case .acked: "подтверждено"
        case .failed: "ошибка"
        }
    }
}
