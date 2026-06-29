# План Crypto Envelope Kraken

Статус: рабочий дизайн crypto envelope и точек интеграции Android.

## Область

Mesh packet envelope сейчас содержит совместимые поля старого packet path:

- `proofMode = "prototype-placeholder"`;
- `payloadJson` для debug compatibility path;
- local `packetId` duplicate suppression;
- expiry and TTL checks.

Этого достаточно только для транспортных проверок и совместимости старых пакетов.
Защищённый message payload path идёт через `ENCRYPTED_MESSAGE_JSON`.

## Целевой Envelope

Дальнейший packet processing требует:

1. каноническую сериализацию packet;
2. подпись отправителя поверх envelope metadata и ciphertext;
3. recipient encryption для message payload;
4. replay protection через packet id, expiry и signature domain;
5. key rotation и правила замены устройства;
6. tombstone/revocation handling;
7. явное разделение debug compatibility path и reviewed crypto path.

## Interfaces

Android code задаёт интерфейсы:

- `PacketSigner`;
- `PacketVerifier`;
- `PacketEncryptor`;
- `PacketDecryptor`.

Текущая совместимая реализация:

- `DebugPlaintextPacketCrypto`;
- debug plaintext compatibility path;
- blocked for release/prod build types by tests.

## Вне Этого Слоя

- custom crypto primitives;
- абсолютные security-claims;
- server key registry;
- account/login identity.

## Следующие Решения

- reviewed library choice: libsodium/Tink/Jetpack Security/other;
- identity key type;
- signing key vs agreement key separation;
- QR payload canonicalization;
- Android Keystore migration;
- recovery and lost-device behavior.
