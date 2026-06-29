import SwiftUI
import UIKit

struct KrakenIOSRootView: View {
    @ObservedObject var store: KrakenIOSStore
    @ObservedObject var transport: IOSNearbyTransportAdapter

    @SceneStorage("kraken.selectedTab") private var selectedTabRaw = KrakenTab.home.rawValue
    @SceneStorage("kraken.showWelcome") private var showWelcome = true
    @State private var displayName = "Kraken"
    @State private var messageDraft = ""
    @State private var qrImportText = ""
    @State private var manualPeerName = "Android Xiaomi"
    @State private var manualPeerFingerprint = "ANDROID-FP"
    @State private var qrPayload = ""
    @State private var importError: String?
    @State private var evidenceJson = ""
    @State private var evidenceURL: URL?
    @State private var scannerVisible = false
    @State private var didApplyLaunchConfiguration = false

    private var selectedTab: Binding<KrakenTab> {
        Binding(
            get: { KrakenTab(rawValue: selectedTabRaw) ?? .home },
            set: { selectedTabRaw = $0.rawValue }
        )
    }

    var body: some View {
        ZStack {
            KrakenPalette.background.ignoresSafeArea()
            KrakenTabShell(
                selectedTab: selectedTab,
                store: store,
                transport: transport,
                displayName: $displayName,
                messageDraft: $messageDraft,
                qrPayload: $qrPayload,
                qrImportText: $qrImportText,
                manualPeerName: $manualPeerName,
                manualPeerFingerprint: $manualPeerFingerprint,
                importError: $importError,
                evidenceJson: $evidenceJson,
                evidenceURL: $evidenceURL,
                scannerVisible: $scannerVisible,
                showWelcome: $showWelcome
            )
            if showWelcome && !LaunchConfiguration.current.skipWelcome {
                KrakenWelcomeScreen(
                    hasLocalIdentity: store.state.localIdentity != nil,
                    primaryAction: {
                        showWelcome = false
                        selectedTab.wrappedValue = store.state.localIdentity == nil ? .settings : .home
                    },
                    secondaryAction: {
                        showWelcome = false
                        selectedTab.wrappedValue = store.state.localIdentity == nil ? .home : .contacts
                    }
                )
                .transition(.opacity)
                .zIndex(2)
            }
        }
        .animation(.easeInOut(duration: 0.22), value: showWelcome)
        .onAppear {
            transport.onReceiveData = { data, peerDisplayName in
                store.receiveTransportEnvelope(data, fromPeer: peerDisplayName)
            }
            applyLaunchConfigurationOnce()
        }
        .sheet(isPresented: $scannerVisible) {
            QRScannerSheet { scannedPayload in
                qrImportText = scannedPayload
                scannerVisible = false
                do {
                    if let responseQrPayload = try store.importHandshakePayload(scannedPayload) {
                        qrPayload = responseQrPayload
                    }
                    importError = nil
                } catch {
                    importError = String(describing: error)
                }
            }
        }
    }

    private func applyLaunchConfigurationOnce() {
        guard !didApplyLaunchConfiguration else { return }
        didApplyLaunchConfiguration = true
        let configuration = LaunchConfiguration.current
        if configuration.skipWelcome {
            showWelcome = false
        } else if configuration.demoMode {
            showWelcome = true
        }
        if let tab = configuration.tab {
            selectedTab.wrappedValue = tab
        }
        if let qrPayloadKind = configuration.qrPayloadKind,
           let payload = makeSmokeQrPayload(kind: qrPayloadKind) {
            qrPayload = payload
        }
    }

    private func makeSmokeQrPayload(kind: KrakenSmokeQrPayloadKind) -> String? {
        let rawPayload: String
        switch kind {
        case .invite:
            return try? store.exportIdentityQrPayload()
        case .response:
            rawPayload = """
            {
              "type": "kraken.handshake.response.v1",
              "version": 1,
              "response_id": "response-ios-smoke",
              "invite_id": "invite-android-smoke",
              "responder_fingerprint": "\(store.state.localIdentity?.fingerprint ?? "IOS-FP")",
              "responder_display_name": "\(store.state.localIdentity?.displayName ?? "Kraken")",
              "responder_public_key_encoded": "\(store.state.localIdentity?.publicKeyEncoded ?? "kraken-public-key")",
              "inviter_fingerprint": "ANDROID-FP-7A91",
              "created_at_epoch_millis": 1800000000000,
              "crypto_profile_id": "standard-reviewed-primitives-v1",
              "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
              "profile_policy_version": 1,
              "proof_placeholder": "prototype-offline-qr-handshake-not-production-crypto"
            }
            """
        case .confirmation:
            rawPayload = """
            {
              "type": "kraken.handshake.confirmation.v1",
              "version": 1,
              "confirmation_id": "confirmation-android-smoke",
              "response_id": "response-ios-smoke",
              "invite_id": "invite-android-smoke",
              "inviter_fingerprint": "ANDROID-FP-7A91",
              "responder_fingerprint": "\(store.state.localIdentity?.fingerprint ?? "IOS-FP")",
              "created_at_epoch_millis": 1800000001000,
              "crypto_profile_id": "standard-reviewed-primitives-v1",
              "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
              "profile_policy_version": 1
            }
            """
        }
        return try? KrakenHandshakeQrCodec.encodedQrPayload(rawPayload)
    }
}

private enum KrakenTab: String, CaseIterable {
    case home
    case contacts
    case realms
    case settings

    var title: String {
        switch self {
        case .home: "Чаты"
        case .contacts: "Контакты"
        case .realms: "Реалмы"
        case .settings: "Настройки"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "bubble.left.and.bubble.right"
        case .contacts: "person.crop.circle"
        case .realms: "person.3"
        case .settings: "gearshape"
        }
    }
}

