import KrakenDesktopCore
import SwiftUI

struct WelcomeIdentityView: View {
    @EnvironmentObject private var store: KrakenDesktopStore
    @State private var displayName = "Kraken Desktop"

    var body: some View {
        StartScreenScaffold {
            CreateIdentityCard(displayName: $displayName) {
                store.createIdentity(displayName: displayName)
            }
        }
    }
}

struct KrakenMark: View {
    let size: CGFloat

    var body: some View {
        KrakenBrandImage(asset: .appIcon)
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: max(8, size * 0.18)))
    }
}

struct ReturningIdentityView: View {
    @EnvironmentObject private var store: KrakenDesktopStore
    var qrAction: (() -> Void)?

    var body: some View {
        StartScreenScaffold {
            ReturningIdentityCard(
                identity: store.state.localIdentity,
                openAction: store.openKraken,
                qrAction: qrAction ?? store.showQr
            )
        }
    }
}

private struct StartScreenScaffold<Card: View>: View {
    @ViewBuilder var card: Card

    var body: some View {
        GeometryReader { proxy in
            let horizontalPadding = max(28, min(58, proxy.size.width * 0.06))
            let brandScale = min(1.18, max(0.72, proxy.size.width / 1_180))

            Group {
                if proxy.size.width >= 980 {
                    HStack(alignment: .center, spacing: 34) {
                        StartBrandBlock(scale: brandScale)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .offset(y: -12 * brandScale)

                        card
                            .frame(width: 360)
                    }
                    .padding(.horizontal, horizontalPadding)
                    .padding(.vertical, 42)
                    .frame(maxWidth: 1_180, maxHeight: .infinity, alignment: .center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else {
                    ScrollView {
                        VStack(alignment: .center, spacing: 30) {
                            StartBrandBlock(scale: brandScale)
                            card
                                .frame(maxWidth: 380)
                        }
                        .padding(.horizontal, horizontalPadding)
                        .padding(.vertical, 34)
                        .frame(
                            maxWidth: .infinity,
                            minHeight: max(0, proxy.size.height - 68),
                            alignment: .center
                        )
                        .offset(y: min(24, proxy.size.height * 0.035))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .background {
                ZStack {
                    KrakenBrandImage(asset: .startBackground)
                        .scaledToFill()

                    LinearGradient(
                        colors: [
                            Color.black.opacity(0.74),
                            Color.black.opacity(0.42),
                            Color.black.opacity(0.80),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                }
                .ignoresSafeArea()
            }
        }
        .frame(minWidth: 760, minHeight: 560)
    }
}

private struct StartBrandBlock: View {
    let scale: CGFloat

    var body: some View {
        VStack(alignment: .center, spacing: 22 * scale) {
            KrakenLockup(scale: scale)
            Text("ПРИВАТНО • ЛОКАЛЬНО • СВОБОДНО")
                .font(.system(size: 22 * scale, weight: .medium, design: .rounded))
                .foregroundStyle(Color.cyan.opacity(0.88))
                .tracking(1.8 * scale)
                .multilineTextAlignment(.center)
                .lineLimit(1)
                .allowsTightening(true)
        }
        .foregroundStyle(.white)
        .frame(width: 620 * scale, alignment: .center)
    }
}

private struct CreateIdentityCard: View {
    @Binding var displayName: String
    let createAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 5) {
                Text("Ваш профиль")
                    .font(.title2.weight(.semibold))
                Text("Имя будет видно вашим контактам")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }

            TextField("Ваше имя", text: $displayName)
                .textFieldStyle(.roundedBorder)

            Button(action: createAction) {
                Label("Продолжить", systemImage: "arrow.right")
                    .frame(maxWidth: .infinity)
            }
            .controlSize(.large)
            .buttonStyle(.borderedProminent)

            Text("После создания профиля откроются настройки личности.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(24)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
        .overlay {
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.cyan.opacity(0.18), lineWidth: 1)
        }
    }
}

private struct ReturningIdentityCard: View {
    let identity: LocalIdentity?
    let openAction: () -> Void
    let qrAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 5) {
                Text(identity?.displayName ?? "Kraken")
                    .font(.title2.weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(identity.map { KrakenFormatters.compactFingerprint($0.fingerprint) } ?? "")
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Button(action: openAction) {
                Label("Открыть Kraken", systemImage: "arrow.right")
                    .frame(maxWidth: .infinity)
            }
            .controlSize(.large)
            .buttonStyle(.borderedProminent)

            Button(action: qrAction) {
                Label("Мой QR", systemImage: "qrcode")
                    .frame(maxWidth: .infinity)
            }
            .controlSize(.large)

            Text("Профиль сохранён на этом Mac.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(24)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
        .overlay {
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.cyan.opacity(0.18), lineWidth: 1)
        }
    }
}

struct KrakenLockup: View {
    let scale: CGFloat

    var body: some View {
        HStack(alignment: .center, spacing: 24 * scale) {
            ZStack {
                RoundedRectangle(cornerRadius: 25 * scale)
                    .fill(Color.black.opacity(0.35))

                KrakenBrandImage(asset: .appIcon)
                    .scaledToFit()
                    .padding(8 * scale)
            }
            .frame(width: 126 * scale, height: 126 * scale)
            .clipShape(RoundedRectangle(cornerRadius: 25 * scale))
            .shadow(color: Color.cyan.opacity(0.28), radius: 24)

            VStack(alignment: .center, spacing: 2) {
                Text("KRAKEN")
                    .font(.system(size: 58 * scale, weight: .bold, design: .rounded))
                Text("D E S K T O P")
                    .font(.system(size: 25 * scale, weight: .medium, design: .rounded))
                    .tracking(7 * scale)
                    .foregroundStyle(Color.cyan.opacity(0.86))
            }
            .fixedSize()
        }
    }
}
