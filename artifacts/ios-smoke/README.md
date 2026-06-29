# Kraken iOS Smoke Screenshots

Current iPhone 17 / iOS 26.5 native TabView / conditional glass captures:

- `kraken-ios-native-welcome.png`
- `kraken-ios-native-home.png`
- `kraken-ios-native-contacts.png`
- `kraken-ios-native-contacts-bottom.png`
- `kraken-ios-native-contacts-qr-invite.png`
- `kraken-ios-native-contacts-qr-response.png`
- `kraken-ios-native-contacts-qr-response-bottom.png`
- `kraken-ios-native-contacts-qr-confirmation.png`
- `kraken-ios-native-realms.png`
- `kraken-ios-native-realms-bottom.png`
- `kraken-ios-native-settings.png`
- `kraken-ios-native-settings-bottom.png`

Current compact iPhone SE (3rd generation) / iOS 26.5 layout captures:

- `kraken-ios-compact-welcome.png`
- `kraken-ios-compact-contacts-qr-response-bottom.png`
- `kraken-ios-compact-settings-bottom.png`

Current iPad (A16) / iOS 26.5 Simulator layout captures:

- `kraken-ios-tablet-welcome.png`
- `kraken-ios-tablet-settings-bottom.png`

These tablet captures are Simulator evidence for iPad layout sanity. Physical
iPad review remains a device-validation gate.

Launch screen visual reference:

- `kraken-ios-launch-reference.png`

This is a generated reference image from the actual `UILaunchScreen`
configuration: `BrandBackground` plus the centered `LaunchGlyph` asset. It is
not a Simulator screenshot and must not be used as physical launch timing
evidence.

The `*-bottom.png` captures are launched with `--kraken-scroll=bottom` and prove
that long tab content can scroll above the native iPhone/Liquid Glass tab bar.
The compact captures use the same app build on a 750x1334 phone viewport and
prove the welcome, QR import bottom state and Settings bottom state remain
readable without oversized controls or tab bar overlap on a small iPhone.
iPhone and compact non-welcome captures are pixel-checked for rendered bottom
tab chrome so an early `LaunchGlyph` frame cannot be accepted as current tab
evidence. iPad tab chrome is checked separately at the top of the tablet
capture because SwiftUI adapts `TabView` differently on the iPad viewport.

The repository `.gitignore` intentionally allows this README plus current
`current-manifest.json`, `kraken-ios-native-*.png` and
`kraken-ios-compact-*.png` and `kraken-ios-tablet-*.png` evidence to be added
normally. Older comparison captures in this directory stay ignored.

`current-manifest.json` is the machine-readable source for the current evidence
set. Only files listed in its `currentScreenshots` array are current iOS UI
screenshots; `launchReference.file` points at the generated launch reference and
`tabletViewport` records the iPad Simulator evidence boundary.

Recreate the full current set with:

```bash
python3 app-ios/Scripts/capture_ios_smoke.py
```

By default the capture script auto-selects iPhone 17 / iOS 26.5 for the full
native set and auto-selects or creates `Kraken iOS Compact SE` for the compact
750x1334 evidence set. Pass `--primary-sim <UDID>` or `--compact-sim <UDID>`
when validating against a different local Simulator runtime.
The script rewrites `current-manifest.json` after capture.
It also regenerates `kraken-ios-launch-reference.png` from the app's launch
assets.
For non-welcome scenarios, the script retries screenshots that still look like
an early launch frame instead of silently keeping them as UI evidence.

The `contacts-qr-*` captures are launched with `--kraken-qr=invite|response|confirmation`
and verify that the QR exchange panel labels each handshake payload role
explicitly.

Launch screen sizing is verified through the `LaunchGlyph` asset and
`UILaunchScreen` configuration. Do not add a `simctl --wait-for-debugger`
transition screenshot as current evidence; it captures an iOS system transition
card, not the fullscreen launch screen.

Older `kraken-ios-android-*.png` files are stale comparison captures from the
custom Android-style bottom bar pass. Do not use them as current UI evidence.

Older `kraken-ios-chat*.png`, `kraken-ios-native-chats.png` and
`kraken-ios-launch.png` files are early smoke captures and should not be treated
as the current design baseline.
`tmp-*.png` files are manual debug captures and are not current UI evidence.
