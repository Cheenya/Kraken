# Чеклист Kraken Research Demo v1

Статус: чеклист кандидата релиза для debug-сборки исследовательского демо.

## Текущая Ветка

- Ветка: `codex/android-research-panel-report-viewer`
- APK: `app-android/app/build/outputs/apk/debug/app-debug.apk`

## Реализовано Для Демо-Кандидата

- локальный профиль Kraken;
- QR-first сценарий приглашения;
- сканер QR через камеру;
- взаимное QR-рукопожатие без сервера;
- локальная доменная модель сообщений;
- packet envelope и ограниченные хранилища;
- loopback delivery pipeline с проверкой доверия;
- mesh-диагностика и метрики;
- прямой LAN NSD + TCP transport;
- усиление QR trust-gating;
- моделируемая store-and-forward ретрансляция, выключенная по умолчанию без явного relay experiment;
- интерфейсы crypto envelope и guard для debug plaintext compatibility;
- вспомогательные проверки transport hardening;
- roadmap для нескольких транспортов;
- P2P evidence reports/checklists.

## Проверки Перед Тегом `kraken-research-demo-v1`

- Android unit tests пройдены.
- `assembleDebug` пройден.
- `installDebug` пройден на устройстве.
- QR-рукопожатие на двух устройствах проверено.
- Прямое LAN P2P-сообщение проверено.
- Подтверждение доставки проверено.
- Набор скриншотов снят:
  - стартовый экран;
  - My QR;
  - сканер;
  - контакты в состояниях pending/active;
  - чат со статусами сообщений;
  - Mesh-диагностика;
  - Research mode;
  - настройки и ограничения.
- `reports/out/two_device_delivery_evidence.md` заполнен данными с реальных устройств.
- Подготовлен repeatable helper для two-device capture:
  `scripts/capture_two_phone_smoke_evidence.sh`.
- После cleanup создан свежий capture bundle через этот helper.
  - `artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/`

## Следующие Слои

- Защищённый путь данных и debug compatibility path разделены.
- Хранение ключей в Android Keystore ведётся отдельным миграционным слоем.
- Подписи QR и packet envelope ведутся отдельными слоями.
- LAN discovery остаётся транспортной подсказкой; доверие задаёт QR/relationship binding.
- Ретрансляция смоделирована и выключена по умолчанию, пока явно не включён relay experiment.
- Профиль Kraken остаётся локальным: без account, login, phone или email binding.
- Public discovery и cloud/server relay находятся вне этого чеклиста.

## Политика Тега

Не создавать `kraken-research-demo-v1` без явного согласия пользователя и
отдельного release pass. Ручное состояние доставки на двух устройствах теперь
повторяемо фиксируется на уровне UI после cleanup/transport изменений. Это
означает, что evidence повторяем как capture bundle, а автоматическая
оркестрация отправки сообщений остаётся отдельной задачей.

Текущий scope: demo release candidate с повторяемым ручным evidence на двух устройствах.
