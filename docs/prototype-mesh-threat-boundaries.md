# Prototype Mesh Threat Boundaries

Этот документ фиксирует границы будущего P2P/mesh слоя Kraken Android research prototype.
Он нужен, чтобы транспортная реализация не превратилась в неявное обещание production security.

## Статус

- Kraken Android сейчас является research prototype.
- QR/offline handshake является единственным источником `ACTIVE` relationship.
- LAN discovery, loopback transport и будущие mesh diagnostics не создают trust.
- Message payload до crypto phase может быть plaintext/prototype JSON.
- `proofMode = "prototype-placeholder"` не является цифровой подписью.
- Production encryption не реализован.
- Cloud/server relay не реализован и не является частью этого плана.
- Public discovery, account/login, phone/email identity не реализуются.

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
- считать `prototype-placeholder` proof криптографической проверкой.

## Payload And Proof Boundary

До Phase 8 packet envelope использует prototype fields:

- `payloadJson` может содержать plaintext/demo payload;
- `proofMode` фиксирует только режим прототипа;
- packet signature отсутствует;
- packet encryption отсутствует;
- replay protection ограничен local `packetId`/seen cache и expiry.

Эти ограничения должны быть видны в Research/Diagnostics UI и документации.

## Relay Boundary

Real relay выключен до отдельного explicit prototype relay mode.

Simulated store-and-forward в Phase 7 допускается только для controlled research/demo:

- relay mode должен включаться явно;
- relay должен проверять expiry, TTL, duplicate и размер packet;
- relay не должен заявлять production privacy;
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

> Прототип P2P/mesh доставки. Доверие создаётся только через QR-рукопожатие. Production encryption пока не реализован.

Не использовать:

- "secure messenger";
- "production E2EE";
- "подписанный handshake", пока подписи placeholder;
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
