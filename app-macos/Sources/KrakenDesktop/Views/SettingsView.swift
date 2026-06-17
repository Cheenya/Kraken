import KrakenDesktopCore
import SwiftUI

struct SettingsView: View {
    @Environment(\.krakenPalette) private var palette
    @EnvironmentObject private var store: KrakenDesktopStore
    let onShowIdentityQr: () -> Void
    let onScanQr: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HeaderBlock(
                    title: store.selectedSettingsPane.title,
                    subtitle: store.selectedSettingsPane.subtitle
                )

                paneContent
            }
            .padding(24)
        }
        .background(palette.detailBackground)
    }

    private var customAccentBinding: Binding<Color> {
        Binding(
            get: {
                Color.krakenHex(store.customAccentHex) ?? store.selectedTheme.palette.accent
            },
            set: { color in
                store.customAccentHex = color.krakenHexString() ?? store.customAccentHex
                store.customAccentEnabled = true
            }
        )
    }

    @ViewBuilder
    private var paneContent: some View {
        switch store.selectedSettingsPane {
        case .identity:
            profileSection
        case .transport:
            transportSection
        case .lanBridge:
            technicalSection
        case .appearance:
            appearanceSection
        case .typography:
            placeholderSection(
                title: "Шрифты",
                text: "Здесь будут параметры размера текста, плотности интерфейса и читаемости чатов."
            )
        case .technical:
            placeholderSection(
                title: "Технические",
                text: "Здесь будут журналы транспорта, диагностика BLE/LAN, экспорт evidence и режимы отладки."
            )
        }
    }

    private var profileSection: some View {
        SectionBlock(title: "Личность") {
            if let identity = store.state.localIdentity {
                KeyValueGrid(
                    items: [
                        ("Имя", identity.displayName),
                        ("Отпечаток", KrakenFormatters.compactFingerprint(identity.fingerprint)),
                        ("Создана", identity.createdAt.formatted(date: .abbreviated, time: .shortened)),
                    ]
                )
                HStack(spacing: 10) {
                    Button(action: onShowIdentityQr) {
                        Label("Мой QR", systemImage: "qrcode")
                    }
                    .buttonStyle(.borderedProminent)

                    Button(action: onScanQr) {
                        Label("Сканировать QR", systemImage: "qrcode.viewfinder")
                    }
                    .buttonStyle(.bordered)
                }

                Text("Имя личности задаётся при создании профиля и не меняется из настроек.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Text("Личность ещё не создана.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var transportSection: some View {
        SectionBlock(title: "Каналы связи") {
            HStack(spacing: 10) {
                TransportSettingsButton(
                    title: "WiFi/LAN",
                    systemImage: "wifi",
                    enabled: store.lanModeEnabled,
                    action: store.toggleLanMode
                )
                TransportSettingsButton(
                    title: "Bluetooth",
                    systemImage: "dot.radiowaves.left.and.right",
                    enabled: store.bluetoothModeEnabled,
                    action: store.toggleBluetoothMode
                )
            }
            Text("Эти переключатели отключают канал только внутри Kraken и не меняют системные настройки macOS.")
                .font(.caption)
                .foregroundStyle(.secondary)

            if store.bleStatus.authorizationState != "allowed" {
                Button {
                    store.openBluetoothPrivacySettings()
                } label: {
                    Label("Разрешить Bluetooth в macOS", systemImage: "lock.open")
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private var technicalSection: some View {
        SectionBlock(title: "LAN/ADB мост") {
            Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 10) {
                GridRow {
                    Text("Адрес")
                        .foregroundStyle(.secondary)
                    TextField("127.0.0.1", text: $store.lanTargetHost)
                        .textFieldStyle(.roundedBorder)
                }
                GridRow {
                    Text("Порт")
                        .foregroundStyle(.secondary)
                    TextField("54035", text: $store.lanTargetPort)
                        .textFieldStyle(.roundedBorder)
                }
                GridRow {
                    Text("Отпечаток устройства")
                        .foregroundStyle(.secondary)
                    TextField("Отпечаток", text: $store.lanTargetFingerprint)
                        .textFieldStyle(.roundedBorder)
                }
                GridRow {
                    Text("Имя устройства")
                        .foregroundStyle(.secondary)
                    TextField("Xiaomi", text: $store.lanTargetDisplayName)
                        .textFieldStyle(.roundedBorder)
                }
                GridRow {
                    Text("Порт приёма")
                        .foregroundStyle(.secondary)
                    TextField("43191", text: $store.lanListenPort)
                        .textFieldStyle(.roundedBorder)
                }
            }

            HStack(spacing: 10) {
                Button {
                    store.selectLanBridgeEndpointPeer()
                } label: {
                    Label("Выбрать peer", systemImage: "person.crop.circle.badge.checkmark")
                }

                Button {
                    store.startLanListener()
                } label: {
                    Label("Запустить приём", systemImage: "antenna.radiowaves.left.and.right")
                }
                .disabled(store.lanListenerRunning || !store.lanModeEnabled)

                Button {
                    store.stopLanListener()
                } label: {
                    Label("Остановить", systemImage: "stop.circle")
                }
                .disabled(!store.lanListenerRunning)
            }
            .buttonStyle(.bordered)

            Text(store.lanListenerRunning ? "Приём LAN активен." : "Приём LAN остановлен.")
                .font(.caption)
                .foregroundStyle(.secondary)

            Label(
                store.lanBridgeBindingTitle,
                systemImage: store.lanBridgeSelectedPeerMatchesEndpoint ? "checkmark.circle" : "exclamationmark.triangle"
            )
            .font(.caption)
            .foregroundStyle(store.lanBridgeSelectedPeerMatchesEndpoint ? .green : .orange)

            Text("ADB forward/reverse задают только текущий endpoint. Peer для UI выбирается по отпечатку устройства.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var appearanceSection: some View {
        SectionBlock(title: "Оформление") {
            VStack(alignment: .leading, spacing: 12) {
                Text("Темы")
                    .font(.subheadline.weight(.semibold))

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                    ForEach(KrakenThemePreset.allCases) { theme in
                        ThemePresetButton(
                            theme: theme,
                            selected: store.selectedTheme == theme
                        ) {
                            store.selectedTheme = theme
                        }
                    }
                }

                Divider()

                Text("Палитра")
                    .font(.subheadline.weight(.semibold))

                Toggle("Свой цвет акцента", isOn: $store.customAccentEnabled)

                HStack(spacing: 12) {
                    ColorPicker("Цвет акцента", selection: customAccentBinding, supportsOpacity: false)
                        .disabled(!store.customAccentEnabled)

                    Text(store.customAccentHex.uppercased())
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)

                    Spacer()

                    Button("Сбросить") {
                        store.customAccentEnabled = false
                        store.customAccentHex = "#12C8DC"
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }

    private func placeholderSection(title: String, text: String) -> some View {
        SectionBlock(title: title) {
            Text(text)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct TransportSettingsButton: View {
    let title: String
    let systemImage: String
    let enabled: Bool
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                ZStack {
                    Image(systemName: systemImage)
                        .font(.system(size: 16, weight: .semibold))
                    if !enabled {
                        Rectangle()
                            .fill(Color.red.opacity(0.9))
                            .frame(width: 22, height: 2)
                            .rotationEffect(.degrees(-35))
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                    Text(enabled ? "включён" : "выключен")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(hovering ? Color.secondary.opacity(0.16) : Color.secondary.opacity(0.10), in: RoundedRectangle(cornerRadius: 8))
            .contentShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .help(enabled ? "Выключить \(title) для Kraken" : "Включить \(title) для Kraken")
    }
}

private struct ThemePresetButton: View {
    let theme: KrakenThemePreset
    let selected: Bool
    let action: () -> Void
    @State private var hovering = false

    private var palette: KrakenThemePalette {
        theme.palette
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(palette.detailBackground)
                    HStack(spacing: 0) {
                        palette.sidebarBackground
                        palette.panelBackground
                        palette.messageBackground
                        palette.inputBackground
                        palette.accent
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 7))
                    .padding(5)
                }
                .frame(width: 54, height: 38)
                .overlay {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(selected ? palette.accent : Color.secondary.opacity(0.20), lineWidth: selected ? 2 : 1)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(theme.title)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text(theme.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()

                if selected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(palette.accent)
                }
            }
            .padding(10)
            .background(hovering || selected ? palette.elevatedPanel : palette.panelBackground, in: RoundedRectangle(cornerRadius: 8))
            .contentShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
    }
}
