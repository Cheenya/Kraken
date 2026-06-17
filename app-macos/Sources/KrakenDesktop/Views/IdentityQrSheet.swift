import Foundation
import KrakenDesktopCore
import SwiftUI

struct IdentityQrSheet: View {
    let identity: LocalIdentity
    let closeAction: () -> Void
    @State private var invite: IdentityInviteSnapshot?
    @State private var now = Date()
    @State private var technicalDetailsVisible = false

    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Мой QR")
                        .font(.title2.weight(.semibold))
                    Text(identity.displayName)
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

            if let invite {
                HStack(alignment: .top, spacing: 18) {
                    QRCodeImage(payload: invite.payload)
                        .frame(width: 260, height: 260)
                        .padding(18)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 8))

                    VStack(alignment: .leading, spacing: 14) {
                        KeyValueGrid(
                            items: [
                                ("Имя", identity.displayName),
                                ("Отпечаток", KrakenFormatters.compactFingerprint(identity.fingerprint)),
                            ]
                        )

                        Label("Покажите этот код второму устройству", systemImage: "camera.viewfinder")
                        Label(statusTitle(for: invite), systemImage: remainingSeconds(for: invite) > 0 ? "timer" : "exclamationmark.triangle")
                            .foregroundStyle(remainingSeconds(for: invite) > 0 ? Color.secondary : Color.orange)

                        Button {
                            refreshInvite()
                        } label: {
                            Label("Обновить QR", systemImage: "arrow.clockwise")
                        }
                        .buttonStyle(.bordered)

                        DisclosureGroup("Технические данные", isExpanded: $technicalDetailsVisible) {
                            Text(invite.payload)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                        }
                    }
                    .foregroundStyle(.secondary)
                }
            } else {
                ProgressView()
                    .onAppear { refreshInvite() }
            }
        }
        .padding(24)
        .frame(width: 720)
        .onReceive(timer) { now = $0 }
    }

    private func refreshInvite() {
        let createdAt = Date()
        invite = IdentityInviteSnapshot(
            payload: invitePayload(createdAt: createdAt),
            createdAt: createdAt,
            expiresAt: createdAt.addingTimeInterval(15 * 60)
        )
        now = createdAt
    }

    private func invitePayload(createdAt: Date) -> String {
        let nowMillis = Int64(createdAt.timeIntervalSince1970 * 1000)
        let payload: [String: Any] = [
            "type": "one_time_invite",
            "version": 1,
            "invite_id": "invite-\(UUID().uuidString)",
            "scope": "DIRECT_CONTACT",
            "inviter_display_name": identity.displayName,
            "inviter_public_key_encoded": identity.publicKeyEncoded,
            "inviter_fingerprint": identity.fingerprint,
            "created_at_epoch_millis": nowMillis,
            "expires_at_epoch_millis": nowMillis + 15 * 60 * 1000,
            "one_time": true,
            "requires_handshake": true,
            "requires_approval": false,
            "nonce": "nonce-\(UUID().uuidString)",
            "capabilities": [
                "kraken.invite.v1",
                "kraken.relationship.v1",
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

    private func remainingSeconds(for invite: IdentityInviteSnapshot) -> Int {
        max(0, Int(invite.expiresAt.timeIntervalSince(now).rounded(.down)))
    }

    private func statusTitle(for invite: IdentityInviteSnapshot) -> String {
        let remainingSeconds = remainingSeconds(for: invite)
        guard remainingSeconds > 0 else { return "QR истёк" }
        return String(format: "истечёт через %02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
    }
}

struct HandshakeConfirmationQrSheet: View {
    let confirmation: HandshakeConfirmationSnapshot
    let closeAction: () -> Void
    @State private var technicalDetailsVisible = false

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(confirmation.title)
                        .font(.title2.weight(.semibold))
                    Text(confirmation.subtitle)
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

            HStack(alignment: .top, spacing: 18) {
                QRCodeImage(payload: confirmation.payload)
                    .frame(width: 260, height: 260)
                    .padding(18)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 8))

                VStack(alignment: .leading, spacing: 14) {
                    KeyValueGrid(items: confirmation.details)

                    Label("Покажите этот код второму устройству", systemImage: "camera.viewfinder")
                    Label("завершает QR-рукопожатие", systemImage: "checkmark.seal")

                    DisclosureGroup("Технические данные", isExpanded: $technicalDetailsVisible) {
                        Text(confirmation.payload)
                            .font(.system(.caption, design: .monospaced))
                            .textSelection(.enabled)
                    }
                }
                .foregroundStyle(.secondary)
            }
        }
        .padding(24)
        .frame(width: 720)
    }
}

private struct IdentityInviteSnapshot {
    let payload: String
    let createdAt: Date
    let expiresAt: Date
}
