import KrakenDesktopCore
import SwiftUI

struct RealmsView: View {
    @EnvironmentObject private var store: KrakenDesktopStore
    let onShowRealmQr: (DesktopRealm) -> Void
    @State private var newRealmName = ""
    @State private var creatingRealm = false
    @State private var activeRealmsExpanded = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if let selectedRealm {
                    RealmDetailView(realm: selectedRealm, onShowQr: { onShowRealmQr(selectedRealm) })
                } else {
                    HeaderBlock(
                        title: "Реалмы",
                        subtitle: "Закрытые локальные круги Kraken. Вход только по QR-приглашению."
                    )

                    HStack(spacing: 10) {
                        Button {
                            creatingRealm.toggle()
                        } label: {
                            Label("Новый реалм", systemImage: "plus")
                        }
                        .buttonStyle(.borderedProminent)
                    }

                    if creatingRealm {
                        SectionBlock(title: "Создать реалм") {
                            HStack(spacing: 10) {
                                TextField("Название реалма", text: $newRealmName)
                                    .textFieldStyle(.roundedBorder)
                                Button("Создать") {
                                    store.createRealm(name: newRealmName)
                                    newRealmName = ""
                                    creatingRealm = false
                                }
                                .disabled(newRealmName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            }
                            Text("Реалм доступен только по QR-приглашениям. Публичного поиска нет.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    CollapsibleRealmSection(
                        title: "Активные реалмы",
                        emptyText: "Активных реалмов пока нет.",
                        realms: store.desktopRealms.filter { $0.state == .active },
                        expanded: $activeRealmsExpanded,
                        onShowQr: onShowRealmQr
                    )

                    RealmSection(
                        title: "Ожидают проверки",
                        emptyText: "Нет реалмов с ожидающими заявками.",
                        realms: store.desktopRealms.filter { $0.state == .pendingReview },
                        onShowQr: onShowRealmQr
                    )

                    RealmSection(
                        title: "Архив",
                        emptyText: "Архивных реалмов нет.",
                        realms: store.desktopRealms.filter { $0.state == .archived },
                        onShowQr: onShowRealmQr
                    )
                }
            }
            .padding(24)
        }
    }

    private var selectedRealm: DesktopRealm? {
        guard let selectedRealmId = store.selectedRealmId else { return nil }
        return store.desktopRealms.first { $0.realmId == selectedRealmId }
    }
}

private struct RealmDetailView: View {
    let realm: DesktopRealm
    let onShowQr: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HeaderBlock(
                title: realm.name,
                subtitle: "Локальный круг Kraken. Управление участниками и приглашением."
            )

            HStack(spacing: 10) {
                Button(action: onShowQr) {
                    Label("QR реалма", systemImage: "qrcode")
                }
                .buttonStyle(.borderedProminent)

                Button {
                } label: {
                    Label("Параметры", systemImage: "ellipsis")
                }
                .buttonStyle(.bordered)
            }

            SectionBlock(title: "Состояние") {
                KeyValueGrid(
                    items: [
                        ("Участников", "\(realm.memberCount)"),
                        ("Статус", realm.state.title),
                        ("Заявки", "\(realm.pendingRequests)"),
                        ("Обновлён", realm.updatedAt.formatted(date: .abbreviated, time: .shortened)),
                    ]
                )
            }

            SectionBlock(title: "Участники") {
                Text("Пока в этом desktop-каркасе показывается только локальная личность. Список участников появится после синхронизации состава реалма.")
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct RealmSection: View {
    let title: String
    let emptyText: String
    let realms: [DesktopRealm]
    let onShowQr: (DesktopRealm) -> Void

    var body: some View {
        SectionBlock(title: title) {
            if realms.isEmpty {
                Text(emptyText)
                    .foregroundStyle(.secondary)
            } else {
                VStack(spacing: 10) {
                    ForEach(realms) { realm in
                        RealmRow(realm: realm, onShowQr: { onShowQr(realm) })
                    }
                }
            }
        }
    }
}

private struct CollapsibleRealmSection: View {
    @Environment(\.krakenPalette) private var palette
    let title: String
    let emptyText: String
    let realms: [DesktopRealm]
    @Binding var expanded: Bool
    let onShowQr: (DesktopRealm) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                withAnimation(.easeOut(duration: 0.15)) {
                    expanded.toggle()
                }
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: expanded ? "chevron.down" : "chevron.right")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(palette.accent)
                    Text(title)
                        .font(.headline)
                    Spacer()
                    Text("\(realms.count)")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.secondary.opacity(0.12), in: Capsule())
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                if realms.isEmpty {
                    Text(emptyText)
                        .foregroundStyle(.secondary)
                } else {
                    VStack(spacing: 10) {
                        ForEach(realms) { realm in
                            RealmRow(realm: realm, onShowQr: { onShowQr(realm) })
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(palette.panelBackground, in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(palette.accent.opacity(0.10), lineWidth: 1)
        }
    }
}

private struct RealmRow: View {
    @Environment(\.krakenPalette) private var palette
    let realm: DesktopRealm
    let onShowQr: () -> Void
    @State private var hovering = false

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(palette.accent.opacity(0.18))
                Image(systemName: "person.3.sequence")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(palette.accent)
            }
            .frame(width: 46, height: 46)

            VStack(alignment: .leading, spacing: 4) {
                Text(realm.name)
                    .font(.headline)
                    .lineLimit(1)
                HStack(spacing: 8) {
                    Label("\(realm.memberCount)", systemImage: "person.2")
                    Text("•")
                    Text(realm.state.title)
                    if realm.pendingRequests > 0 {
                        Text("•")
                        Text("заявки: \(realm.pendingRequests)")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Spacer()

            RealmIconButton(
                systemImage: "qrcode",
                help: "QR-приглашение в реалм",
                action: onShowQr
            )

            Button {
            } label: {
                Image(systemName: "ellipsis")
                    .frame(width: 28, height: 28)
            }
            .buttonStyle(.borderless)
            .help("Управление реалмом")
        }
        .padding(12)
        .background(hovering ? palette.elevatedPanel : palette.inputBackground, in: RoundedRectangle(cornerRadius: 8))
        .contentShape(Rectangle())
        .onHover { hovering = $0 }
    }
}

private struct RealmIconButton: View {
    @Environment(\.krakenPalette) private var palette
    let systemImage: String
    let help: String
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 15, weight: .semibold))
                .frame(width: 30, height: 30)
                .background(hovering ? palette.accent.opacity(0.16) : Color.clear, in: Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .help(help)
    }
}

