# Kraken Crypto Envelope Plan

Status: design/interface phase. Production cryptography is not implemented.

## Scope

The mesh packet envelope currently carries prototype fields:

- `proofMode = "prototype-placeholder"`;
- plaintext/prototype `payloadJson`;
- local `packetId` duplicate suppression;
- expiry and TTL checks.

This is enough for research transport testing, but it is not a secure message envelope.

## Target Envelope

Future production-grade packet processing needs:

1. canonical packet serialization;
2. sender signature over envelope metadata and ciphertext;
3. recipient encryption for message payload;
4. replay protection using packet id, expiry and signature domain;
5. key rotation and device replacement semantics;
6. tombstone/revocation handling;
7. explicit build separation between prototype and reviewed crypto.

## Interfaces

Android code now defines interfaces:

- `PacketSigner`;
- `PacketVerifier`;
- `PacketEncryptor`;
- `PacketDecryptor`.

Current implementation:

- `PrototypeNoSecurityPacketCrypto`;
- diagnostic only;
- not a signature;
- not encryption;
- blocked for release/prod build types by tests.

## Non-Goals In This Phase

- no custom crypto primitives;
- no production E2EE claim;
- no audited secure messenger claim;
- no server key registry;
- no account/login identity.

## Future Decision Points

- reviewed library choice: libsodium/Tink/Jetpack Security/other;
- identity key type;
- signing key vs agreement key separation;
- QR payload canonicalization;
- Android Keystore migration;
- recovery and lost-device behavior.
