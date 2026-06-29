# Roadmap готовности Kraken к промышленному выпуску

Дата: 2026-06-08.

Этот документ отделяет будущий промышленный roadmap Kraken от текущего dissertation research prototype. Он не является заявлением о готовности продукта к промышленной защите сообщений и не расширяет текущие claims прототипа. Текущая ветка остаётся mesh/serverless: доверие устанавливается локально через QR/relationship workflow, а план не вводит server-scale архитектуру, аккаунты, публичный каталог или централизованную историю.

## 1. Что сейчас является prototype-only

Текущий Android-прототип можно использовать как исследовательский и демонстрационный контур, но не как промышленный защищённый мессенджер. Prototype-only зона включает:

- placeholder identity storage без Android Keystore и без non-exportable private keys;
- `PrototypeNoSecurityPacketCrypto`, который явно не подписывает и не шифрует packet payload;
- QR invite/response/confirmation metadata без промышленной схемы подписи и replay model;
- plaintext/prototype JSON payload внутри mesh packet;
- admission/evidence metadata для experimental crypto profile, включая контур
  допуска на основе диагностики кручения рациональных кривых, как
  policy/evidence слой, а не доказательство криптостойкости сообщений;
- LAN/BLE ручные evidence и diagnostics без статистически подтверждённой надёжности;
- debug/research UI, screenshots, local reports и dissertation artifacts;
- build/config режимы, где prototype crypto допустим только для debug/research сборок.

Правило wording: текущий статус формулируется как “research prototype with explicit prototype crypto boundary”. Нельзя писать, что это промышленно готовая защищённая система сообщений.

## 2. Что требуется для production identity

Production identity должна быть отдельной фазой, без silent-upgrade существующих placeholder identities.

Требования:

- Android Keystore-backed key provider для identity keys;
- non-exportable private keys: приватный материал не покидает Keystore и не сериализуется в app storage;
- явная schema/version boundary между placeholder identity и production identity;
- migration from placeholder identity через пользовательский flow: старый ключ не становится доверенным production ключом автоматически;
- fingerprint/display UX, где display name не является trust anchor;
- backup/recovery policy: если восстановление не поддерживается, UI прямо объясняет, что новый ключ означает нового пользователя;
- локальные тесты на отсутствие device identifiers, phone/email/login/account fields как identity material.

Критерии приёмки:

- release/prod build не может создать или использовать placeholder identity provider;
- старые prototype identities получают explicit “migration required” state;
- private key export API отсутствует на production provider boundary;
- threat model описывает потерю устройства, reinstall, backup, key reset и user-visible trust impact.

## 3. Что требуется для signed QR

QR handshake должен перейти от prototype metadata к подписанному локальному handshake, без server-scale coordinator.

Требования:

- canonical payload для invite, response и final confirmation: стабильная сериализация, field order/normalization, versioning и size budget;
- invite signature поверх canonical invite payload;
- response signature поверх canonical response payload и binding к invite id/nonce/profile metadata;
- final confirmation signature поверх confirmation payload, relationship id и выбранной policy версии;
- replay protection: одноразовый nonce, expiry, seen-cache для invite/response/confirmation, state-machine guard против повторной активации;
- downgrade protection: signature покрывает crypto profile id/hash, admission decision hash, protocol version и capabilities;
- QR import остаётся pending до explicit user approval/confirmation, если relationship ещё не активирован.

Критерии приёмки:

- tampered QR payload rejected;
- replayed invite/response/confirmation rejected;
- mismatched profile/admission metadata rejected;
- unsigned payload rejected in release/prod build;
- canonical test vectors хранятся в repo и проверяются в CI.

## 4. Что требуется для encrypted packets

Encrypted packets должны строиться на reviewed crypto library и reviewed protocol design. В roadmap запрещено реализовывать custom crypto primitives вместо проверенных библиотек и стандартных конструкций.

Требования:

- выбор reviewed crypto library и документированный rationale;
- key agreement для relationship/session keys с явным transcript binding;
- KDF с domain separation для message keys, receipt/control keys и future group/realm keys;
- AEAD для payload confidentiality/integrity;
- authenticated envelope metadata: sender/recipient relationship binding, packet type, profile policy version и replay fields;
- nonce/replay model: уникальность nonce, persisted counters или случайный nonce с проверенной collision/replay strategy, seen-cache и expiry;
- key rotation/revocation: explicit rekey flow, peer unlink/rejoin boundary, compromised-key handling;
- tamper/wrong-recipient/replay/downgrade tests.