private struct LaunchConfiguration {
    var skipWelcome: Bool
    var demoMode: Bool
    var tab: KrakenTab?
    var scrollTarget: KrakenScrollTarget?
    var qrPayloadKind: KrakenSmokeQrPayloadKind?

    static var current: LaunchConfiguration {
        let arguments = ProcessInfo.processInfo.arguments
        let environment = ProcessInfo.processInfo.environment
        let tab = arguments
            .first { $0.hasPrefix("--kraken-tab=") }
            .flatMap { KrakenTab(rawValue: String($0.dropFirst("--kraken-tab=".count))) }
            ?? environment["KRAKEN_TAB"].flatMap(KrakenTab.init(rawValue:))
        let scrollTarget = arguments
            .first { $0.hasPrefix("--kraken-scroll=") }
            .flatMap { KrakenScrollTarget(rawValue: String($0.dropFirst("--kraken-scroll=".count))) }
            ?? environment["KRAKEN_SCROLL"].flatMap(KrakenScrollTarget.init(rawValue:))
        let qrPayloadKind = arguments
            .first { $0.hasPrefix("--kraken-qr=") }
            .flatMap { KrakenSmokeQrPayloadKind(rawValue: String($0.dropFirst("--kraken-qr=".count))) }
            ?? environment["KRAKEN_QR"].flatMap(KrakenSmokeQrPayloadKind.init(rawValue:))
        return LaunchConfiguration(
            skipWelcome: arguments.contains("--kraken-skip-welcome")
                || environment["KRAKEN_SKIP_WELCOME"] == "1",
            demoMode: arguments.contains("--kraken-demo")
                || environment["KRAKEN_DEMO_MODE"] == "1",
            tab: tab,
            scrollTarget: scrollTarget,
            qrPayloadKind: qrPayloadKind
        )
    }
}

private enum KrakenScrollTarget: String {
    case bottom
}

private enum KrakenSmokeQrPayloadKind: String {
    case invite
    case response
    case confirmation
}

private struct KrakenTabShell: View {
    @Binding var selectedTab: KrakenTab
    @ObservedObject var store: KrakenIOSStore
    @ObservedObject var transport: IOSNearbyTransportAdapter
    @Binding var displayName: String
    @Binding var messageDraft: String
    @Binding var qrPayload: String
    @Binding var qrImportText: String
    @Binding var manualPeerName: String
    @Binding var manualPeerFingerprint: String
    @Binding var importError: String?
    @Binding var evidenceJson: String
    @Binding var evidenceURL: URL?
    @Binding var scannerVisible: Bool
    @Binding var showWelcome: Bool

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                ChatListScreen(
                    store: store,
                    transport: transport,
                    selectedTab: $selectedTab,
                    messageDraft: $messageDraft,
                    qrPayload: $qrPayload,
                    scannerVisible: $scannerVisible
                )
            }
            .tabItem { Label(KrakenTab.home.title, systemImage: KrakenTab.home.systemImage) }
            .tag(KrakenTab.home)

            NavigationStack {
                ContactsScreen(
                    store: store,
                    qrPayload: $qrPayload,
                    qrImportText: $qrImportText,
                    manualPeerName: $manualPeerName,
                    manualPeerFingerprint: $manualPeerFingerprint,
                    importError: $importError,
                    scannerVisible: $scannerVisible
                )
            }
            .tabItem { Label(KrakenTab.contacts.title, systemImage: KrakenTab.contacts.systemImage) }
            .tag(KrakenTab.contacts)

            NavigationStack {
                RealmsScreen(store: store)
            }
            .tabItem { Label(KrakenTab.realms.title, systemImage: KrakenTab.realms.systemImage) }
            .tag(KrakenTab.realms)

            NavigationStack {
                SettingsScreen(
                    store: store,
                    transport: transport,
                    displayName: $displayName,
                    evidenceJson: $evidenceJson,
                    evidenceURL: $evidenceURL,
                    showWelcome: $showWelcome
                )
            }
            .tabItem { Label(KrakenTab.settings.title, systemImage: KrakenTab.settings.systemImage) }
            .tag(KrakenTab.settings)
        }
        .tint(KrakenPalette.primary)
        .preferredColorScheme(.dark)
        .krakenTabBarMinimizeOnScroll()
    }
}

private struct KrakenWelcomeScreen: View {
    var hasLocalIdentity: Bool
    var primaryAction: () -> Void
    var secondaryAction: () -> Void

