# Kraken Key Generation Policy

This document fixes the current policy for identity-key generation, uniqueness and privacy.

It complements:

- `kraken-current-concept.md`;
- `kraken-identity-and-unlink-policy.md`;
- `mobile-p2p-messenger-plan-addendum.md`.

## 1. Core Decision

Kraken must not use device identifiers as cryptographic key material.

Forbidden as key material:

```text
IMEI
Android ID
phone number
SIM identifiers
MAC address
serial number
hardware fingerprint
user display name
realm name
```

Reason:

- these values deanonymize users;
- they are not high-quality secrets;
- they can be stable cross-context identifiers;
- they conflict with accountless/pseudonymous design.

Key uniqueness must come from cryptographically strong randomness, not from device identity.

## 2. Identity Key Generation

Default rule:

```text
Generate identity keys from platform CSPRNG / Android Keystore using a reviewed crypto library.
```

Do not implement a custom random number generator.

Do not derive identity keys from deterministic user/device values.

Recommended Android approach:

- use Android Keystore where possible;
- prefer hardware-backed keys when available;
- prefer non-exportable private identity keys when compatible with the product model;
- use system CSPRNG for any random seed/nonce material;
- fail closed if secure randomness is unavailable.

## 3. New Key Means New User

Kraken identity is the identity key.

Rule:

```text
new identity key = new user
```

A new identity key does not automatically inherit:

- old realm memberships;
- old contacts;
- old invite edges;
- old courier score;
- old relay reliability;
- old trust state;
- old moderation state.

This matches the current identity/unlink policy.

## 4. Optional Touch Entropy Ceremony

Kraken may include an optional UX ceremony where the user moves a finger on screen to add additional entropy.

Important rule:

```text
Touch entropy is additive only. Security must not depend on it.
```

Safe model:

```text
final_seed_material = OS_CSPRNG_bytes || optional_touch_jitter || optional_timing_jitter || app_install_nonce
final_seed = HKDF(final_seed_material, context="kraken.identity.seed.v1")
```

If the user skips the ceremony, identity generation must still be secure because OS CSPRNG / Keystore remains the primary entropy source.

Touch entropy must not replace system randomness.

## 5. App Install Nonce

Kraken may create a local app install nonce on first launch:

```text
app_install_nonce = CSPRNG(32 bytes or more)
```

Rules:

- it is local only;
- it is not a device identifier;
- it must not be sent to other peers;
- it may be mixed into local derivation contexts;
- it must not be used as a public tracking identifier.

## 6. Domain Separation

Different keys and contexts must use domain-separated derivation labels.

Example labels:

```text
kraken.identity.v1
kraken.session.v1
kraken.invite.v1
kraken.relationship.v1
kraken.packet.v1
kraken.receipt.v1
kraken.tombstone.v1
kraken.realm.membership.v1
```

Never reuse raw key material across unrelated protocol purposes.

## 7. Key Hierarchy

Do not use the long-term identity key for everything.

Recommended separation:

```text
identity key       long-term pseudonymous identity
handshake/prekey   session establishment
session key        peer/session encryption context
message key        per-message encryption
receipt key/context control-message protection
tombstone key/context signed deletion signal context
```

The exact primitive choices are future implementation decisions, but separation must be preserved.

## 8. Duplicate Public Key Handling

Kraken should treat the public identity key as the cryptographic identity.

Rules:

- one realm must not accept duplicate active memberships for the same public identity key;
- if a scanned/imported contact has an already-known public key, show that it is already known;
- do not create a second independent contact for the same public key without explicit user confirmation and context;
- if the same public key appears from another device, treat it as the same identity key, not a new person.

This does not prove real-human uniqueness. It only prevents duplicate key identities.

## 9. Membership Identity

A realm may issue a realm-specific membership id.

Purpose:

- realm member counting;
- membership certificate reference;
- scoped governance state;
- limiting duplicate memberships.

A membership id counts a membership/identity in a realm, not a unique real human.

## 10. Randomness Failure Handling

The app should include minimal sanity checks for catastrophic failures.

Examples:

- reject all-zero random output;
- reject obviously repeated local generation output inside one runtime session;
- fail if secure randomness provider is unavailable;
- log only non-secret error state, never seed material;
- never fall back to deterministic device identifiers.

These checks do not prove randomness quality; they only catch severe implementation failure.

## 11. Private Key Storage

Recommended Android policy:

```text
Store private identity keys in Android Keystore where possible.
Prefer non-exportable/hardware-backed storage when available.
```

If non-exportable keys make device migration impossible, that is acceptable for Kraken's current model:

```text
new device/key = new user identity
```

Any future migration/recovery feature must be explicitly designed and must not silently transfer trust.

## 12. Key Compromise

If a user believes their identity key is compromised:

1. mark current identity as compromised;
2. leave old realms where possible;
3. send leave/key-compromise notices where possible;
4. delete old local identity;
5. create a new identity key;
6. rejoin realms through new invites;
7. rebuild contacts through new QR/invite exchanges.

If the old key is unavailable, some notices may be impossible to send.

## 13. Identity Recreation Limits

Local app-level limits can reduce casual abuse:

```text
identity recreation cooldown
max identity resets per time window
extra warnings after repeated resets
local risk warning after frequent recreation
```

However, these are not hard security boundaries. A user may clear app data, reinstall or use a modified client.

Real protection comes from:

- invite-only membership;
- one-time QR plus handshake;
- inviter-invitee accountability;
- new-member cooldown;
- no automatic transfer of membership/trust.

## 14. Anti-Patterns

Do not implement:

```text
key = hash(IMEI)
key = hash(Android ID)
key = hash(display name + timestamp)
key = hash(phone number)
key = user-drawn entropy only
custom PRNG seeded from UI events only
automatic trust transfer to a new key
silent key rotation inside the same identity
```

## 15. Current Policy Summary

```text
1. Device identifiers are forbidden as key material.
2. Identity keys are generated using platform CSPRNG / Android Keystore.
3. Optional touch entropy can be mixed in, but security must not depend on it.
4. Use HKDF/domain separation for derivation contexts.
5. Prefer non-exportable private identity keys when supported.
6. Same public key must not create duplicate active membership in one realm.
7. New key means new user.
8. Key compromise means leaving/rejoining, not transparent key rotation.
9. Never implement custom RNG.
```

## 16. Codex Task Seed

### Task: Implement Identity Key Generation Policy Models

Acceptance criteria:

- docs and code do not use IMEI, Android ID, phone number, MAC or serial identifiers for key generation;
- identity generation uses platform secure randomness / Android Keystore abstraction;
- optional touch entropy model is additive only;
- domain separation labels exist for identity/session/invite/packet/receipt/tombstone contexts;
- same public key duplicate detection is modeled;
- new key is treated as new user;
- key compromise flow does not preserve memberships automatically;
- tests cover duplicate public key detection and no device-id dependency.
