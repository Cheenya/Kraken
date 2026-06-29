# Android Demo Evidence Audit — 2026-05-24

Цель: отдельно проверить Android branch как demo-прототип Kraken, не смешивая
его с math/evidence branch.

## 1. Проверенный контекст

| Поле | Значение |
| --- | --- |
| Worktree | `/tmp/kraken-android-research-report-viewer-git` |
| Branch | `codex/android-research-panel-report-viewer` |
| HEAD | `ac30559 Wire mesh queue sync and realm admin actions` |
| Android app | `app-android/` |
| Package | `com.disser.kraken` |
| Manifest permissions | `android.permission.CAMERA`; `android.permission.INTERNET` for local LAN sockets only |

Исходный каталог `/tmp/kraken-android-research-report-viewer` оказался
prunable copy без `.git`, поэтому для проверки был создан свежий git worktree:
`/tmp/kraken-android-research-report-viewer-git`.

## 2. Build and Test

Команды запускались из:

```bash
cd /tmp/kraken-android-research-report-viewer-git/app-android
```

| Проверка | Результат |
| --- | --- |
| `./gradlew test` | passed, `BUILD SUCCESSFUL` |
| `./gradlew assembleDebug` | passed, `BUILD SUCCESSFUL` |
| `adb devices` | no connected devices listed |
| `./gradlew installDebug` | skipped: device not visible to ADB |

APK:

```text
/tmp/kraken-android-research-report-viewer-git/app-android/app/build/outputs/apk/debug/app-debug.apk
```

Gradle warnings remain about deprecated AGP options and legacy variant APIs.
They are not blocking this audit, but should stay tracked in build-warning
triage.

## 3. Demo-Scope Status

На текущей ветке присутствуют:

- local identity;
- QR-first invite UX;
- real invite QR rendering;
- camera QR scanner;
- QR lifecycle controls;
- offline mutual QR handshake;
- contacts pending/active flow;
- realms local prototype UX;
- Settings-hosted Research mode;
- bundled guided research examples;
- native C++ research backend benchmark path.

## 4. Safety and Claim Check

Проверка кода и активных assets показывает:

- manifest добавляет `CAMERA` и `INTERNET`, где `INTERNET` ограничен целью local LAN socket transport, не cloud/server backend;
- запрещённые permissions вроде contacts, phone, location и Bluetooth не
  добавлены;
- Research examples имеют `production_crypto_claim: false`;
- Research assets явно содержат caveat: rational diagnostics over `Q`, not
  production finite-field crypto;
- scanner copy говорит, что invite QR запускает handshake и не активирует
  контакт напрямую;
- QR flow не должен создавать `ACTIVE` из invite import alone.

Ограничения, которые должны оставаться видимыми:

- production crypto не реализована;
- Android Keystore production storage не реализован;
- signed QR proofs не реализованы;
- real P2P/mesh transport не реализован;
- Android app не запускает Python/Sage runtime.

Актуальные границы будущего P2P/mesh слоя зафиксированы отдельно:
`docs/prototype-mesh-threat-boundaries.md`.

## 5. Findings

### P0 — Stale demo docs mentioned old QR/JSON workflow

На момент аудита были найдены stale строки:

- `docs/kraken-demo-checklist.md` всё ещё говорит:
  - старый неполный QR UI;
  - ручной copy/paste payload workflow.
- `docs/phase-acceptance-checklist.md` всё ещё содержит старый статус:
  - QR rendering не был отражён как реализованный;
  - ручной payload flow был описан как основной acceptance path.
- `docs/android-ui-implementation-plan.md` содержит историческую формулировку
  про незавершённый QR UI и visible payload text.

Это не ломает сборку, но может запутать reviewer/demo flow. Текущая продуктовая
линия должна быть QR-first; JSON/payload text может оставаться
внутренним/debug форматом, но не пользовательским workflow.

### P1 — Screenshot evidence was not captured in this run

`adb devices` не показал подключённый телефон, поэтому install/screenshot
smoke не выполнен. Для финального demo evidence нужно отдельно собрать
скриншоты:

- start screen;
- My QR;
- scanner;
- contacts pending/active;
- Research Panel;
- settings/limitations.

### P1 — Research remains diagnostic-only and must stay framed that way

Research mode содержит полезные bundled examples и native benchmark path, но
его нельзя подавать как production encryption или finite-field ECC security
доказательство. Текущие assets в целом держат caveat корректно.

## 6. Manual Smoke Checklist For Next Device Run

Когда телефон виден в ADB:

```bash
cd /tmp/kraken-android-research-report-viewer-git/app-android
PATH="$HOME/Library/Android/sdk/platform-tools:$PATH" ./gradlew installDebug
```

Проверить вручную:

1. Запуск приложения, стартовый экран и отсутствие визуальных артефактов.
2. Создание local identity.
3. My QR показывает реальный QR.
4. Scanner открывается, permission copy понятен.
5. Invite QR import создаёт pending relationship, не `ACTIVE`.
6. Response QR и final confirmation доводят offline handshake до `ACTIVE`.
7. Contacts показывает pending/active состояния понятно.
8. Research mode доступен из Settings, не из primary bottom nav.
9. Research mode явно не обещает production encryption.
10. Settings/limitations не заявляют P2P/mesh как реализованный transport.

## 7. Conclusion

Android branch сейчас годится для controlled dissertation demo как локальный
prototype: QR handshake, contacts/realms state и offline Research Panel
существуют и собираются. Он не готов как production messenger.

Ближайшая практическая правка перед показом: очистить stale demo docs от
устаревших QR/manual-payload сценариев и собрать screenshot evidence на
подключённом устройстве.
