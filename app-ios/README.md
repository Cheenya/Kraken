# Kraken iOS

Нативный iOS-порт Kraken для отдельной ветки `codex/kraken-ios`.
Общие правила текста и границ утверждений: `docs/kraken-cross-platform-copy-rules.md`.

## Что входит

- SwiftUI-каркас приложения: брендовый welcome, тёмная тема, native iPhone `TabView`/system bottom tabs `Чаты / Контакты / Реалмы / Настройки`.
- Ориентация iPhone остаётся portrait-only до отдельного landscape UI-прохода; iPad сохраняет более широкую поддержку ориентаций.
- UI reference boundary: Android даёт мобильную структуру, брендовые ассеты и продуктовые маршруты; iOS chrome должен оставаться нативным для iPhone/Liquid Glass system tab bar, без самодельной Android bottom bar.
- Custom panels, fields and Kraken button styles use conditional iOS 26 `glassEffect` helpers grouped by `GlassEffectContainer`, with fallback surfaces for older iOS targets.
- On iOS 26, the native tab bar opts into `tabBarMinimizeBehavior(.onScrollDown)` for long scrollable tabs.
- Brand copy: `ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО`. Не возвращать stale `РЯДОМ`.
- `verify_ios_smoke.py` checks this copy against Android `WelcomeScreen.kt`,
  iOS `KrakenIOSRootView.swift` and the root `README.md`.
- iOS assets берутся из Android `app/src/main/res/drawable-nodpi`: favicon, splash/start background, launcher icon base.
- Launch screen использует отдельный масштабированный asset `LaunchGlyph`, чтобы 512px favicon не растягивался на первом кадре.
- iOS core для профиля Kraken, импорта контактов, QR-рукопожатия, packet policy, outbox retry, ACK/failure state.
- QR exchange UI labels payloads by handshake role instead of treating every
  generated code as the device's own invite.
- QR export, ручной payload import и camera QR scanner через AVFoundation.
- QR handshake core покрывает импорт приглашения Android -> генерацию QR-ответа iOS -> активацию по подтверждению.
- Активация confirmation привязана к pending invite id, сгенерированному response
  id и локальному responder fingerprint, поэтому несовпадающее confirmation не может
  активировать pending relationship.
- Local realm management: create/list local realms, active-contact membership, local pause/archive/leave lifecycle.
- MultipeerConnectivity adapter для локальной связи Apple.
- Multipeer service type is `kraken-ios`; Bonjour discovery is declared as `_kraken-ios._tcp`.
- Inbound transport messages use `kraken.ios.packet.v1` envelopes and validate
  packet policy before mutating the local timeline.
- Inbound transport envelopes: trusted relationship message import, ACK
  handling and unresolved-peer rejection.
- Transport message/ACK import requires an active relationship with matching `relationship_id` and `peer_fingerprint`; display-name fallback is not used for live transport envelopes.
- Evidence export в JSON из текущего состояния приложения через copy/share flow.
- Settings по умолчанию держит raw evidence JSON свёрнутым под disclosure.
- Persistence: state/outbox/diagnostics сохраняются в Application Support JSON.
- QA/demo launch args для стабильных скриншотов.

iOS использует локальную связь Apple через MultipeerConnectivity. Android остаётся источником истины для Wi-Fi Direct reasoning и device evidence.

## Сборка

Открыть проект:

```bash
open app-ios/KrakenIOS.xcodeproj
```

CLI build на Simulator после установки совместимого iOS runtime:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcodebuild build \
  -project app-ios/KrakenIOS.xcodeproj \
  -scheme KrakenIOS \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -derivedDataPath app-ios/DerivedData
```

Если локально установлен другой runtime, поменять `name`/`OS` по выводу:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcrun simctl list devices available
```

## Тесты

Портable iOS core проверяется через SwiftPM:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test --package-path app-ios
```

Дополнительный typecheck всего app target против iOS Simulator SDK:

```bash
SDKROOT=$(DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcrun --sdk iphonesimulator --show-sdk-path)
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcrun swiftc -typecheck \
  -target arm64-apple-ios17.0-simulator \
  -sdk "$SDKROOT" \
  $(rg --files app-ios/KrakenIOS -g '*.swift')
```

## UI smoke-проверка

Обычный запуск показывает брендовый welcome screen, как в Android. Для повторяемых скриншотов:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcrun simctl launch <SIMULATOR_ID> local.kraken.ios \
  --kraken-demo
```

Сразу открыть нужную вкладку без welcome:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcrun simctl launch <SIMULATOR_ID> local.kraken.ios \
  --kraken-demo --kraken-skip-welcome --kraken-tab=home
