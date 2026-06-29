# Kraken iOS Technical And Design State

Branch: `codex/kraken-ios`

Last updated: 2026-06-24

## Current Direction

Kraken iOS is a native SwiftUI port in a separate app tree under `app-ios/`.
It should not copy Android chrome blindly. Android remains the primary source
for mobile product structure, data flows, QR/relationship behavior and brand
assets, but iOS navigation must feel native on iPhone.

The independent consilium review and closure notes are recorded in
`docs/kraken-ios-consilium-audit.md`.
Requirement-by-requirement readiness is tracked in
`docs/kraken-ios-readiness-matrix.md`.
Physical-device validation is prepared in
`docs/kraken-ios-device-validation-runbook.md` and
`app-ios/Scripts/prepare_ios_device_validation.py`.
The readiness audit does not trust a top-level physical evidence status alone:
each required gate must be `passed` with valid `verifiedAtUtc`, existing
repo-relative `artifactPaths` and non-empty `verdictNotes`.

## Design Source Of Truth

- Brand slogan: `ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО`.
- Do not reintroduce stale `ПРИВАТНО  •  РЯДОМ  •  СВОБОДНО` copy.
- Cross-platform UI copy rules live in
  `docs/kraken-cross-platform-copy-rules.md`.
- Android welcome source and guard tests were corrected to `ЛОКАЛЬНО`.
- iOS smoke verification now checks Android welcome, iOS welcome and root
  README together so this copy cannot silently drift again.
- macOS lockup confirms the same brand idea with `ПРИВАТНО • ЛОКАЛЬНО • СВОБОДНО`.
- iOS platform sublabel on the welcome screen is `I O S`, not `M E S S E N G E R` or `D E S K T O P`.
- Welcome imagery and icon assets come from Android/macOS Kraken brand assets.
- `UILaunchScreen` uses a dedicated scaled `LaunchGlyph` asset instead of the
  full-size 512px favicon, so the first frame is not an oversized cropped logo.

## iOS UI State

- App shell uses native SwiftUI `TabView` with iPhone system tab bar behavior.
- iPhone orientation is portrait-only until landscape has a dedicated UI pass;
  iPad still declares portrait and landscape orientations.
- The custom Android-style bottom bar was removed because it fought iPhone safe
  areas and made scroll content feel cramped.
- On iOS 26 simulator this should present as native iPhone/Liquid Glass system
  tab chrome rather than a copied Android navigation bar.
- Custom panels, inset fields, metric blocks, icon controls and Kraken button
  styles use conditional iOS 26 `glassEffect` helpers grouped by
  `GlassEffectContainer`, with stable fallback backgrounds for older iOS
  deployment targets.
- On iOS 26, the `TabView` opts into
  `tabBarMinimizeBehavior(.onScrollDown)` so long scrollable tabs use the
  native iPhone collapsing bottom bar behavior.
- Tabs remain product-aligned: `Главная`, `Контакты`, `Реалмы`, `Настройки`.
- The first post-welcome surface is now `Главная`, not a raw chat harness. It
  follows Android `HomeScreen` structure with profile state, local nearby
  transport status, QR actions, dialogue metrics and access to message flow.
- Smoke verification guards that each `TabView` tab has exactly one selection
  tag and the expected Russian title, so duplicate or stale tab wiring cannot
  silently pass.
- Navigation titles use inline display to avoid oversized large headers.
- Shared panel, button, icon button, text field and empty-state sizing has been
  reduced from the earlier oversized pass.
- Welcome screen is responsive and scroll-backed so Dynamic Island/status bar
  devices do not clip the layout.
- Long-tab bottom states are covered by smoke captures launched with
  `--kraken-scroll=bottom`, which checks that final controls can scroll above
  the native tab bar.
- Non-welcome screenshot evidence is pixel-checked so early `LaunchGlyph`
  frames cannot pass as tab screens; iPhone/compact captures require bottom tab
  chrome and iPad captures require the adapted top tab chrome.
- Compact iPhone SE (3rd generation) captures cover welcome, QR response bottom
  state and Settings bottom state on a 750x1334 viewport to guard against
  oversized controls and clipping regressions.
- iPad (A16) Simulator captures cover welcome and Settings bottom state on a
  1640x2360 viewport. This is tablet layout sanity evidence, not physical iPad
  validation.
- Wide iPad layouts constrain welcome actions and scroll content width so
  buttons and panels do not stretch edge-to-edge on tablet screens.