struct RealmQrSheet: View {
    let realm: DesktopRealm
    let identity: LocalIdentity?
    let closeAction: () -> Void
    @State private var invite: RealmInviteSnapshot?
    @State private var now = Date()

    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("QR реалма")
                        .font(.title2.weight(.semibold))
                    Text(realm.name)
                        .font(.headline)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button(action: closeAction) {
                    ZStack {
                        Circle()
                            .fill(Color.secondary.opacity(0.14))
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .frame(width: 34, height: 34)
                    .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .help("Закрыть")
                .keyboardShortcut(.cancelAction)
            }

            if let identity {
                if let invite {
                    HStack(alignment: .top, spacing: 18) {
                        QRCodeImage(payload: invite.payload)
                            .frame(width: 260, height: 260)
                            .padding(18)
                            .background(Color.white, in: RoundedRectangle(cornerRadius: 8))

                        VStack(alignment: .leading, spacing: 14) {
                            Label("Вход только по приглашению", systemImage: "lock")
                            Label("Потребуется подтверждение заявки", systemImage: "checkmark.shield")
                            Label(statusTitle(for: invite), systemImage: remainingSeconds(for: invite) > 0 ? "timer" : "exclamationmark.triangle")
                                .foregroundStyle(remainingSeconds(for: invite) > 0 ? Color.secondary : Color.orange)

                            Button {
                                refreshInvite(identity: identity)
                            } label: {
                                Label("Обновить QR", systemImage: "arrow.clockwise")
                            }
                            .buttonStyle(.bordered)

                            DisclosureGroup("Технические данные") {
                                Text(invite.payload)
                                    .font(.system(.caption, design: .monospaced))
                                    .textSelection(.enabled)
                            }
                        }
                        .foregroundStyle(.secondary)
                    }
                } else {
                    ProgressView()
                        .onAppear { refreshInvite(identity: identity) }
                }
            } else {
                ContentUnavailableView("Личность не создана", systemImage: "person.crop.circle.badge.questionmark")
            }
        }
        .padding(24)
        .frame(width: 720)
        .onReceive(timer) { now = $0 }
    }

    private func refreshInvite(identity: LocalIdentity) {
        let createdAt = Date()
        invite = RealmInviteSnapshot(
            payload: realmPayload(identity: identity, createdAt: createdAt),
            createdAt: createdAt,
            expiresAt: createdAt.addingTimeInterval(15 * 60)
        )
        now = createdAt
    }

    private func realmPayload(identity: LocalIdentity, createdAt: Date) -> String {
        let nowMillis = Int64(createdAt.timeIntervalSince1970 * 1000)
        let payload: [String: Any] = [
            "type": "one_time_invite",
            "version": 1,
            "invite_id": "invite-\(UUID().uuidString)",
            "scope": "REALM_MEMBERSHIP",
            "realm_id": realm.realmId,
            "realm_name": realm.name,
            "inviter_display_name": identity.displayName,
            "inviter_public_key_encoded": identity.publicKeyEncoded,
            "inviter_fingerprint": identity.fingerprint,
            "created_at_epoch_millis": nowMillis,
            "expires_at_epoch_millis": nowMillis + 15 * 60 * 1000,
            "one_time": true,
            "requires_handshake": true,
            "requires_approval": true,
            "nonce": "nonce-\(UUID().uuidString)",
            "capabilities": [
                "kraken.invite.v1",
                "kraken.relationship.v1",
                "kraken.realm.membership.request.v1",
            ],
            "crypto_profile_id": "standard-reviewed-primitives-v1",
            "crypto_profile_hash": "sha256:standard-reviewed-primitives-v1",
            "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
            "profile_policy_version": 1,
            "native_backend_version": "not-applicable-standard-profile",
            "signature": NSNull(),
        ]
        let data = (try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])) ?? Data()
        let rawPayload = String(data: data, encoding: .utf8) ?? "{}"
        return (try? KrakenHandshakeQrCodec.encodedQrPayload(rawPayload)) ?? rawPayload
    }

    private func remainingSeconds(for invite: RealmInviteSnapshot) -> Int {
        max(0, Int(invite.expiresAt.timeIntervalSince(now).rounded(.down)))
    }

    private func statusTitle(for invite: RealmInviteSnapshot) -> String {
        let remainingSeconds = remainingSeconds(for: invite)
        guard remainingSeconds > 0 else { return "QR истёк" }
        return String(format: "истечёт через %02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
    }
}

private struct RealmInviteSnapshot {
    let payload: String
    let createdAt: Date
    let expiresAt: Date
}