```

Допустимые значения `--kraken-tab`: `home`, `contacts`, `realms`, `settings`.
Для свежих Simulator runtimes, где `simctl launch` может нестабильно передать
custom `--kraken-*` arguments, smoke capture также поддерживает environment
fallback через `SIMCTL_CHILD_KRAKEN_DEMO_MODE=1`,
`SIMCTL_CHILD_KRAKEN_SKIP_WELCOME=1`, `SIMCTL_CHILD_KRAKEN_TAB=<tab>`,
`SIMCTL_CHILD_KRAKEN_QR=<kind>` и `SIMCTL_CHILD_KRAKEN_SCROLL=bottom`.

Для проверки разных состояний QR-рукопожатия во вкладке контактов:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcrun simctl launch <SIMULATOR_ID> local.kraken.ios \
  --kraken-demo --kraken-skip-welcome --kraken-tab=contacts --kraken-qr=response
```

Допустимые значения `--kraken-qr`: `invite`, `response`, `confirmation`.

Для проверки нижней части длинных вкладок:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcrun simctl launch <SIMULATOR_ID> local.kraken.ios \
  --kraken-demo --kraken-skip-welcome --kraken-tab=settings --kraken-scroll=bottom
```

Переснять весь текущий набор smoke screenshots воспроизводимо:

```bash
python3 app-ios/Scripts/capture_ios_smoke.py
```

По умолчанию скрипт автоматически ищет iPhone 17 / iOS 26.5 для полного набора
и автоматически использует либо создаёт `Kraken iOS Compact SE` на базе iPhone
SE (3rd generation) / iOS 26.5 для compact viewport. Также снимается iPad
(A16) / iOS 26.5 Simulator evidence для tablet layout sanity. При другом
локальном Simulator runtime передайте `--primary-sim <UDID>`,
`--compact-sim <UDID>` и `--tablet-sim <UDID>`.

Актуальные smoke-скриншоты:

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

Статус screenshot-файлов зафиксирован в `artifacts/ios-smoke/README.md`; старые `kraken-ios-android-*` не считать текущими доказательствами.
Машинно-читаемый текущий набор закреплён в
`artifacts/ios-smoke/current-manifest.json`; только `currentScreenshots` из
этого manifest считать актуальным UI evidence.
`artifacts/ios-smoke/kraken-ios-launch-reference.png` — generated reference из
`UILaunchScreen` assets, не Simulator screenshot.
`tabletViewport` в manifest — только Simulator evidence; physical iPad review
остаётся отдельным device gate.

Проверить smoke manifest, PNG headers, LaunchGlyph, AppIcon slots, welcome
assets/copy, Android/iOS/root README brand source-of-truth, iPhone portrait
orientation, privacy usage strings, Liquid Glass grouping, tab bar behavior и
ссылки в документации. Verifier also pixel-checks non-welcome screenshots so an
early `LaunchGlyph` frame cannot pass as a tab screen:

```bash
python3 app-ios/Scripts/verify_ios_smoke.py
```

Пересобрать только launch reference без Simulator:

```bash
python3 app-ios/Scripts/render_ios_launch_reference.py
```

## Локально проверено

- iOS Simulator build прошёл на `iPhone 17`, iOS `26.5`.
- Xcode test target прошёл на Simulator: 17 XCTest cases.
- Native iPhone welcome, glass UI tabs, bottom-of-scroll and compact viewport screenshots обновлены в `artifacts/ios-smoke/`.
- iOS smoke verifier прошёл: `python3 app-ios/Scripts/verify_ios_smoke.py`.
- Readiness audit для non-physical gates и stale matrix check: `python3 app-ios/Scripts/audit_ios_port_readiness.py --fail-on-missing-proven --check-matrix-doc docs/kraken-ios-readiness-matrix.md`.
- Текущее техническое и дизайн-состояние ведётся в `docs/kraken-ios-technical-design-state.md`.

## Оставшаяся проверка

Подготовить пакет подтверждений с физических устройств:

```bash
python3 app-ios/Scripts/prepare_ios_device_validation.py \
  --label physical-run \
  --require-ios-count 2 \
  --require-android-count 1
```

Сгенерированный пакет находится в `artifacts/ios-device-validation/` and follows
`docs/kraken-ios-device-validation-runbook.md`.
`physical-device-gates` остаётся pending, пока каждый обязательный gate in
`evidence.json` не будет отмечен как `passed` с `verifiedAtUtc` и непустыми
`artifactPaths`.

- Проверить MultipeerConnectivity между двумя реальными iPhone/iPad.
- Сравнить QR invite/response/confirmation fixtures с Android на физическом устройстве.
- Довести realm QR invites, moderation и pending approvals до Android parity после device interop.
- Не заявлять parity с Android Wi-Fi Direct без отдельного device evidence.
