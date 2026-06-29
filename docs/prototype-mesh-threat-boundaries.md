# Mesh Threat Boundaries

Этот документ фиксирует границы P2P/mesh слоя Kraken Android.
Он нужен, чтобы транспортная реализация не смешивалась с доверием, криптографическим
допуском и пользовательской идентичностью.

## Статус

- Kraken Android сейчас является исследовательской Android-сборкой.
- QR/offline handshake является единственным источником `ACTIVE` relationship.
- LAN discovery, loopback transport и mesh diagnostics не создают trust.
- Message payload использует защищённый path; debug compatibility path отделён.
- `proofMode = "prototype-placeholder"` является служебным маркером старого packet path.
- Подписи QR/packet, Android Keystore и replay-hardening вынесены в отдельные слои плана.
- Cloud/server relay не входит в этот локальный транспортный слой.
- Account/login/phone/email identity находятся за пределами локальной модели идентичности Kraken.

## Trust Boundary

Транспорт может обнаружить технически доступный peer, но это не делает peer контактом.

Разрешено:

- показывать discovered peer в diagnostics;
- отправлять message packet только ACTIVE контакту;
- принимать inbound `MESSAGE` только от ACTIVE peer;
- reject unknown, pending, blocked, wrong-recipient packets;
- считать duplicate/expired/malformed packets отдельными rejection metrics.

Запрещено:

- создавать contact из LAN discovery;
- создавать chat из unknown inbound packet;
- активировать relationship из invite QR import;
- принимать pending relationship как trusted transport peer;
- считать `prototype-placeholder` proof цифровой подписью.

## Payload And Proof Boundary

Текущий packet envelope сохраняет transitional fields для совместимости:

- `payloadJson` используется старым debug compatibility path;
- `proofMode` фиксирует служебный режим packet path;
- подписи packet envelope вынесены в отдельный слой;
- защищённый message payload path использует `ENCRYPTED_MESSAGE_JSON`;
- replay protection сейчас опирается на local `packetId`/seen cache и expiry.

Эти ограничения должны быть видны в Research/Diagnostics UI и документации.

## Relay Boundary

Real relay выключен до отдельного явно включаемого relay mode.

Simulated store-and-forward в Phase 7 допускается только для controlled research/demo:

- relay mode должен включаться явно;
- relay должен проверять expiry, TTL, duplicate и размер packet;
- relay не должен заявлять абсолютную приватность;
- relay не должен создавать contacts или trust relationships;
- relay не должен использовать server/cloud endpoint.

## LAN Boundary

Direct LAN NSD + TCP является первым реальным P2P transport milestone.

Ограничения:

- LAN discovery не является публичным каталогом;
- LAN discovery не является trust;
- `INTERNET` permission используется только как Android permission для local LAN sockets, не cloud/server backend;
- `CHANGE_WIFI_MULTICAST_STATE` используется только для удержания multicast lock во время локального NSD discovery;
- контакты и сообщения остаются gated через QR-established ACTIVE relationships.

## Safe User-Facing Wording

Использовать:

> Локальная P2P/mesh доставка Kraken. Доверие создаётся через QR-рукопожатие и relationship binding; защищённый payload path и диагностика фиксируются в evidence.

Не использовать:

- "абсолютно защищённый мессенджер";
- "полный промышленный E2EE";
- "handshake с цифровой подписью", пока подпись вынесена в отдельный слой;
- "публичное обнаружение";
- "серверная синхронизация";
- "LAN peer = trusted contact".

## Phase Gate

Перед real LAN transport должны существовать:

1. Local message model.
2. Packet envelope.
3. Duplicate/expired/TTL rejection.
4. Trust-gated loopback delivery.
5. Diagnostics that separate discovered peers from trusted contacts.

Detailed trust gate audit: `docs/mesh-trust-gating-audit.md`.
Transport hardening notes: `docs/mesh-transport-hardening.md`.
Multi-transport roadmap: `docs/multi-transport-mesh-roadmap.md`.
Crypto envelope plan: `protocol-spec/crypto-envelope.md`.
Android Keystore migration plan: `docs/android-keystore-migration-plan.md`.