Критерии приёмки:

- `PrototypeNoSecurityPacketCrypto` недоступен в release/prod build;
- plaintext message payload не сохраняется в encrypted packet envelope;
- wrong recipient не может расшифровать packet;
- replayed packet не создаёт новое сообщение или receipt;
- crypto design проходит review до claim о промышленной защите сообщений.

## 5. Transport hardening

Transport roadmap остаётся mesh/serverless: direct LAN/BLE/Wi-Fi Direct и store-carry-forward research mode без server-scale backend.

Требования по transport:

- LAN: documented local-network permission rationale, NSD/TCP lifecycle, multicast lock policy, local-only discovery boundary;
- BLE: bounded advertising/scanning policy, GATT lifecycle, MTU/chunking/backpressure, reconnect/error taxonomy;
- Wi-Fi Direct: отдельный permissions/device-compatibility audit перед включением в основной UX;
- rate limits на inbound frames, QR imports, malformed packets, retries и relay/prototype forwarding;
- malformed packet isolation: parse errors, wrong recipient, unknown peer, expired, duplicate и crypto failure учитываются отдельно и не создают contact/chat;
- queue durability: persisted outbox, ack/receipt state, expiry, retry backoff, restart recovery и bounded storage;
- battery/background policy: foreground service boundary, low-power behavior, Android background restrictions, user-visible state и opt-in relay mode;
- route-aware UX: UI различает no route, Bluetooth direct, LAN/Wi-Fi direct и routed prototype только по подтверждённым diagnostics.

Критерии приёмки:

- automated route-specific smoke покрывает LAN/BLE direct paths отдельно;
- queue survives app/process restart within documented storage limits;
- malformed packet corpus не приводит к crash/contact creation/message creation;
- transport metrics не формулируются как широкая reliability гарантия без статистики.

## 6. Security review gates

Перед любым промышленным claim нужны review gates, а не только зелёные unit tests.

Минимальный набор gates:

- threat model: assets, actors, trust boundaries, metadata leakage, local storage, lost device, malicious peer, replay/downgrade, relay abuse;
- crypto design review: library selection, protocol transcript, signatures, key agreement, KDF, AEAD, nonce/replay, rotation/revocation;
- Android security review: Keystore usage, backup flags, exported components, permissions, logging, clipboard/share surfaces, screenshots/notifications;
- transport abuse review: rate limits, malformed isolation, battery abuse, local network exposure, BLE/Wi-Fi Direct pairing assumptions;
- privacy review: no phone/email/account identifiers, no public discovery, no centralized server identity, no global directory;
- implementation review: code ownership, tests, fuzz/property tests where applicable, reproducible release build, dependency audit;
- external review or documented independent review before any public wording about industrial-grade protection.

Completion критерий: review findings are either fixed or explicitly accepted with documented residual risk. Unreviewed prototype behavior remains prototype-only.

## 7. Release/prototype build separation

Release/prototype separation должна быть техническим gate, а не только документацией.

Требования:

- отдельные build flavors или equivalent build-time policy для `research/debug` и `release/prod`;
- compile/runtime guard, запрещающий `PrototypeNoSecurityPacketCrypto` в release/prod;
- release/prod build требует Keystore identity provider, signed QR provider и encrypted packet provider;
- Research Panel, diagnostic exports, fake validators, manual smoke shortcuts и prototype relay mode выключены или явно скрыты в release/prod;
- release/prod wording не содержит dissertation/evidence claims как замену security review;
- CI matrix разделяет research tests, production policy tests и release build checks;
- artifact naming ясно отделяет research APK от release candidate.

Минимальный release gate:

1. No placeholder identity: release/prod не использует placeholder identity provider.
2. No unsigned QR activation: активация relationship невозможна без подписанного QR handshake.
3. No plaintext/prototype packet crypto: packet payload не остаётся в prototype/plaintext envelope.
4. No custom crypto primitives: не добавляются самодельные криптографические примитивы.
5. No server-scale dependency introduced for core direct messaging: базовые direct messages остаются mesh/serverless.
6. Security review gates documented and satisfied: review gates задокументированы и закрыты.

До выполнения этих условий Kraken остаётся research prototype, а production roadmap должен использоваться только как список работ и acceptance criteria.