    var body: some View {
        GeometryReader { proxy in
            let contentWidth = welcomeContentWidth(for: proxy.size.width)
            ZStack {
                KrakenPalette.background.ignoresSafeArea()
                Image("StartBackground")
                    .resizable()
                    .scaledToFill()
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
                LinearGradient(
                    colors: [
                        KrakenPalette.background.opacity(0.1),
                        KrakenPalette.background.opacity(0.28),
                        KrakenPalette.background.opacity(0.78),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        Spacer(minLength: max(42, proxy.size.height * 0.08))
                        Image("LaunchMark")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 108, height: 108)
                            .clipShape(Circle())
                            .shadow(color: KrakenPalette.primary.opacity(0.32), radius: 18)
                            .accessibilityLabel("Kraken")
                        Text("K R A K E N")
                            .font(.system(size: 29, weight: .semibold, design: .default))
                            .foregroundStyle(KrakenPalette.text)
                            .padding(.top, 18)
                        Text("ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО")
                            .font(.system(size: 11, weight: .medium, design: .default))
                            .foregroundStyle(KrakenPalette.primary)
                            .padding(.top, 8)

                        Spacer(minLength: max(28, proxy.size.height * 0.035))

                        Button(action: primaryAction) {
                            Label(hasLocalIdentity ? "ОТКРЫТЬ KRAKEN" : "СОЗДАТЬ ЛИЧНОСТЬ", systemImage: hasLocalIdentity ? "bubble.left.and.bubble.right.fill" : "person.badge.plus")
                                .font(.system(size: 14, weight: .semibold))
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(KrakenPrimaryButtonStyle())
                        .frame(width: contentWidth)

                        Button(action: secondaryAction) {
                            Label(hasLocalIdentity ? "МОЙ QR-КОД" : "ОБЗОР", systemImage: hasLocalIdentity ? "qrcode" : "square.grid.2x2")
                                .font(.system(size: 14, weight: .semibold))
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(KrakenOutlinedButtonStyle())
                        .frame(width: contentWidth)
                        .padding(.top, 10)

                        Spacer(minLength: max(30, proxy.size.height * 0.05))

                        HStack(alignment: .top) {
                            StartPrinciple(systemImage: "shield", title: "ПОЛНАЯ\nПРИВАТНОСТЬ")
                            Spacer()
                            StartPrinciple(systemImage: "wifi.slash", title: "БЕЗ СЕРВЕРОВ\nИ АККАУНТОВ")
                            Spacer()
                            StartPrinciple(systemImage: "lock", title: "ВАШИ ДАННЫЕ\nТОЛЬКО У ВАС")
                        }
                        .frame(width: contentWidth)
                        .padding(.bottom, max(24, proxy.safeAreaInsets.bottom + 20))
                    }
                    .frame(minHeight: proxy.size.height)
                }
            }
        }
    }

    private func welcomeContentWidth(for screenWidth: CGFloat) -> CGFloat {
        min(max(screenWidth - 68, 286), 520)
    }
}

private struct ChatListScreen: View {
    @ObservedObject var store: KrakenIOSStore
    @ObservedObject var transport: IOSNearbyTransportAdapter
    @Binding var selectedTab: KrakenTab
    @Binding var messageDraft: String
    @Binding var qrPayload: String
    @Binding var scannerVisible: Bool

    private var activeRelationships: [Relationship] {
        store.state.relationships.filter { $0.state == .active }
    }

    var body: some View {
        KrakenScreen(title: "Чаты") {
            if activeRelationships.isEmpty {
                KrakenEmptyState(
                    systemImage: "person.2.slash",
                    title: "Пока нет активных чатов",
                    detail: "Добавьте контакт через QR во вкладке «Контакты»."
                )
                Button {
                    selectedTab = .contacts
                } label: {
                    Label("Открыть контакты", systemImage: "person.crop.circle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(KrakenPrimaryButtonStyle())
            } else {
                KrakenPanel(title: "Диалоги", systemImage: "bubble.left.and.bubble.right") {
                    ForEach(activeRelationships) { relationship in
                        Button {
                            store.selectRelationship(relationship.relationshipId)
                        } label: {
                            ChatListRow(
                                relationship: relationship,
                                latestMessage: latestMessage(for: relationship),
                                selected: relationship.relationshipId == store.selectedRelationshipId
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if store.selectedRelationship != nil {
                KrakenPanel(title: store.selectedRelationship?.peerDisplayName ?? "Сообщения", systemImage: "bubble.left.and.bubble.right") {
                    if store.selectedMessages.isEmpty {
                        KrakenEmptyState(
                            systemImage: "bubble.left",
                            title: "Переписка пуста",
                            detail: "Напишите первое сообщение."
                        )
                    } else {
                        ForEach(store.selectedMessages) { message in
                            MessageBubble(message: message)
                        }
                    }
                    HStack(alignment: .bottom, spacing: 10) {
                        TextField("Сообщение", text: $messageDraft, axis: .vertical)
                            .textFieldStyle(.plain)
                            .lineLimit(1...4)
                            .padding(12)
                            .krakenGlassInset(cornerRadius: 10)
                        Button {
                            sendCurrentDraft()
                        } label: {
                            Image(systemName: "paperplane.fill")
                                .frame(width: 20, height: 20)
                        }
                        .buttonStyle(KrakenIconButtonStyle())
                        .disabled(messageDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .accessibilityLabel("Отправить сообщение")
                    }
                }
            }
        }
    }

    private func latestMessage(for relationship: Relationship) -> LocalMessage? {
        store.state.messages
            .filter { $0.relationshipId == relationship.relationshipId }
            .sorted { $0.createdAt > $1.createdAt }
            .first
    }

    private func sendCurrentDraft() {
        let draft = messageDraft
        guard let messageId = store.sendMessage(draft) else { return }
        messageDraft = ""
        do {
            guard let relationship = store.selectedRelationship else { return }
            try transport.send(
                makeTransportEnvelope(messageId: messageId, body: draft, relationship: relationship),
                toPeerNamed: relationship.peerDisplayName
            )
        } catch {
            store.markTransportFailure(messageId: messageId, error: "nearby-peer-not-connected")
        }
    }

    private func makeTransportEnvelope(messageId: String, body: String, relationship: Relationship) -> Data {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let localIdentity = store.state.localIdentity
        let payloadJson = (try? String(
            data: JSONSerialization.data(
                withJSONObject: [
                    "message_id": messageId,
                    "body": body,
                ],
                options: [.sortedKeys]
            ),
            encoding: .utf8
        )) ?? #"{"body":""}"#
        let packet = KrakenPacket(
            packetId: "packet-\(UUID().uuidString)",
            senderFingerprint: localIdentity?.fingerprint ?? "IOS-FP",
            recipientFingerprint: relationship.peerFingerprint,
            relationshipId: relationship.relationshipId,
            conversationId: relationship.relationshipId,
            messageId: messageId,
            createdAtEpochMillis: nowMillis,
            expiresAtEpochMillis: nowMillis + 300_000,
            payloadJson: payloadJson,
            cryptoProfileId: relationship.cryptoProfileId,
            admissionDecisionHash: relationship.admissionDecisionHash,
            profilePolicyVersion: relationship.profilePolicyVersion
        )
        let packetObject = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(packet))) as? [String: Any] ?? [:]
        let envelope: [String: Any] = [
            "type": "kraken.ios.packet.v1",
            "packet": packetObject,
        ]
        return (try? JSONSerialization.data(withJSONObject: envelope, options: [.sortedKeys])) ?? Data(body.utf8)
    }
}

private struct ChatListRow: View {
    var relationship: Relationship
    var latestMessage: LocalMessage?
    var selected: Bool

    var body: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .bottomTrailing) {
                Text(String(relationship.peerDisplayName.prefix(2)).uppercased())
                    .font(.subheadline.weight(.semibold).monospaced())
                    .foregroundStyle(KrakenPalette.onPrimary)
                    .frame(width: 42, height: 42)
                    .krakenGlassCircle(fill: KrakenPalette.primary)
                Circle()
                    .fill(KrakenPalette.primary)
                    .frame(width: 11, height: 11)
                    .overlay(Circle().stroke(KrakenPalette.background, lineWidth: 2))
            }
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    Text(relationship.peerDisplayName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(KrakenPalette.text)
                        .lineLimit(1)
                    Spacer()
                    Text(latestMessage?.createdAt.formatted(date: .omitted, time: .shortened) ?? relationship.state.title)
                        .font(.caption2)
                        .foregroundStyle(KrakenPalette.secondaryText)
                }
                Text(latestMessage?.body ?? "Переписка готова")
                    .font(.caption)
                    .foregroundStyle(KrakenPalette.secondaryText)
                    .lineLimit(1)
            }
            if selected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(KrakenPalette.primary)
            }
        }
        .padding(.vertical, 8)
    }
}

private struct ContactsScreen: View {
    @ObservedObject var store: KrakenIOSStore
    @Binding var qrPayload: String
    @Binding var qrImportText: String
    @Binding var manualPeerName: String
    @Binding var manualPeerFingerprint: String
    @Binding var importError: String?
    @Binding var scannerVisible: Bool

