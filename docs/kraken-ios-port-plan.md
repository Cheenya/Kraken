# Kraken iOS Port Plan

Branch: `codex/kraken-ios`

Goal: build Kraken as a native iOS SwiftUI app for the separate iOS branch without mixing iOS work into the existing Android or macOS branches.

## Source Boundaries

- Android remains the behavior and mobile product source of truth for packet fields, QR handshake data, trusted device identity, ACK/TTL/messageId semantics, outbox states, route/evidence wording, branded welcome imagery, bottom navigation order and Wi-Fi Direct limitations.
- macOS remains the Swift starting point for models, QR normalization, packet policy validation, outbox backoff, LAN/BLE frame compatibility and smoke-test style.
- iOS UI chrome must stay native to iPhone. Use SwiftUI `TabView`/system tab bar behavior rather than a custom Android bottom bar.
- Brand copy is `ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО`. Do not reintroduce stale `РЯДОМ` wording.
- iOS transport is Apple-specific. It may use MultipeerConnectivity and local-network primitives, but must not be described as Android Wi-Fi Direct.
- User-facing copy follows `docs/kraken-cross-platform-copy-rules.md`: Russian text, `Профиль Kraken`, `QR-приглашение` / `QR-ответ` / `QR подтверждения`, no `локальная личность`, no stale `рядом` slogan.

## Planned Files

- `app-ios/KrakenIOS.xcodeproj/`: iOS project for Simulator/device builds.
- `app-ios/KrakenIOS/`: SwiftUI app, screens and platform store.
- `app-ios/KrakenIOS/Core/`: portable iOS core adapted from the macOS Swift core.
- `app-ios/KrakenIOS/Transport/`: iOS local transport adapter and diagnostics.
- `app-ios/KrakenIOSTests/`: XCTest coverage for QR, packet policy, outbox, timeline and transport boundary.
- `app-ios/README.md`: iOS build/run notes and explicit transport boundary.
- `docs/kraken-ios-technical-design-state.md`: current technical/design state, screenshots, verification and remaining risks.

## Implementation Steps

1. Create an iOS project and test target on `codex/kraken-ios`.
2. Add failing XCTest coverage for QR data normalization, packet policy rejection, outbox backoff and iOS transport naming/boundary.
3. Implement the minimal iOS core needed to pass those tests.
4. Add SwiftUI app shell aligned with Android product routes: `Чаты / Контакты / Реалмы / Настройки`, native iPhone tab navigation, branded welcome screen, launcher/launch assets and Android-derived imagery.
5. Add a platform store that supports profile creation, contact import, QR import/export, message send, ACK/failure state, outbox retry state and evidence export.
6. Add a MultipeerConnectivity transport adapter with explicit diagnostic states and no Wi-Fi Direct claim.
7. Build on iOS Simulator and run the iOS test target.
8. Update README docs with iOS build/run instructions and remaining real-device validation.

## Done Criteria

- `codex/kraken-ios` exists and contains all iOS changes.
- iOS app builds for Simulator.
- XCTest or smoke coverage verifies shared protocol/QR/outbox behavior.
- UI exposes brand-aligned welcome, app icon/launch assets, native iPhone tab navigation, profile, contacts, QR handshake/import/export, chat, local realm management, transport status, outbox/ACK and diagnostics.
- Realm support currently covers local create/list/member/lifecycle flows. Realm QR invites, moderation and pending approvals still require a separate Android parity and device interop pass.
- Documentation states that the iOS transport is local Apple transport, not Android Wi-Fi Direct.
- No commit or push is made without explicit user command.