- Demo identity copy is platform-neutral `Kraken iOS`, not `Kraken iPhone`, so
  iPad settings evidence does not present the wrong device class.
- Settings keeps raw evidence JSON behind a disclosure by default so diagnostic
  data is available without dominating the primary settings layout.

## Technical State

- SwiftUI app target and SwiftPM test package exist under `app-ios/`.
- Core covers identity, QR import/export, relationship import, local messages,
  ACK/failure state, outbox retry, evidence export and local realm lifecycle.
- QR handshake core covers both directions at model level: iOS invite export,
  Android-style invite import, iOS QR-response generation and confirmation-based
  relationship activation.
- Confirmation-based activation is bound to the pending invite id, generated
  response id and local responder fingerprint; mismatched confirmations are
  rejected before a pending relationship becomes active.
- Contacts QR UI distinguishes invite, response, confirmation and invalid
  data states so generated QR-response is not mislabeled as the local invite.
- Smoke launch args include `--kraken-qr=invite|response|confirmation` for
  repeatable screenshots of each QR exchange state.
- Smoke launch configuration also supports `SIMCTL_CHILD_KRAKEN_*` environment
  fallbacks for tab, QR, scroll and skip-welcome state so fresh Simulator
  runtimes can capture deterministic screens even when custom launch arguments
  are not propagated reliably.
- `app-ios/Scripts/capture_ios_smoke.py` reproduces the full screenshot set
  using those environment fallbacks.
- Transport adapter is local Apple transport via MultipeerConnectivity.
  It must not be called Android Wi-Fi Direct.
- MultipeerConnectivity preflight is guarded: service type stays `kraken-ios`
  and `Info.plist` declares `_kraken-ios._tcp` in `NSBonjourServices`.
- Inbound transport messages use `kraken.ios.packet.v1` envelopes and run
  `KrakenPacketPolicyValidator` before timeline mutation.
- Inbound transport envelopes handle trusted relationship messages and ACKs,
  rejecting unresolved peers.
- Outbound live sends target the selected relationship's connected peer display
  name instead of broadcasting a packet to every connected Multipeer peer.
- Multipeer browser auto-invites only peers advertising the expected
  `ios-multipeerconnectivity` discovery info.
- State/outbox/diagnostics persist to Application Support JSON outside demo mode.
- Transport message and ACK receive paths require a trusted active relationship
  with matching `relationship_id` and `peer_fingerprint`; display-name and
  single-active-relationship fallbacks are not accepted on the live receive path.

## Current Screenshots

- `artifacts/ios-smoke/kraken-ios-native-welcome.png`
- `artifacts/ios-smoke/kraken-ios-native-home.png`
- `artifacts/ios-smoke/kraken-ios-native-contacts.png`
- `artifacts/ios-smoke/kraken-ios-native-contacts-bottom.png`
- `artifacts/ios-smoke/kraken-ios-native-contacts-qr-invite.png`
- `artifacts/ios-smoke/kraken-ios-native-contacts-qr-response.png`
- `artifacts/ios-smoke/kraken-ios-native-contacts-qr-response-bottom.png`
- `artifacts/ios-smoke/kraken-ios-native-contacts-qr-confirmation.png`
- `artifacts/ios-smoke/kraken-ios-native-realms.png`
- `artifacts/ios-smoke/kraken-ios-native-realms-bottom.png`
- `artifacts/ios-smoke/kraken-ios-native-settings.png`
- `artifacts/ios-smoke/kraken-ios-native-settings-bottom.png`
- `artifacts/ios-smoke/kraken-ios-compact-welcome.png`
- `artifacts/ios-smoke/kraken-ios-compact-contacts-qr-response-bottom.png`
- `artifacts/ios-smoke/kraken-ios-compact-settings-bottom.png`
- `artifacts/ios-smoke/kraken-ios-tablet-welcome.png`
- `artifacts/ios-smoke/kraken-ios-tablet-settings-bottom.png`

`artifacts/ios-smoke/README.md` records which screenshots are current and which
older `kraken-ios-android-*` files are stale comparison captures.
`artifacts/ios-smoke/current-manifest.json` is the machine-readable source for
the current evidence set; only files listed in `currentScreenshots` count as
current iOS UI evidence.
`artifacts/ios-smoke/kraken-ios-launch-reference.png` is a generated visual
reference from `UILaunchScreen` assets, not a Simulator screenshot.