    var body: some View {
        KrakenScreen(title: "Контакты") {
            KrakenPanel(title: "Контакты", systemImage: "person.2") {
                if store.state.relationships.isEmpty {
                    KrakenEmptyState(
                        systemImage: "qrcode.viewfinder",
                        title: "Контактов нет",
                        detail: "Добавьте устройство через QR или временно создайте тестовый контакт."
                    )
                } else {
                    ForEach(store.state.relationships) { relationship in
                        VStack(alignment: .leading, spacing: 5) {
                            Text(relationship.peerDisplayName)
                                .font(.subheadline.weight(.semibold))
                            Text(relationship.peerFingerprint)
                                .font(.caption.monospaced())
                                .foregroundStyle(KrakenPalette.secondaryText)
                            Text(relationship.state.title)
                                .font(.caption)
                                .foregroundStyle(KrakenPalette.primary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 6)
                    }
                }
            }

            KrakenPanel(title: "QR-обмен", systemImage: "qrcode") {
                if store.state.localIdentity == nil {
                    KrakenEmptyState(
                        systemImage: "person.crop.circle.badge.exclamationmark",
                        title: "Личность не создана",
                        detail: "Создайте личность в настройках, затем экспортируйте QR."
                    )
                } else {
                    Button {
                        qrPayload = (try? store.exportIdentityQrPayload()) ?? ""
                    } label: {
                        Label("Показать мой QR", systemImage: "qrcode")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenPrimaryButtonStyle())
                    if !qrPayload.isEmpty {
                        let descriptor = qrPayloadDescriptor
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: descriptor.systemImage)
                                .foregroundStyle(KrakenPalette.primary)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(descriptor.title)
                                    .font(.footnote.weight(.semibold))
                                    .foregroundStyle(KrakenPalette.text)
                                Text(descriptor.detail)
                                    .font(.caption)
                                    .foregroundStyle(KrakenPalette.secondaryText)
                            }
                        }
                        .padding(.vertical, 2)
                        QRCodeView(payload: qrPayload)
                            .frame(maxWidth: .infinity)
                        ShareLink(item: qrPayload) {
                            Label("Поделиться QR", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(KrakenOutlinedButtonStyle())
                        DisclosureGroup("Данные QR для проверки") {
                            Text(qrPayload)
                                .font(.caption.monospaced())
                                .textSelection(.enabled)
                                .padding(.top, 8)
                        }
                    }
                }
            }

            KrakenPanel(title: "Импорт", systemImage: "square.and.arrow.down") {
                Button {
                    scannerVisible = true
                } label: {
                    Label("Скан QR", systemImage: "camera.viewfinder")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(KrakenPrimaryButtonStyle())

                TextEditor(text: $qrImportText)
                    .frame(minHeight: 94)
                    .font(.footnote.monospaced())
                    .scrollContentBackground(.hidden)
                    .padding(8)
                    .krakenGlassInset(cornerRadius: 8)

                Button {
                    do {
                        if let responseQrPayload = try store.importHandshakePayload(qrImportText) {
                            qrPayload = responseQrPayload
                        }
                        importError = nil
                        qrImportText = ""
                    } catch {
                        importError = String(describing: error)
                    }
                } label: {
                    Label("Импортировать", systemImage: "square.and.arrow.down")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(KrakenOutlinedButtonStyle())
                .disabled(qrImportText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                if let importError {
                    Text(importError)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }

            KrakenPanel(title: "Ручное добавление", systemImage: "person.crop.circle.badge.plus") {
                Text("Если локальное подтверждение не придёт, добавьте устройство вручную.")
                    .font(.footnote)
                    .foregroundStyle(KrakenPalette.secondaryText)
                TextField("Имя", text: $manualPeerName)
                    .textFieldStyle(KrakenTextFieldStyle())
                TextField("Отпечаток", text: $manualPeerFingerprint)
                    .textInputAutocapitalization(.characters)
                    .font(.subheadline.monospaced())
                    .textFieldStyle(KrakenTextFieldStyle())
                Button {
                    store.importPeer(displayName: manualPeerName, fingerprint: manualPeerFingerprint)
                } label: {
                    Label("Добавить контакт", systemImage: "plus.circle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(KrakenOutlinedButtonStyle())
            }
        }
    }

    private var qrPayloadDescriptor: QrPayloadDescriptor {
        switch KrakenHandshakeQrCodec.detectKind(qrPayload) {
        case .invite:
            QrPayloadDescriptor(
                systemImage: "person.badge.plus",
                title: "QR-приглашение для Android",
                detail: "Покажите этот QR на Android, чтобы Android сформировал ответ рукопожатия."
            )
        case .response:
            QrPayloadDescriptor(
                systemImage: "arrowshape.turn.up.backward.badge.clock",
                title: "QR-ответ для приглашения Android",
                detail: "Покажите этот ответ на Android, затем импортируйте подтверждение для активации связи."
            )
        case .confirmation:
            QrPayloadDescriptor(
                systemImage: "checkmark.seal",
                title: "QR подтверждения",
                detail: "Эти данные завершают ожидающее QR-рукопожатие."
            )
        case .unknown:
            QrPayloadDescriptor(
                systemImage: "questionmark.circle",
                title: "Неизвестный QR",
                detail: "Данные распознаны как JSON, но тип не входит в текущий QR-обмен."
            )
        case .invalid:
            QrPayloadDescriptor(
                systemImage: "exclamationmark.triangle",
                title: "Некорректный QR",
                detail: "Данные не удалось распознать как JSON QR-рукопожатия."
            )
        }
    }
}

private struct QrPayloadDescriptor {
    var systemImage: String
    var title: String
    var detail: String
}

private struct RealmsScreen: View {
    @ObservedObject var store: KrakenIOSStore
    @State private var newRealmName = ""
    @State private var showCreateRealm = false
    @State private var selectedRealmId: String?

    private var activeRealms: [LocalRealm] {
        store.state.realms.filter { $0.localState == .active }
    }

    private var pausedRealms: [LocalRealm] {
        store.state.realms.filter { $0.localState == .paused }
    }

    private var archivedRealms: [LocalRealm] {
        store.state.realms.filter { $0.localState == .archived || $0.localState == .left }
    }

    private var selectedRealm: LocalRealm? {
        if let selectedRealmId,
           let realm = store.state.realms.first(where: { $0.realmId == selectedRealmId }) {
            return realm
        }
        return store.state.realms.first
    }

    var body: some View {
        KrakenScreen(title: "Реалмы") {
            if store.state.localIdentity == nil {
                KrakenEmptyState(
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    title: "Нужен профиль на этом устройстве",
                    detail: "Создайте личность в настройках перед созданием реалма."
                )
            } else if store.state.realms.isEmpty {
                KrakenEmptyState(
                    systemImage: "cube.transparent",
                    title: "Локальных реалмов нет",
                    detail: "Вход только по приглашению. Публичного поиска нет."
                )
            }

            RealmSection(
                title: "Активные реалмы",
                emptyText: "Активных локальных реалмов нет.",
                realms: activeRealms,
                selectedRealmId: selectedRealm?.realmId,
                onSelect: { selectedRealmId = $0.realmId }
            )
            RealmSection(
                title: "Ожидают проверки",
                emptyText: "Нет реалмов с ожидающими локальными действиями.",
                realms: pausedRealms,
                selectedRealmId: selectedRealm?.realmId,
                onSelect: { selectedRealmId = $0.realmId }
            )
            RealmSection(
                title: "Покинутые / архив",
                emptyText: "Нет покинутых или архивных записей.",
                realms: archivedRealms,
                selectedRealmId: selectedRealm?.realmId,
                onSelect: { selectedRealmId = $0.realmId }
            )

            KrakenPanel(title: "Новый реалм", systemImage: "plus.circle") {
                if showCreateRealm {
                    TextField("Название реалма", text: $newRealmName)
                        .textFieldStyle(KrakenTextFieldStyle())
                    Text("Локальный реалм доступен только по QR-приглашениям. Публичного каталога нет.")
                        .font(.footnote)
                        .foregroundStyle(KrakenPalette.secondaryText)
                    HStack {
                        Button {
                            if let realmId = store.createRealm(name: newRealmName) {
                                selectedRealmId = realmId
                            }
                            newRealmName = ""
                            showCreateRealm = false
                        } label: {
                            Text("Создать")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(KrakenPrimaryButtonStyle())
                        .disabled(newRealmName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                        Button {
                            newRealmName = ""
                            showCreateRealm = false
                        } label: {
                            Text("Отмена")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(KrakenOutlinedButtonStyle())
                    }
                } else {
                    Button {
                        showCreateRealm = true
                    } label: {
                        Label("Создать реалм", systemImage: "cube.transparent")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenOutlinedButtonStyle())
                    .disabled(store.state.localIdentity == nil)
                }
            }

            if let selectedRealm {
                RealmDetailsPanel(realm: selectedRealm, store: store)
            }
        }
    }
}

private struct RealmSection: View {
    var title: String
    var emptyText: String
    var realms: [LocalRealm]
    var selectedRealmId: String?
    var onSelect: (LocalRealm) -> Void

    var body: some View {
        KrakenPanel(title: title, systemImage: "cube.transparent") {
            if realms.isEmpty {
                Text(emptyText)
                    .font(.footnote)
                    .foregroundStyle(KrakenPalette.secondaryText)
            } else {
                ForEach(realms) { realm in
                    Button {
                        onSelect(realm)
                    } label: {
                        HStack(spacing: 12) {
                            Text(String(realm.name.prefix(2)).uppercased())
                                .font(.subheadline.weight(.semibold).monospaced())
                                .foregroundStyle(KrakenPalette.onPrimary)
                                .frame(width: 38, height: 38)
                                .krakenGlassCircle(fill: KrakenPalette.primary)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(realm.name)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(KrakenPalette.text)
                                Text("\(realm.localState.title) · \(realm.memberCount)/\(realm.capacity) участников")
                                    .font(.caption)
                                    .foregroundStyle(KrakenPalette.secondaryText)
                            }
                            Spacer()
                            if realm.realmId == selectedRealmId {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(KrakenPalette.primary)
                            }
                        }
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct RealmDetailsPanel: View {
    var realm: LocalRealm
    @ObservedObject var store: KrakenIOSStore

    private var realmRelationships: [Relationship] {
        realm.memberRelationshipIds.compactMap { relationshipId in
            store.state.relationships.first { $0.relationshipId == relationshipId }
        }
    }

    private var availableRelationships: [Relationship] {
        store.state.relationships.filter {
            $0.state.isMessageCapable && realm.memberRelationshipIds.contains($0.relationshipId) == false
        }
    }

    var body: some View {
        KrakenPanel(title: "Обзор", systemImage: "info.circle") {
            Text(realm.name)
                .font(.headline)
                .foregroundStyle(KrakenPalette.text)
            Text(realm.description ?? "Локальный реалм по приглашению.")
                .font(.footnote)
                .foregroundStyle(KrakenPalette.secondaryText)
                HStack {
                    KrakenMetric(title: "Состояние", value: realm.localState.title)
                    KrakenMetric(title: "Участники", value: "\(realm.memberCount)/\(realm.capacity)")
                }
        }

        KrakenPanel(title: "Участники", systemImage: "person.3") {
            if realmRelationships.isEmpty {
                Text("Локальных участников пока нет.")
                    .font(.footnote)
                    .foregroundStyle(KrakenPalette.secondaryText)
            } else {
                ForEach(realmRelationships) { relationship in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(relationship.peerDisplayName)
                                .foregroundStyle(KrakenPalette.text)
                            Text(relationship.peerFingerprint)
                                .font(.caption.monospaced())
                                .foregroundStyle(KrakenPalette.secondaryText)
                        }
                        Spacer()
                        Button {
                            store.removeRelationshipFromRealm(
                                realmId: realm.realmId,
                                relationshipId: relationship.relationshipId
                            )
                        } label: {
                            Image(systemName: "minus.circle")
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(KrakenPalette.primary)
                        .accessibilityLabel("Удалить участника из реалма")
                    }
                    .padding(.vertical, 4)
                }
            }
            if !availableRelationships.isEmpty && realm.hasCapacity {
                Divider().background(KrakenPalette.outline)
                Text("Добавить активный контакт")
                    .font(.caption)
                    .foregroundStyle(KrakenPalette.secondaryText)
                ForEach(availableRelationships) { relationship in
                    Button {
                        store.addRelationshipToRealm(
                            realmId: realm.realmId,
                            relationshipId: relationship.relationshipId
                        )
                    } label: {
                        Label(relationship.peerDisplayName, systemImage: "plus.circle")
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(KrakenOutlinedButtonStyle())
                }
            }
        }

        KrakenPanel(title: "Приглашения", systemImage: "qrcode") {
            Text("Вступление только по QR-приглашению. Каталога и публичного входа нет.")
                .font(.footnote)
                .foregroundStyle(KrakenPalette.secondaryText)
            Text("QR-приглашения реалма требуют отдельного interop-прохода с Android.")
                .font(.caption)
                .foregroundStyle(KrakenPalette.secondaryText)
        }

        KrakenPanel(title: "Локальные действия", systemImage: "slider.horizontal.3") {
            HStack {
                if realm.localState == .active {
                    Button {
                        store.updateRealmState(realmId: realm.realmId, state: .paused)
                    } label: {
                        Label("Пауза", systemImage: "pause.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenOutlinedButtonStyle())
                } else if realm.localState == .paused {
                    Button {
                        store.updateRealmState(realmId: realm.realmId, state: .active)
                    } label: {
                        Label("Возобновить", systemImage: "play.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenPrimaryButtonStyle())
                }
                if realm.localState != .archived {
                    Button {
                        store.updateRealmState(realmId: realm.realmId, state: .archived)
                    } label: {
                        Label("Архив", systemImage: "archivebox")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenOutlinedButtonStyle())
                }
            }
            if realm.localState != .left {
                Button {
                    store.updateRealmState(realmId: realm.realmId, state: .left)
                } label: {
                        Label("Покинуть реалм", systemImage: "rectangle.portrait.and.arrow.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(KrakenOutlinedButtonStyle())
            }
        }
    }
}

private struct KrakenMetric: View {
    var title: String
    var value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(KrakenPalette.secondaryText)
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(KrakenPalette.text)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .krakenGlassInset(cornerRadius: 10)
    }
}

private struct SettingsScreen: View {
    @ObservedObject var store: KrakenIOSStore
    @ObservedObject var transport: IOSNearbyTransportAdapter
    @Binding var displayName: String
    @Binding var evidenceJson: String
    @Binding var evidenceURL: URL?
    @Binding var showWelcome: Bool

    var body: some View {
        KrakenScreen(title: "Настройки") {
            KrakenPanel(title: "Профиль", systemImage: "person.crop.circle") {
                if let identity = store.state.localIdentity {
                    KrakenKeyValue(title: "Имя", value: identity.displayName)
                    KrakenKeyValue(title: "Отпечаток", value: identity.fingerprint, monospaced: true)
                } else {
                    TextField("Имя устройства", text: $displayName)
                        .textFieldStyle(KrakenTextFieldStyle())
                    Button {
                        store.createIdentity(displayName: displayName)
                    } label: {
                        Label("Создать профиль", systemImage: "person.badge.plus")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenPrimaryButtonStyle())
                }
                Button {
                    showWelcome = true
                } label: {
                    Label("Показать стартовый экран", systemImage: "sparkles")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(KrakenOutlinedButtonStyle())
            }

            KrakenPanel(title: "Сеть", systemImage: "antenna.radiowaves.left.and.right") {
                KrakenKeyValue(title: "Канал", value: transport.descriptor.transportId, monospaced: true)
                KrakenKeyValue(title: "Состояние", value: transport.state.title)
                Text(transport.descriptor.boundaryNote)
                    .font(.footnote)
                    .foregroundStyle(KrakenPalette.secondaryText)
                HStack {
                    Button {
                        let name = store.state.localIdentity?.displayName ?? "Kraken"
                        transport.start(displayName: name)
                    } label: {
                        Label("Старт", systemImage: "play.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenPrimaryButtonStyle())

                    Button {
                        transport.stop()
                    } label: {
                        Label("Стоп", systemImage: "stop.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenOutlinedButtonStyle())
                }
            }

            KrakenPanel(title: "Исходящая очередь", systemImage: "tray.and.arrow.up") {
                if store.outboxRecords.isEmpty {
                    KrakenEmptyState(systemImage: "tray", title: "Очередь пуста", detail: "Сообщения появятся после отправки.")
                } else {
                    ForEach(Array(store.outboxRecords.values).sorted(by: { $0.messageId < $1.messageId }), id: \.messageId) { record in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(record.messageId)
                                .font(.footnote.monospaced())
                            Text("Попытки: \(record.attempts), retry: \(Int(record.nextRetryDelay))s")
                                .font(.caption)
                                .foregroundStyle(KrakenPalette.secondaryText)
                            HStack(spacing: 8) {
                                Button {
                                    store.applyAck(messageId: record.messageId)
                                } label: {
                                    Label("ACK", systemImage: "checkmark.circle")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(KrakenOutlinedButtonStyle())

                                Button {
                                    store.markTransportFailure(messageId: record.messageId, error: "manual-ios-failure")
                                } label: {
                                    Label("Сбой", systemImage: "exclamationmark.triangle")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(KrakenOutlinedButtonStyle())

                                Button {
                                    store.retryMessage(messageId: record.messageId)
                                } label: {
                                    Label("Повтор", systemImage: "arrow.clockwise")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(KrakenOutlinedButtonStyle())
                            }
                        }
                    }
                }
            }

            KrakenPanel(title: "Evidence", systemImage: "doc.text.magnifyingglass") {
                HStack {
                    Button {
                        refreshEvidence()
                    } label: {
                        Label("Обновить", systemImage: "arrow.clockwise")
                    }
                    .buttonStyle(KrakenPrimaryButtonStyle())
                    Button {
                        UIPasteboard.general.string = currentEvidence
                    } label: {
                        Label("Копировать", systemImage: "doc.on.doc")
                    }
                    .buttonStyle(KrakenOutlinedButtonStyle())
                }
                if let evidenceURL {
                    ShareLink(item: evidenceURL) {
                        Label("Поделиться JSON", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KrakenOutlinedButtonStyle())
                }
                DisclosureGroup("Текущий JSON evidence") {
                    Text(currentEvidence)
                        .font(.caption.monospaced())
                        .foregroundStyle(KrakenPalette.secondaryText)
                        .textSelection(.enabled)
                        .padding(.top, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .font(.footnote.weight(.semibold))
                .foregroundStyle(KrakenPalette.primary)
                .padding(10)
                .krakenGlassInset(cornerRadius: 8)
            }
        }
    }

    private var currentEvidence: String {
        evidenceJson.isEmpty ? store.exportEvidenceJson() : evidenceJson
    }

    private func refreshEvidence() {
        evidenceJson = store.exportEvidenceJson()
        evidenceURL = try? KrakenEvidenceExporter().writeEvidence(evidenceJson)
    }
}

private struct KrakenScreen<Content: View>: View {
    var title: String
    @ViewBuilder var content: () -> Content
    @State private var didApplySmokeScroll = false

    private let bottomAnchor = "kraken-screen-bottom-anchor"

    var body: some View {
        GeometryReader { geometry in
            ScrollViewReader { proxy in
                ScrollView {
                    KrakenGlassGroup(spacing: 10) {
                        VStack(alignment: .leading, spacing: 10) {
                            content()
                            Color.clear
                                .frame(height: 1)
                                .id(bottomAnchor)
                        }
                    }
                    .frame(maxWidth: screenContentWidth(for: geometry.size.width), alignment: .center)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 10)
                    .padding(.top, 8)
                    .padding(.bottom, 124)
                }
                .onAppear {
                    applySmokeScrollIfNeeded(proxy)
                }
            }
        }
        .background(KrakenPalette.background.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(KrakenPalette.background, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
    }

    private func screenContentWidth(for screenWidth: CGFloat) -> CGFloat {
        min(max(screenWidth - 20, 300), 680)
    }

    private func applySmokeScrollIfNeeded(_ proxy: ScrollViewProxy) {
        guard !didApplySmokeScroll else { return }
        guard LaunchConfiguration.current.scrollTarget == .bottom else { return }
        didApplySmokeScroll = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            withAnimation(.easeOut(duration: 0.2)) {
                proxy.scrollTo(bottomAnchor, anchor: .bottom)
            }
        }
    }
}

private struct KrakenGlassGroup<Content: View>: View {
    var spacing: CGFloat
    @ViewBuilder var content: () -> Content

    var body: some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) {
                content()
            }
        } else {
            content()
        }
    }
}

private struct KrakenPanel<Content: View>: View {
    var title: String
    var systemImage: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: systemImage)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(KrakenPalette.text)
            content()
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .krakenGlassPanel(cornerRadius: 12)
    }
}

private struct MessageBubble: View {
    var message: LocalMessage

    var body: some View {
        let outgoing = message.direction == .outgoing
        VStack(alignment: outgoing ? .trailing : .leading, spacing: 5) {
            Text(message.body)
                .font(.subheadline)
                .foregroundStyle(outgoing ? KrakenPalette.onPrimary : KrakenPalette.text)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(outgoing ? KrakenPalette.primaryContainer : KrakenPalette.surfaceHigh, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
            Text("\(message.status.title) · \(message.messageId)")
                .font(.caption2)
                .foregroundStyle(outgoing ? KrakenPalette.primary : KrakenPalette.secondaryText)
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: outgoing ? .trailing : .leading)
    }
}

private struct KrakenEmptyState: View {
    var systemImage: String
    var title: String
    var detail: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage)
                .font(.body.weight(.semibold))
                .foregroundStyle(KrakenPalette.primary)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(KrakenPalette.text)
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(KrakenPalette.secondaryText)
            }
        }
        .padding(10)
        .krakenGlassInset(cornerRadius: 9)
    }
}

private struct KrakenKeyValue: View {
    var title: String
    var value: String
    var monospaced = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(KrakenPalette.secondaryText)
            Text(value)
                .font(monospaced ? .caption.monospaced() : .subheadline)
                .foregroundStyle(KrakenPalette.text)
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct StartPrinciple: View {
    var systemImage: String
    var title: String

    var body: some View {
        VStack(spacing: 7) {
            Image(systemName: systemImage)
                .font(.system(size: 21, weight: .medium))
                .foregroundStyle(KrakenPalette.primary)
            Text(title)
                .font(.system(size: 8.5, weight: .medium))
                .multilineTextAlignment(.center)
                .foregroundStyle(KrakenPalette.secondaryText)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
        }
        .frame(width: 78)
    }
}

private struct KrakenTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .font(.subheadline)
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .foregroundStyle(KrakenPalette.text)
            .krakenGlassInset(cornerRadius: 8)
    }
}

private struct KrakenPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(KrakenPalette.onPrimary)
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 10)
            .frame(minHeight: 38)
            .krakenGlassControl(
                fill: KrakenPalette.primary.opacity(configuration.isPressed ? 0.74 : 0.96),
                stroke: KrakenPalette.primary.opacity(0.42),
                cornerRadius: 9,
                prominent: true
            )
    }
}

private struct KrakenOutlinedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(KrakenPalette.text)
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 10)
            .frame(minHeight: 38)
            .krakenGlassControl(
                fill: KrakenPalette.surface.opacity(configuration.isPressed ? 0.62 : 0.34),
                stroke: KrakenPalette.primary.opacity(0.72),
                cornerRadius: 9,
                prominent: false
            )
    }
}

private struct KrakenIconButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(KrakenPalette.onPrimary)
            .frame(width: 40, height: 40)
            .krakenGlassCircle(fill: KrakenPalette.primary.opacity(configuration.isPressed ? 0.74 : 0.96))
    }
}

private extension View {
    @ViewBuilder
    func krakenTabBarMinimizeOnScroll() -> some View {
        if #available(iOS 26.0, *) {
            self.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }

    @ViewBuilder
    func krakenGlassPanel(cornerRadius: CGFloat) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        if #available(iOS 26.0, *) {
            self
                .background(KrakenPalette.surface.opacity(0.46), in: shape)
                .glassEffect(.regular, in: shape)
                .overlay(shape.stroke(KrakenPalette.primary.opacity(0.18), lineWidth: 1))
        } else {
            self
                .background(KrakenPalette.surface, in: shape)
                .overlay(shape.stroke(KrakenPalette.outline, lineWidth: 1))
        }
    }

    @ViewBuilder
    func krakenGlassInset(cornerRadius: CGFloat) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        if #available(iOS 26.0, *) {
            self
                .background(KrakenPalette.surfaceHigh.opacity(0.48), in: shape)
                .glassEffect(.regular, in: shape)
        } else {
            self.background(KrakenPalette.surfaceHigh, in: shape)
        }
    }

    @ViewBuilder
    func krakenGlassControl(fill: Color, stroke: Color, cornerRadius: CGFloat, prominent: Bool) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        if #available(iOS 26.0, *) {
            self
                .background(fill, in: shape)
                .glassEffect(.regular.interactive(), in: shape)
                .overlay(shape.stroke(stroke, lineWidth: 1))
        } else {
            self
                .background(fill, in: shape)
                .overlay(shape.stroke(stroke, lineWidth: 1))
        }
    }

    @ViewBuilder
    func krakenGlassCircle(fill: Color) -> some View {
        if #available(iOS 26.0, *) {
            self
                .background(fill, in: Circle())
                .glassEffect(.regular.interactive(), in: Circle())
        } else {
            self.background(fill, in: Circle())
        }
    }
}

private enum KrakenPalette {
    static let background = Color(red: Double(0x07) / 255.0, green: Double(0x11) / 255.0, blue: Double(0x15) / 255.0)
    static let surface = Color(red: Double(0x0F) / 255.0, green: Double(0x1C) / 255.0, blue: Double(0x24) / 255.0)
    static let surfaceHigh = Color(red: Double(0x18) / 255.0, green: Double(0x2B) / 255.0, blue: Double(0x35) / 255.0)
    static let primary = Color(red: Double(0x23) / 255.0, green: Double(0xDA) / 255.0, blue: Double(0xD4) / 255.0)
    static let primaryContainer = Color(red: Double(0x12) / 255.0, green: Double(0x3C) / 255.0, blue: Double(0x40) / 255.0)
    static let text = Color(red: Double(0xE9) / 255.0, green: Double(0xED) / 255.0, blue: Double(0xF0) / 255.0)
    static let secondaryText = Color(red: Double(0xB7) / 255.0, green: Double(0xC1) / 255.0, blue: Double(0xC9) / 255.0)
    static let outline = Color(red: Double(0x25) / 255.0, green: Double(0x2C) / 255.0, blue: Double(0x35) / 255.0)
    static let onPrimary = Color(red: Double(0x02) / 255.0, green: Double(0x08) / 255.0, blue: Double(0x0B) / 255.0)
}

#Preview {
    KrakenIOSRootView(store: KrakenIOSFixtures.makeDemoStore(), transport: IOSNearbyTransportAdapter())
}
