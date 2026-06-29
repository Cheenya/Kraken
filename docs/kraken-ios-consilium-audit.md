# Kraken iOS Consilium Audit

Date: 2026-06-24

Branch: `codex/kraken-ios`

## Scope

Independent review of the current iOS port against the reported gaps:

- missing launch/start screen;
- missing app icons;
- missing iOS interface technologies;
- oversized titles and controls;
- broken scrolling / content hidden behind the bottom bar;
- stale or incorrect welcome copy.

## Review Slices

1. UI/UX evidence review: SwiftUI root view, launch plist, assets and smoke PNGs.
2. Technical boundary review: QR handshake, packet policy, MultipeerConnectivity
   boundary and persistence.
3. Source-of-truth review: Android/macOS/iOS brand copy and docs claims.

## Closed Findings

- Start screen exists in `app-ios/KrakenIOS/Views/KrakenIOSRootView.swift` and
  uses `StartBackground`, `LaunchMark`, `K R A K E N`, `I O S` and the current
  slogan `ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО`.
- Launch screen is configured through `UILaunchScreen` with `BrandBackground`
  and scaled `LaunchGlyph` assets.
- App icons cover iPhone, iPad and iOS marketing slots.
- Xcode project/scheme packaging is guarded: `local.kraken.ios` bundle id,
  explicit `Info.plist`, `AppIcon`, iPhone+iPad target family and launch/test
  scheme entries.
- iOS shell uses native `TabView`, `NavigationStack`, SF Symbols,
  `GlassEffectContainer` and iOS 26 `tabBarMinimizeBehavior(.onScrollDown)`.
- The first post-welcome iOS surface is now `Главная` instead of the earlier
  raw chat harness. It ports the Android home structure at native SwiftUI level:
  profile, local Apple transport, QR actions, dialogue metrics and access to
  messages.
- `TabView` wiring is guarded so each product tab has exactly one SwiftUI
  selection tag and the expected title: `Главная`, `Контакты`, `Реалмы`,
  `Настройки`.
- Large-title regression is guarded by inline navigation titles.
- Long-screen bottom states and compact iPhone SE states are represented in
  `artifacts/ios-smoke/current-manifest.json`.
- iPad (A16) Simulator welcome and Settings bottom states are represented in
  `artifacts/ios-smoke/current-manifest.json` as tablet layout sanity evidence.
- Smoke evidence no longer accepts early `LaunchGlyph` frames as tab screens:
  `capture_ios_smoke.py` retries non-welcome captures and
  `verify_ios_smoke.py` pixel-checks iPhone/compact bottom tab chrome and iPad
  top tab chrome.
- Demo settings/profile copy now uses `Kraken iOS` instead of iPhone-specific
  identity text, so tablet evidence does not show the wrong device class.
- Wide iPad layout constrains welcome actions and scroll content so controls do
  not stretch edge-to-edge on tablet screens.
- Android stale welcome copy was corrected to `ЛОКАЛЬНО` and is guarded by
  Android unit tests plus the iOS smoke verifier's cross-platform brand-source
  check.
- QR confirmation activation now requires the pending `invite_id`, generated
  `response_id` and local responder fingerprint before a relationship becomes
  active.
- Multipeer sends no longer broadcast relationship packets to every connected
  peer. The UI sends to the selected relationship's connected peer display name,
  and incompatible discovery-info peers are ignored before auto-invite.
- Root `README.md` now pins the current cross-platform slogan.

## Remaining Gates

- Physical iPhone/iPad MultipeerConnectivity validation.
- Android/iOS physical QR invite -> response -> confirmation interop.
- Android/iOS physical packet-envelope interop and hostile-packet negative
  checks.
- Physical gates should be captured with
  `app-ios/Scripts/prepare_ios_device_validation.py` and
  `docs/kraken-ios-device-validation-runbook.md`.
- A physical gate cannot be closed by top-level `status` alone; each gate entry
  must be `passed` with valid `verifiedAtUtc`, existing repo-relative
  `artifactPaths` and non-empty `verdictNotes`.
- Requirement-by-requirement status is tracked in
  `docs/kraken-ios-readiness-matrix.md`.
- iPad Simulator smoke does not replace physical iPad review.
- Visual launch-screen proof includes generated
  `artifacts/ios-smoke/kraken-ios-launch-reference.png` from `UILaunchScreen`
  assets. It is still not a physical launch timing screenshot; do not treat
  Simulator transition-card screenshots as valid launch evidence.
- `--kraken-scroll=bottom` smoke captures prove bottom reachability above native
  tab chrome. They are not a substitute for full visual review of every
  intermediate scroll position.
- Stale comparison PNGs remain physically present in `artifacts/ios-smoke/`,
  but `current-manifest.json` is the current evidence source and excludes them.

## Verification

- `python3 app-ios/Scripts/verify_ios_smoke.py`
- `python3 app-ios/Scripts/audit_ios_port_readiness.py --fail-on-missing-proven --check-matrix-doc docs/kraken-ios-readiness-matrix.md`
- `python3 app-ios/Scripts/render_ios_launch_reference.py`
- `python3 app-ios/Scripts/capture_ios_smoke.py --skip-build --only compact`
- `python3 app-ios/Scripts/prepare_ios_device_validation.py --label preflight --require-ios-count 2 --require-android-count 1`
- `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test --package-path app-ios --scratch-path /tmp/kraken-ios-binding-build`
- `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -project app-ios/KrakenIOS.xcodeproj -scheme KrakenIOS -destination id=F6B53733-34F3-4C87-9A28-BC465BAF59A2 -derivedDataPath app-ios/DerivedData -quiet`
- `(cd app-android && ./gradlew testDebugUnitTest --tests com.disser.kraken.ui.WelcomeBrandingGuardTest --tests com.disser.kraken.ui.MainUiCopyGuardTest)`