`app-ios/Scripts/capture_ios_smoke.py` captures the current iPhone 17 native set
and compact iPhone SE set into `artifacts/ios-smoke/`. In auto mode it finds the
primary iPhone 17 simulator and finds or creates the compact
`Kraken iOS Compact SE` simulator so compact smoke capture is reproducible
without a hard-coded local UDID. It also finds iPad (A16) / iOS 26.5 for tablet
layout sanity captures.
It also regenerates the launch-screen reference from `BrandBackground` and
`LaunchGlyph`.
For non-welcome scenarios it retries screenshots that still look like early
launch frames instead of silently keeping them as current UI evidence.

`app-ios/Scripts/verify_ios_smoke.py` verifies the current screenshot set,
`current-manifest.json`, launch-screen reference, documentation references, PNG headers,
`UILaunchScreen` image binding, LaunchGlyph dimensions, AppIcon slots, welcome
assets/copy, root README slogan source-of-truth, bundle display name, URL
scheme, iPhone/iPad orientations, privacy usage strings, Liquid Glass grouping
and native tab bar scroll behavior.
It also guards Xcode project/scheme packaging settings: bundle id, `Info.plist`
binding, AppIcon binding, iPhone+iPad target family, supported platforms and
scheme launch/test entries.
It also pixel-checks tab-screen screenshots for rendered phone bottom chrome or
iPad top chrome.
It also guards against default bordered buttons returning inside the glass UI,
stale `РЯДОМ` welcome copy across Android/iOS brand sources and
always-expanded raw evidence JSON in Settings.

## Verified Locally

- `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test --package-path app-ios`
- `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild build -project app-ios/KrakenIOS.xcodeproj -scheme KrakenIOS -destination id=F6B53733-34F3-4C87-9A28-BC465BAF59A2 -derivedDataPath app-ios/DerivedData`
- `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -project app-ios/KrakenIOS.xcodeproj -scheme KrakenIOS -destination id=F6B53733-34F3-4C87-9A28-BC465BAF59A2 -derivedDataPath app-ios/DerivedData -quiet` - 17 XCTest cases passed.
- `plutil -lint app-ios/KrakenIOS/Info.plist`
- `python3 -m json.tool app-ios/KrakenIOS/Assets.xcassets/LaunchGlyph.imageset/Contents.json`
- `sips -g pixelWidth -g pixelHeight app-ios/KrakenIOS/Assets.xcassets/LaunchGlyph.imageset/launch-glyph-120.png app-ios/KrakenIOS/Assets.xcassets/LaunchGlyph.imageset/launch-glyph-240.png app-ios/KrakenIOS/Assets.xcassets/LaunchGlyph.imageset/launch-glyph-360.png`
- `python3 app-ios/Scripts/verify_ios_smoke.py`
- `python3 app-ios/Scripts/audit_ios_port_readiness.py --fail-on-missing-proven --check-matrix-doc docs/kraken-ios-readiness-matrix.md`
- `python3 app-ios/Scripts/capture_ios_smoke.py --skip-build --only compact`
- `python3 app-ios/Scripts/prepare_ios_device_validation.py --label preflight --require-ios-count 2 --require-android-count 1`
- `rg -n "glassEffect|krakenGlass" app-ios/KrakenIOS/Views/KrakenIOSRootView.swift`
- `(cd app-android && ./gradlew testDebugUnitTest --tests com.disser.kraken.ui.WelcomeBrandingGuardTest --tests com.disser.kraken.ui.MainUiCopyGuardTest)`
- `git diff --check`

## Remaining Risks

- Full visual QA is still simulator-only. Screenshots exist for the primary
  home surface, remaining tabs, long-tab bottom states and compact iPhone
  viewport states, but physical iPhone/iPad review is pending. The local
  evidence-packet harness is prepared but does not satisfy the physical run by
  itself.
- `--kraken-scroll=bottom` screenshots prove reachability above the native tab
  chrome, not final visual approval of every intermediate scroll position.
- Real-device iPhone/iPad MultipeerConnectivity validation is still pending.
- QR invite/response/confirmation interop with Android physical devices is still
  pending even though the iOS core path is now unit-tested.
- Realm QR invites, moderation and pending approvals are not yet at Android
  parity.
- Live receive now validates packet policy before message timeline mutation, but
  full Android/iOS packet interop still needs physical-device evidence.
- Android/macOS/iOS brand copy must stay synchronized; stale source tests should
  be treated as bugs, not as final design truth.
