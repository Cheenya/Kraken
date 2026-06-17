import AppKit
import CoreImage.CIFilterBuiltins
import KrakenDesktopCore
import SwiftUI

struct MyQrView: View {
    @EnvironmentObject private var store: KrakenDesktopStore
    @State private var technicalDetailsVisible = false
    @State private var invite: QrInviteSnapshot?
    @State private var now = Date()

    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HeaderBlock(
                    title: "Мой QR",
                    subtitle: "Откройте Kraken на втором устройстве и наведите камеру на код."
                )

                if let identity = store.state.localIdentity {
                    if let invite, invite.identityId == identity.identityId {
                        ViewThatFits(in: .horizontal) {
                            HStack(alignment: .top, spacing: 24) {
                                QrCodeCard(
                                    payload: invite.payload,
                                    remainingSeconds: remainingSeconds(for: invite),
                                    refreshAction: { refreshInvite(for: identity) }
                                )
                                .frame(width: 350)
                                QrSidePanel(
                                    identity: identity,
                                    payload: invite.payload,
                                    technicalDetailsVisible: $technicalDetailsVisible
                                )
                                .frame(width: 306)
                            }
                            .frame(width: 680, alignment: .leading)

                            VStack(alignment: .center, spacing: 20) {
                                QrCodeCard(
                                    payload: invite.payload,
                                    remainingSeconds: remainingSeconds(for: invite),
                                    refreshAction: { refreshInvite(for: identity) }
                                )
                                QrSidePanel(
                                    identity: identity,
                                    payload: invite.payload,
                                    technicalDetailsVisible: $technicalDetailsVisible
                                )
                                .frame(maxWidth: 520)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    } else {
                        ProgressView()
                            .onAppear { refreshInvite(for: identity) }
                    }
                } else {
                    ContentUnavailableView("Личность не создана", systemImage: "person.crop.circle.badge.questionmark")
                }
            }
            .padding(24)
        }
        .onReceive(timer) { now = $0 }
        .navigationTitle("QR")
    }

    private func refreshInvite(for identity: LocalIdentity) {
        let createdAt = Date()
        invite = QrInviteSnapshot(
            identityId: identity.identityId,
            payload: invitePayload(identity, createdAt: createdAt),
            createdAt: createdAt,
            expiresAt: createdAt.addingTimeInterval(15 * 60)
        )
        now = createdAt
    }

    private func remainingSeconds(for invite: QrInviteSnapshot) -> Int {
        max(0, Int(invite.expiresAt.timeIntervalSince(now).rounded(.down)))
    }

    private func invitePayload(_ identity: LocalIdentity, createdAt: Date) -> String {
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
}

private struct QrInviteSnapshot {
    let identityId: String
    let payload: String
    let createdAt: Date
    let expiresAt: Date
}

private struct QrCodeCard: View {
    let payload: String
    let remainingSeconds: Int
    let refreshAction: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            QRCodeImage(payload: payload)
                .frame(width: 300, height: 300)
                .padding(22)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 8))

            HStack(spacing: 10) {
                Label(statusTitle, systemImage: remainingSeconds > 0 ? "timer" : "exclamationmark.triangle")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(remainingSeconds > 0 ? Color.secondary : Color.orange)

                Button {
                    refreshAction()
                } label: {
                    Label("Обновить", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.borderless)
                .font(.caption.weight(.semibold))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 7)
            .background(.quaternary, in: Capsule())
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
    }

    private var statusTitle: String {
        guard remainingSeconds > 0 else { return "QR истёк" }
        let minutes = remainingSeconds / 60
        let seconds = remainingSeconds % 60
        return String(format: "истечёт через %02d:%02d", minutes, seconds)
    }
}

private struct QrSidePanel: View {
    let identity: LocalIdentity
    let payload: String
    @Binding var technicalDetailsVisible: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            QrProfileCard(identity: identity)
            QrHelpCard()

            DisclosureGroup("Технические данные", isExpanded: $technicalDetailsVisible) {
                Text(payload)
                    .font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 8)
            }
            .font(.callout.weight(.medium))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct QrProfileCard: View {
    let identity: LocalIdentity

    var body: some View {
        QrInfoCard(title: "Профиль") {
            VStack(alignment: .leading, spacing: 12) {
                QrValueRow(title: "Имя", value: identity.displayName)
                QrValueRow(title: "Отпечаток", value: identity.fingerprint, monospaced: true)
            }
        }
    }
}

private struct QrHelpCard: View {
    var body: some View {
        QrInfoCard(title: "Добавление контакта") {
            VStack(alignment: .leading, spacing: 12) {
                QrInstructionRow(
                    systemImage: "camera.viewfinder",
                    text: "Наведите камеру второго устройства на QR."
                )
                QrInstructionRow(
                    systemImage: "checkmark.shield",
                    text: "Сверьте имя и отпечаток перед подтверждением."
                )
            }
        }
    }
}

private struct QrInfoCard<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title)
                .font(.headline)
            content
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct QrValueRow: View {
    let title: String
    let value: String
    var monospaced = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(monospaced ? .system(.callout, design: .monospaced).weight(.semibold) : .callout.weight(.semibold))
                .lineLimit(2)
                .minimumScaleFactor(0.82)
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct QrInstructionRow: View {
    let systemImage: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: systemImage)
                .frame(width: 18)
                .foregroundStyle(.secondary)
            Text(text)
                .font(.callout)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct QRCodeImage: View {
    let payload: String

    var body: some View {
        if let image = makeQRCode(payload) {
            Image(nsImage: image)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
        } else {
            Image(systemName: "qrcode")
                .font(.system(size: 72))
                .foregroundStyle(.secondary)
        }
    }

    private func makeQRCode(_ text: String) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 12, y: 12))
        let rep = NSCIImageRep(ciImage: scaled)
        let image = NSImage(size: rep.size)
        image.addRepresentation(rep)
        return image
    }
}
